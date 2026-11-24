package matchbook.persistence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Generic UTF-8 CSV repository with header validation and atomic snapshot replacement. */
public class CsvRepository<T> {
    private final Path filePath;
    private final CsvMapper<T> mapper;

    public CsvRepository(Path filePath, CsvMapper<T> mapper) {
        this.filePath = Objects.requireNonNull(filePath).toAbsolutePath().normalize();
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Path getFilePath() { return filePath; }

    public void saveAll(Collection<T> records) throws IOException {
        List<T> snapshot = copy(records);
        Path parent = filePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "trades-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(mapper.header());
                writer.newLine();
                writeRows(writer, snapshot);
            }
            try {
                Files.move(temp, filePath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public void appendAll(Collection<T> records) throws IOException {
        List<T> snapshot = copy(records);
        Path parent = filePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(filePath) || Files.size(filePath) == 0) {
            saveAll(snapshot);
            return;
        }
        validateHeader();
        if (snapshot.isEmpty()) return;
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
                StandardOpenOption.APPEND)) {
            writeRows(writer, snapshot);
        }
    }

    public List<T> loadAll() throws IOException {
        if (!Files.exists(filePath)) return List.of();
        List<T> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!mapper.header().equals(header)) {
                throw new IOException("Unexpected CSV header. Expected: " + mapper.header());
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                try {
                    result.add(Objects.requireNonNull(mapper.fromCsvRow(line)));
                } catch (RuntimeException ex) {
                    throw new IOException("Invalid trade CSV row at line " + lineNumber + ": "
                            + ex.getMessage(), ex);
                }
            }
        }
        return List.copyOf(result);
    }

    private void validateHeader() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            if (!mapper.header().equals(reader.readLine())) {
                throw new IOException("Unexpected CSV header. Expected: " + mapper.header());
            }
        }
    }

    private List<T> copy(Collection<T> records) {
        Objects.requireNonNull(records, "records cannot be null");
        List<T> copy = new ArrayList<>(records.size());
        for (T item : records) copy.add(Objects.requireNonNull(item, "record cannot be null"));
        return copy;
    }

    private void writeRows(BufferedWriter writer, Collection<T> records) throws IOException {
        for (T item : records) {
            String row = Objects.requireNonNull(mapper.toCsvRow(item));
            if (row.contains("\n") || row.contains("\r")) {
                throw new IOException("CSV row contains a line break");
            }
            writer.write(row);
            writer.newLine();
        }
    }
}
