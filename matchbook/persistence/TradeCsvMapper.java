package matchbook.persistence;

import matchbook.model.Trade;

/**
 * Maps the integrated project's seven-field Trade model to CSV.
 * Prices remain integer paise; symbol and timestamp are retained.
 */
public final class TradeCsvMapper implements CsvMapper<Trade> {
    public static final String CSV_HEADER =
            "tradeId,buyOrderId,sellOrderId,symbol,pricePaise,quantity,timestamp";

    @Override
    public String header() {
        return CSV_HEADER;
    }

    @Override
    public String toCsvRow(Trade trade) {
        if (trade == null) {
            throw new IllegalArgumentException("Trade cannot be null");
        }
        return csv(trade.getTradeId()) + ","
                + csv(trade.getBuyOrderId()) + ","
                + csv(trade.getSellOrderId()) + ","
                + csv(trade.getSymbol()) + ","
                + trade.getPricePaise() + ","
                + trade.getQuantity() + ","
                + trade.getTimestamp();
    }

    @Override
    public Trade fromCsvRow(String row) {
        if (row == null || row.isBlank()) {
            throw new IllegalArgumentException("CSV row cannot be empty");
        }
        String[] v = parse(row);
        if (v.length != 7) {
            throw new IllegalArgumentException("Expected 7 columns but found " + v.length);
        }
        try {
            long pricePaise = Long.parseLong(v[4].trim());
            int quantity = Integer.parseInt(v[5].trim());
            long timestamp = Long.parseLong(v[6].trim());
            if (v[0].isBlank() || v[1].isBlank() || v[2].isBlank() || v[3].isBlank()
                    || pricePaise <= 0 || quantity <= 0 || timestamp < 0) {
                throw new IllegalArgumentException("Trade CSV values are outside valid ranges");
            }
            return new Trade(v[0], v[1], v[2], v[3], pricePaise, quantity, timestamp);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric value in trade CSV row", ex);
        }
    }

    private static String csv(String value) {
        if (value == null) throw new IllegalArgumentException("CSV value cannot be null");
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String[] parse(String row) {
        java.util.List<String> values = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"'); i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                if (current.length() != 0) throw new IllegalArgumentException("Unexpected quote in CSV row");
                quoted = true;
            } else if (c == ',') {
                values.add(current.toString()); current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quoted CSV field");
        values.add(current.toString());
        return values.toArray(String[]::new);
    }
}
