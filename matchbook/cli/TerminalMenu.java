package matchbook.cli;

import matchbook.exceptions.AuthenticationException;
import matchbook.model.*;
import matchbook.Main;
import matchbook.service.AuthenticationService;
import matchbook.service.ExchangeService;
import matchbook.analytics.TradeAnalytics;
import matchbook.analytics.TradeAnalyticsReport;
import matchbook.storage.CsvService;
import matchbook.concurrency.ConcurrentTradeProcessor;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TerminalMenu {
    private final Scanner scanner;
    private final AuthenticationService authentication;
    private final ExchangeService exchange;
    private final CsvService csvService;
    private User currentUser;
    private final List<Main.AutomatedTrader> autoTraders;
    private final List<Thread> traderThreads;

    public TerminalMenu(Scanner scanner, AuthenticationService authentication,
                        ExchangeService exchange) {
        this.scanner = scanner;
        this.authentication = authentication;
        this.exchange = exchange;
        this.csvService = new CsvService();
        this.autoTraders = new ArrayList<>();
        this.traderThreads = new ArrayList<>();
    }

    public void start() {
        boolean running = true;

        while (running) {
            System.out.println();
            System.out.println("======================================");
            System.out.println("              MATCHBOOK");
            System.out.println("       LIMIT ORDER BOOK SIMULATOR");
            System.out.println("======================================");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Reset Password");
            System.out.println("4. Exit");
            System.out.print("Choose: ");

            int choice = readInt();

            if (choice == 1) {
                login();
            } else if (choice == 2) {
                register();
            } else if (choice == 3) {
                resetPassword();
            } else if (choice == 4) {
                running = false;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private void register() {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Email: ");
            String email = scanner.nextLine().trim();
            System.out.print("Password (minimum 8 characters): ");
            String password = scanner.nextLine();
            System.out.print("Recovery question answer: ");
            String answer = scanner.nextLine();

            authentication.register(username, email, password, answer);
            System.out.println("Registration successful. You can now log in.");
        } catch (Exception exception) {
            System.out.println("Registration failed: " + exception.getMessage());
        }
    }

    private void login() {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            currentUser = authentication.login(username, password);
            System.out.println("Welcome, " + currentUser.getUsername() + ".");
            userMenu();
        } catch (Exception exception) {
            System.out.println("Login failed: " + exception.getMessage());
        }
    }

    private void resetPassword() {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Registered email: ");
            String email = scanner.nextLine().trim();
            System.out.print("Recovery answer: ");
            String answer = scanner.nextLine();
            System.out.print("New password: ");
            String newPassword = scanner.nextLine();

            authentication.resetPassword(username, email, answer, newPassword);
            System.out.println("Password updated. You can now log in.");
        } catch (Exception exception) {
            System.out.println("Password reset failed: " + exception.getMessage());
        }
    }

    private void userMenu() {
        boolean loggedIn = true;

        while (loggedIn) {
            System.out.println();
            System.out.println("======================================");
            System.out.println("MATCHBOOK | User: " + currentUser.getUsername());
            System.out.println("Instrument: RELIANCE");
            System.out.println("======================================");
            System.out.println("1. Place limit order");
            System.out.println("2. Cancel my open order");
            System.out.println("3. View order book");
            System.out.println("4. View my orders");
            System.out.println("5. View my trade history");
            System.out.println("6. Start automated traders");
            System.out.println("7. Stop automated traders");
            System.out.println("8. Import orders from CSV");
            System.out.println("9. Export all trades to CSV");
            System.out.println("10. View trade analytics");
            System.out.println("11. Concurrent CSV persistence + analytics");
            System.out.println("12. Logout");
            System.out.print("Choose: ");

            int choice = readInt();

            if (choice == 1) {
                placeOrder();
            } else if (choice == 2) {
                cancelOrder();
            } else if (choice == 3) {
                showOrderBook();
            } else if (choice == 4) {
                showMyOrders();
            } else if (choice == 5) {
                showMyTrades();
            } else if (choice == 6) {
                startAutomatedTraders();
            } else if (choice == 7) {
                stopAutomatedTraders();
            } else if (choice == 8) {
                importOrders();
            } else if (choice == 9) {
                exportTrades();
            } else if (choice == 10) {
                showTradeAnalytics();
            } else if (choice == 11) {
                runConcurrentTradeProcessing();
            } else if (choice == 12) {
                stopAutomatedTraders();
                currentUser = null;
                loggedIn = false;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private void placeOrder() {
        try {
            System.out.print("Side (BUY/SELL): ");
            OrderSide side = OrderSide.valueOf(scanner.nextLine().trim().toUpperCase());

            System.out.print("Limit price (for example 1250.50): ");
            long price = Math.round(Double.parseDouble(scanner.nextLine().trim()) * 100);

            System.out.print("Quantity: ");
            int quantity = Integer.parseInt(scanner.nextLine().trim());

            String orderId = exchange.placeOrder(currentUser.getUserId(),
                    currentUser.getUsername(), side, price, quantity);

            System.out.println("Order accepted. Order ID: " + orderId);
        } catch (Exception exception) {
            System.out.println("Order rejected: " + exception.getMessage());
        }
    }

    private void cancelOrder() {
        try {
            System.out.print("Order ID: ");
            String orderId = scanner.nextLine().trim();
            exchange.cancelOrder(orderId, currentUser.getUserId());
            System.out.println("Order cancelled.");
        } catch (Exception exception) {
            System.out.println("Cancellation failed: " + exception.getMessage());
        }
    }

    private void showOrderBook() {
        List<Order> orders = exchange.getBookOrders();

        System.out.println();
        System.out.println("SELL ORDERS (lowest price first)");
        printSide(orders, OrderSide.SELL);

        System.out.println("-----------------------------------------------");
        System.out.println("BUY ORDERS (highest price first)");
        printSide(orders, OrderSide.BUY);

    }

    private void printSide(List<Order> orders, OrderSide side) {
        for (Order order : orders) {
            if (order.getSide() == side) {
                System.out.printf("%-12s %-8s %-12s %-10d %-20s%n",
                        order.getOrderId(), side,
                        ExchangeService.formatPrice(order.getPricePaise()),
                        order.getRemainingQuantity(), order.getUsername());
            }
        }
    }

    private void showMyOrders() {
        try {
            List<Order> orders = exchange.getMyOrders(currentUser.getUserId());
            System.out.println("ORDER ID | SIDE | PRICE | ORIGINAL | REMAINING | STATUS");
            for (Order order : orders) {
                System.out.println(order.getOrderId() + " | " + order.getSide()
                        + " | " + ExchangeService.formatPrice(order.getPricePaise())
                        + " | " + order.getOriginalQuantity()
                        + " | " + order.getRemainingQuantity()
                        + " | " + order.getStatus());
            }
        } catch (SQLException exception) {
            System.out.println("Could not load orders: " + exception.getMessage());
        }
    }

    private void showMyTrades() {
        try {
            List<Trade> trades = exchange.getMyTrades(currentUser.getUserId());
            System.out.println("TRADE ID | BUY ORDER | SELL ORDER | PRICE | QUANTITY");
            for (Trade trade : trades) {
                System.out.println(trade.getTradeId() + " | "
                        + trade.getBuyOrderId() + " | "
                        + trade.getSellOrderId() + " | "
                        + ExchangeService.formatPrice(trade.getPricePaise())
                        + " | " + trade.getQuantity());
            }
        } catch (SQLException exception) {
            System.out.println("Could not load trades: " + exception.getMessage());
        }
    }

    private void startAutomatedTraders() {
        System.out.print("Number of automated traders (1-10): ");
        int count = readInt();

        if (count < 1 || count > 10) {
            System.out.println("Choose between 1 and 10.");
            return;
        }

        for (int index = 1; index <= count; index++) {
            Main.AutomatedTrader trader = new Main.AutomatedTrader(exchange,
                    currentUser.getUserId(),
                    currentUser.getUsername() + "-BOT-" + index, 20);
            Thread thread = new Thread(trader, "TRADER-" + String.format("%02d", index));
            autoTraders.add(trader);
            traderThreads.add(thread);
            thread.start();
        }

        System.out.println();
        System.out.println("========== AUTOMATED TRADERS STARTED ==========");
        System.out.println("Active trader threads: " + count);
        System.out.println("Each trader will submit up to 20 orders.");
        System.out.println("===============================================");
    }

    private void stopAutomatedTraders() {
        for (Main.AutomatedTrader trader : autoTraders) {
            trader.stop();
        }

        for (Thread thread : traderThreads) {
            try {
                thread.join(2000);
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        autoTraders.clear();
        traderThreads.clear();
        System.out.println("========== AUTOMATED TRADERS STOPPED ==========");
    }

    private void importOrders() {
        try {
            System.out.print("CSV path (default data/import_orders.csv): ");
            String value = scanner.nextLine().trim();
            Path path;
            if (value.isEmpty()) {
                path = Path.of("data/import_orders.csv");
            } else {
                path = Path.of(value);
            }

            int count = csvService.importOrders(path, (side, price, quantity) ->
                    exchange.placeOrder(currentUser.getUserId(),
                            currentUser.getUsername(), side, price, quantity));

            System.out.println("Imported orders: " + count);
        } catch (Exception exception) {
            System.out.println("Import failed: " + exception.getMessage());
        }
    }

    private void exportTrades() {
        try {
            List<Trade> trades = exchange.getAllTrades();
            csvService.exportTrades(trades, Path.of("data/trades_export.csv"));
            System.out.println("Trades exported to data/trades_export.csv");
        } catch (Exception exception) {
            System.out.println("Export failed: " + exception.getMessage());
        }
    }

    private void showTradeAnalytics() {
        try {
            List<Trade> trades = exchange.getAllTrades();

            if (trades.isEmpty()) {
                System.out.println("No trades available for analytics.");
                return;
            }

            TradeAnalyticsReport report = TradeAnalytics.generateReport(trades);
            System.out.println();
            System.out.println(report);

            // Additional query options
            System.out.println();
            System.out.println("Additional queries available:");
            System.out.println("  a. Show recent trades (last 10)");
            System.out.println("  b. Show trades above a price");
            System.out.print("Select query (or press Enter to continue): ");

            String queryChoice = scanner.nextLine().trim().toLowerCase();

            if (queryChoice.equals("a")) {
                List<Trade> recent = TradeAnalytics.getRecentTrades(trades, 10);
                System.out.println("\n--- Recent Trades (newest first) ---");
                for (Trade trade : recent) {
                    System.out.printf("%s | %s | %s | ₹%.2f | %d%n",
                            trade.getTradeId(),
                            trade.getBuyOrderId(),
                            trade.getSellOrderId(),
                            trade.getPricePaise() / 100.0,
                            trade.getQuantity());
                }
            } else if (queryChoice.equals("b")) {
                System.out.print("Minimum price (e.g., 1250.00): ");
                String priceInput = scanner.nextLine().trim();
                try {
                    long minPricePaise = Math.round(Double.parseDouble(priceInput) * 100);
                    List<Trade> filtered = TradeAnalytics.findTradesAtOrAbovePrice(trades, minPricePaise);
                    System.out.println("\n--- Trades at or above ₹" + priceInput + " ---");
                    for (Trade trade : filtered) {
                        System.out.printf("%s | %s | %s | ₹%.2f | %d%n",
                                trade.getTradeId(),
                                trade.getBuyOrderId(),
                                trade.getSellOrderId(),
                                trade.getPricePaise() / 100.0,
                                trade.getQuantity());
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid price format.");
                }
            }

        } catch (SQLException exception) {
            System.out.println("Could not load trades: " + exception.getMessage());
        } catch (Exception exception) {
            System.out.println("Analytics failed: " + exception.getMessage());
        }
    }

    /**
     * Demonstrates post-trade concurrency without allowing worker threads to
     * access or mutate the live matching engine.
     */
    private void runConcurrentTradeProcessing() {
        try {
            List<Trade> trades = exchange.getAllTrades();
            if (trades.isEmpty()) {
                System.out.println("No completed trades available. Execute trades first.");
                return;
            }

            System.out.print("CSV path (default data/concurrent_trades.csv): ");
            String value = scanner.nextLine().trim();
            Path csvPath = value.isEmpty()
                    ? Path.of("data/concurrent_trades.csv")
                    : Path.of(value);

            ConcurrentTradeProcessor processor = new ConcurrentTradeProcessor();
            ConcurrentTradeProcessor.ProcessingResult result =
                    processor.process(List.copyOf(trades), csvPath);

            System.out.println();
            System.out.println("========== CONCURRENT PROCESSING COMPLETE ==========");
            System.out.println("Trades processed: " + result.processedTradeCount());
            System.out.println("CSV saved to: " + result.csvPath());
            System.out.println();
            System.out.println(result.analyticsReport());
            System.out.println("=====================================================");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("Concurrent processing was interrupted.");
        } catch (Exception exception) {
            System.out.println("Concurrent processing failed: " + exception.getMessage());
        }
    }

    private int readInt() {
        try {
            String value = scanner.nextLine().trim();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
