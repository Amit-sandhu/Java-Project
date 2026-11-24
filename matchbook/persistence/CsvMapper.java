package matchbook.persistence;

/** Converts domain objects to and from CSV rows. */
public interface CsvMapper<T> {
    String header();
    String toCsvRow(T value);
    T fromCsvRow(String row);
}
