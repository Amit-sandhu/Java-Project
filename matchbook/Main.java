package matchbook;

import matchbook.cli.TerminalMenu;
import matchbook.db.DatabaseManager;
import matchbook.model.OrderSide;
import matchbook.service.AuthenticationService;
import matchbook.service.ExchangeService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        DatabaseManager databaseManager = new DatabaseManager();
        ExchangeService exchangeService = null;

        printStartupBanner();

        try (Scanner scanner = new Scanner(System.in)) {
            databaseManager.initialize();

            AuthenticationService authenticationService =
                    new AuthenticationService(databaseManager);

            exchangeService = new ExchangeService(databaseManager);

            System.out.println("Database connected.");
            System.out.println("Matching engine started.");
            System.out.println("Open orders restored: "
                    + exchangeService.getBookOrders().size());

            TerminalMenu terminalMenu = new TerminalMenu(
                    scanner, authenticationService, exchangeService);

            terminalMenu.start();

        } catch (Exception exception) {
            System.out.println("Application error: " + exception.getMessage());
        } finally {
            if (exchangeService != null) {
                exchangeService.shutdown();
            }

            try {
                databaseManager.close();
            } catch (Exception exception) {
                System.out.println("Database close error: " + exception.getMessage());
            }

            System.out.println("MATCHBOOK has shut down.");
        }
    }

    private static void printStartupBanner() {
        System.out.println("==================================================");
        System.out.println("                    MATCHBOOK");
        System.out.println("          LIMIT ORDER BOOK SIMULATOR");
        System.out.println("==================================================");
        System.out.println("Starting application...");
    }

    public static class AutomatedTrader implements Runnable {
        private static final DateTimeFormatter TIME_FORMAT =
                DateTimeFormatter.ofPattern("HH:mm:ss");

        private final ExchangeService exchange;
        private final int userId;
        private final String username;
        private final String traderId;
        private final int numberOfOrders;
        private final Random random;
        private volatile boolean running;

        public AutomatedTrader(ExchangeService exchange, int userId,
                               String username, int numberOfOrders) {
            this.exchange = exchange;
            this.userId = userId;
            this.username = username;
            this.traderId = username.substring(username.lastIndexOf("-BOT-") + 1);
            this.numberOfOrders = numberOfOrders;
            this.random = new Random();
            this.running = true;
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            int submittedOrders = 0;

            printLine("STARTED", "Automated trading session started.");

            while (running && submittedOrders < numberOfOrders) {
                try {
                    OrderSide side;
                    if (random.nextBoolean()) {
                        side = OrderSide.BUY;
                    } else {
                        side = OrderSide.SELL;
                    }

                    long pricePaise = 124500 + random.nextInt(1101);
                    int quantity = 1 + random.nextInt(20);

                    String orderId = exchange.placeOrder(userId, username,
                            side, pricePaise, quantity);

                    submittedOrders++;
                    printLine("ORDER SENT",
                            side + " " + quantity + " RELIANCE @ "
                                    + ExchangeService.formatRupees(pricePaise)
                                    + " | Order: " + orderId);

                    Thread.sleep(700 + random.nextInt(1000));

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception exception) {
                    printLine("ORDER FAILED", exception.getMessage());
                }
            }

            printLine("STOPPED", "Orders submitted: " + submittedOrders);
        }

        private void printLine(String status, String message) {
            synchronized (System.out) {
                System.out.printf("[%s] [TRADER-%s] %-11s | %s%n",
                        LocalTime.now().format(TIME_FORMAT),
                        traderId, status, message);
            }
        }
    }
}
