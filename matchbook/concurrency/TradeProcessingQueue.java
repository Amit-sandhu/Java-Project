package matchbook.concurrency;

import matchbook.model.Trade;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Bounded producer-consumer queue for post-trade persistence.
 * synchronized protects shared state; wait/notifyAll coordinate producer and consumer.
 */
final class TradeProcessingQueue {
    private final ArrayDeque<Trade> trades = new ArrayDeque<>();
    private final int capacity;
    private boolean closed;

    TradeProcessingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Queue capacity must be positive");
        this.capacity = capacity;
    }

    synchronized void put(Trade trade) throws InterruptedException {
        Objects.requireNonNull(trade, "trade cannot be null");
        while (trades.size() >= capacity && !closed) wait();
        if (closed) throw new IllegalStateException("Queue is closed");
        trades.addLast(trade);
        notifyAll();
    }

    synchronized Trade take() throws InterruptedException {
        while (trades.isEmpty() && !closed) wait();
        Trade next = trades.pollFirst();
        notifyAll();
        return next;
    }

    synchronized void close() {
        closed = true;
        notifyAll();
    }
}
