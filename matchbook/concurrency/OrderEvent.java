package matchbook.concurrency;

import matchbook.model.Order;
import java.util.concurrent.CountDownLatch;

public class OrderEvent {
    public enum EventType {
        PLACE,
        CANCEL,
        STOP
    }

    private final EventType type;
    private final Order order;
    private final String orderId;
    private final int userId;
    private final CountDownLatch completion;

    public OrderEvent(EventType type, Order order, String orderId, int userId) {
        this.type = type;
        this.order = order;
        this.orderId = orderId;
        this.userId = userId;
        this.completion = new CountDownLatch(1);
    }

    public EventType getType() { return type; }
    public Order getOrder() { return order; }
    public String getOrderId() { return orderId; }
    public int getUserId() { return userId; }
    public CountDownLatch getCompletion() { return completion; }
}
