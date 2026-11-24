package matchbook.concurrency;

import java.util.LinkedList;
import java.util.Queue;

public class OrderEventQueue {
    private final Queue<OrderEvent> events;
    private final int capacity;
    private boolean stopped;

    public OrderEventQueue(int capacity) {
        this.capacity = capacity;
        this.events = new LinkedList<>();
        this.stopped = false;
    }

    public synchronized void put(OrderEvent event) throws InterruptedException {
        while (events.size() >= capacity && !stopped) {
            wait();
        }
        if (stopped) {
            throw new InterruptedException("Event queue has stopped.");
        }
        events.add(event);
        notifyAll();
    }

    public synchronized OrderEvent take() throws InterruptedException {
        while (events.isEmpty() && !stopped) {
            wait();
        }
        if (events.isEmpty() && stopped) {
            return null;
        }
        OrderEvent event = events.remove();
        notifyAll();
        return event;
    }

    public synchronized void stop() {
        stopped = true;
        notifyAll();
    }
}
