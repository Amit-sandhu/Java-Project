package matchbook.service;

import matchbook.concurrency.OrderEvent;
import matchbook.concurrency.OrderEventQueue;
import matchbook.db.DatabaseManager;
import matchbook.exceptions.InvalidOrderException;
import matchbook.exceptions.OrderNotFoundException;
import matchbook.model.*;
import matchbook.orderbook.OrderBook;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExchangeService {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DatabaseManager database;
    private final OrderBook orderBook;
    private final OrderEventQueue eventQueue;
    private final Thread matchingThread;
    private final List<Trade> sessionTrades;
    private volatile boolean running;

    public ExchangeService(DatabaseManager database) throws Exception {
        this.database = database;
        this.orderBook = new OrderBook();
        this.eventQueue = new OrderEventQueue(500);
        this.sessionTrades = new ArrayList<>();
        List<Order> oldOrders = database.loadOpenOrders();
        for (Order order : oldOrders) {
            orderBook.add(order);
        }

        running = true;
        matchingThread = new Thread(this::matchingLoop, "MatchingEngine");
        matchingThread.start();
    }

    public String placeOrder(int userId, String username, OrderSide side,
                             long pricePaise, int quantity) throws Exception {
        if (pricePaise <= 0 || quantity <= 0) {
            throw new InvalidOrderException("Price and quantity must be greater than zero.");
        }

        String orderId = "O-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Order order = new Order(orderId, userId, username, side,
                pricePaise, quantity, System.currentTimeMillis());

        OrderEvent event = new OrderEvent(OrderEvent.EventType.PLACE, order, null, userId);
        submitAndWait(event);
        return orderId;
    }

    public void cancelOrder(String orderId, int userId) throws Exception {
        if (!database.orderBelongsToUser(orderId, userId)) {
            throw new OrderNotFoundException("Open order not found for your account.");
        }

        OrderEvent event = new OrderEvent(OrderEvent.EventType.CANCEL,
                null, orderId, userId);
        submitAndWait(event);
    }

    private void submitAndWait(OrderEvent event) throws Exception {
        eventQueue.put(event);
        boolean completed = event.getCompletion().await(10, TimeUnit.SECONDS);
        if (!completed) {
            throw new Exception("Matching engine did not respond in time.");
        }
    }

    private void matchingLoop() {
        while (true) {
            OrderEvent event = null;
            try {
                event = eventQueue.take();
                if (event == null || event.getType() == OrderEvent.EventType.STOP) {
                    break;
                }

                synchronized (orderBook) {
                    if (event.getType() == OrderEvent.EventType.PLACE) {
                        processOrder(event.getOrder());
                    } else if (event.getType() == OrderEvent.EventType.CANCEL) {
                        processCancellation(event.getOrderId(), event.getUserId());
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                log("ENGINE ERROR", exception.getMessage());
            } finally {
                if (event != null) {
                    event.getCompletion().countDown();
                }
            }
        }
    }

    private void processOrder(Order incoming) throws SQLException {
        database.saveOrder(incoming);

        log("ORDER ACCEPTED", incoming.getUsername() + " | "
                + incoming.getSide() + " " + incoming.getOriginalQuantity()
                + " RELIANCE @ " + formatRupees(incoming.getPricePaise())
                + " | " + incoming.getOrderId());

        while (incoming.getRemainingQuantity() > 0) {
            Order resting = orderBook.getBestOppositeOrder(incoming);
            if (resting == null) {
                break;
            }

            int tradedQuantity = Math.min(
                    incoming.getRemainingQuantity(), resting.getRemainingQuantity());

            long executionPrice = resting.getPricePaise();

            incoming.execute(tradedQuantity);
            resting.execute(tradedQuantity);

            String buyId;
            String sellId;
            String buyer;
            String seller;
            if (incoming.getSide() == OrderSide.BUY) {
                buyId = incoming.getOrderId();
                sellId = resting.getOrderId();
                buyer = incoming.getUsername();
                seller = resting.getUsername();
            } else {
                buyId = resting.getOrderId();
                sellId = incoming.getOrderId();
                buyer = resting.getUsername();
                seller = incoming.getUsername();
            }

            Trade trade = new Trade(
                    "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    buyId, sellId, "RELIANCE", executionPrice,
                    tradedQuantity, System.currentTimeMillis());

            database.saveTrade(trade);
            database.updateOrder(incoming);
            database.updateOrder(resting);
            sessionTrades.add(trade);

            orderBook.removeIfFinished(resting);

            printTrade(trade, buyer, seller, incoming, resting);
        }

        if (incoming.getRemainingQuantity() > 0) {
            orderBook.add(incoming);
            database.updateOrder(incoming);

            log("BOOKED",
                    incoming.getUsername() + " | " + incoming.getRemainingQuantity()
                            + " shares remain open | " + incoming.getOrderId());
        } else {
            log("ORDER FILLED", incoming.getUsername()
                    + " | " + incoming.getOrderId());
        }
    }

    private void printTrade(Trade trade, String buyer, String seller,
                            Order incoming, Order resting) {
        synchronized (System.out) {
            System.out.println();
            System.out.println("  +--------------------------------------------------+");
            System.out.println("  |                  TRADE EXECUTED                  |");
            System.out.println("  +--------------------------------------------------+");
            System.out.printf("  | Trade ID   : %-36s |%n", trade.getTradeId());
            System.out.printf("  | Symbol     : %-36s |%n", trade.getSymbol());
            System.out.printf("  | Buyer      : %-36s |%n", buyer);
            System.out.printf("  | Seller     : %-36s |%n", seller);
            System.out.printf("  | Price      : %-36s |%n", formatRupees(trade.getPricePaise()));
            System.out.printf("  | Quantity   : %-36s |%n", trade.getQuantity() + " shares");
            System.out.printf("  | Buy Order  : %-36s |%n", trade.getBuyOrderId());
            System.out.printf("  | Sell Order : %-36s |%n", trade.getSellOrderId());
            System.out.println("  +--------------------------------------------------+");
        }
    }

    private void processCancellation(String orderId, int userId) throws SQLException {
        Order found = null;
        for (Order order : orderBook.getAllOrders()) {
            if (order.getOrderId().equals(orderId) && order.getUserId() == userId) {
                found = order;
                break;
            }
        }

        if (found == null) {
            throw new SQLException("Order is no longer open.");
        }

        orderBook.cancel(orderId, userId);
        found.cancel();
        database.updateOrder(found);

        log("CANCELLED", found.getUsername() + " | " + orderId);
    }

    public List<Order> getBookOrders() {
        synchronized (orderBook) {
            return new ArrayList<>(orderBook.getAllOrders());
        }
    }

    public List<Order> getMyOrders(int userId) throws SQLException {
        return database.getUserOrders(userId);
    }

    public List<Trade> getMyTrades(int userId) throws SQLException {
        return database.getUserTrades(userId);
    }

    public List<Trade> getAllTrades() throws SQLException {
        return database.getTrades();
    }

    public void shutdown() {
        running = false;
        eventQueue.stop();
        try {
            matchingThread.join(5000);
            if (matchingThread.isAlive()) {
                matchingThread.interrupt();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String status, String message) {
        synchronized (System.out) {
            System.out.printf("[%s] [MATCHING ENGINE] %-13s | %s%n",
                    LocalTime.now().format(TIME_FORMAT), status, message);
        }
    }

    public static String formatPrice(long paise) {
        return String.format("%.2f", paise / 100.0);
    }

    public static String formatRupees(long paise) {
        return "₹" + String.format("%,.2f", paise / 100.0);
    }
}
