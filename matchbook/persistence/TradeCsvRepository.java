package matchbook.persistence;

import matchbook.model.Trade;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Trade-specific CSV repository using the integrated MatchBook Trade model. */
public final class TradeCsvRepository implements TradeRepository {
    private final CsvRepository<Trade> repository;

    public TradeCsvRepository(Path path) {
        repository = new CsvRepository<>(Objects.requireNonNull(path), new TradeCsvMapper());
    }

    public Path getFilePath() { return repository.getFilePath(); }
    @Override public void saveAll(Collection<Trade> trades) throws IOException { repository.saveAll(trades); }
    @Override public void appendAll(Collection<Trade> trades) throws IOException { repository.appendAll(trades); }
    @Override public List<Trade> findAll() throws IOException { return repository.loadAll(); }
}
