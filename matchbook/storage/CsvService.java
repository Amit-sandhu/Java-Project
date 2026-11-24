package matchbook.storage;

import matchbook.model.OrderSide;
import matchbook.model.Trade;
import matchbook.service.ExchangeService;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class CsvService {
    public void exportTrades(List<Trade> trades, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("tradeId,buyOrderId,sellOrderId,symbol,price,quantity,timestamp");
            writer.newLine();

            for (Trade trade : trades) {
                StringBuilder line = new StringBuilder();
                line.append(trade.getTradeId()).append(",");
                line.append(trade.getBuyOrderId()).append(",");
                line.append(trade.getSellOrderId()).append(",");
                line.append(trade.getSymbol()).append(",");
                line.append(ExchangeService.formatPrice(trade.getPricePaise())).append(",");
                line.append(trade.getQuantity()).append(",");
                line.append(trade.getTimestamp());
                writer.write(line.toString());
                writer.newLine();
            }
        }
    }

    public int importOrders(Path file, OrderImporter importer) throws IOException {
        int imported = 0;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("CSV file is empty.");
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split(",");
                if (values.length != 3) {
                    throw new IOException("Invalid CSV structure at line " + lineNumber);
                }

                try {
                    OrderSide side = OrderSide.valueOf(values[0].trim().toUpperCase());
                    long price = Math.round(Double.parseDouble(values[1].trim()) * 100);
                    int quantity = Integer.parseInt(values[2].trim());

                    importer.submit(side, price, quantity);
                    imported++;
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid data at line " + lineNumber, exception);
                } catch (Exception exception) {
                    throw new IOException("Could not import line " + lineNumber
                            + ": " + exception.getMessage(), exception);
                }
            }
        }

        return imported;
    }

    public interface OrderImporter {
        void submit(OrderSide side, long pricePaise, int quantity) throws Exception;
    }
}
