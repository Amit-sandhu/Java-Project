package matchbook.model;

public class Order {
    private final String orderId;
    private final int userId;
    private final String username;
    private final OrderSide side;
    private final long pricePaise;
    private final int originalQuantity;
    private int remainingQuantity;
    private OrderStatus status;
    private final long createdAt;

    public Order(String orderId, int userId, String username, OrderSide side,
                 long pricePaise, int quantity, long createdAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.username = username;
        this.side = side;
        this.pricePaise = pricePaise;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.OPEN;
        this.createdAt = createdAt;
    }

    public String getOrderId() { return orderId; }
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public OrderSide getSide() { return side; }
    public long getPricePaise() { return pricePaise; }
    public int getOriginalQuantity() { return originalQuantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }

    public int getExecutedQuantity() {
        return originalQuantity - remainingQuantity;
    }

    public void execute(int quantity) {
        if (quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException("Invalid execution quantity.");
        }
        remainingQuantity = remainingQuantity - quantity;
        if (remainingQuantity == 0) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        remainingQuantity = 0;
        status = OrderStatus.CANCELLED;
    }

    public void restore(int remaining, OrderStatus restoredStatus) {
        if (remaining < 0 || remaining > originalQuantity) {
            throw new IllegalArgumentException("Invalid restored quantity.");
        }
        remainingQuantity = remaining;
        status = restoredStatus;
    }
}
