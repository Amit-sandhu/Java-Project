package matchbook.concurrency;

import matchbook.analytics.TradeAnalytics;
import matchbook.analytics.TradeAnalyticsReport;
import matchbook.model.Trade;
import matchbook.persistence.TradeCsvRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Runs CSV persistence and analytics concurrently on an immutable snapshot.
 * The matching engine/database service is not accessed by worker threads.
 */
public final class ConcurrentTradeProcessor {
    private static final int QUEUE_CAPACITY = 32;

    public ProcessingResult process(Collection<Trade> sourceTrades, Path csvPath)
            throws IOException, InterruptedException {
        Objects.requireNonNull(sourceTrades, "sourceTrades cannot be null");
        Objects.requireNonNull(csvPath, "csvPath cannot be null");

        List<Trade> snapshot = List.copyOf(sourceTrades);
        TradeProcessingQueue queue = new TradeProcessingQueue(QUEUE_CAPACITY);
        TradeCsvRepository repository = new TradeCsvRepository(csvPath);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        Future<?> producer = null;
        Future<?> consumer = null;
        Future<TradeAnalyticsReport> analytics = null;
        try {
            producer = executor.submit((Runnable) () -> {
                System.out.println("[Trade Producer] " + Thread.currentThread().getName());
                try {
                    for (Trade trade : snapshot) queue.put(trade);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(ex);
                } finally {
                    queue.close();
                }
            });

            consumer = executor.submit((Runnable) () -> {
                System.out.println("[CSV Consumer] " + Thread.currentThread().getName());
                List<Trade> received = new ArrayList<>();
                try {
                    Trade next;
                    while ((next = queue.take()) != null) received.add(next);
                    repository.saveAll(received);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(ex);
                } catch (IOException ex) {
                    throw new CompletionException(ex);
                }
            });

            analytics = executor.submit((Callable<TradeAnalyticsReport>) () -> {
                System.out.println("[Analytics Task] " + Thread.currentThread().getName());
                return TradeAnalytics.generateReport(snapshot);
            });

            producer.get();
            consumer.get();
            TradeAnalyticsReport report = analytics.get();
            return new ProcessingResult(snapshot.size(), repository.getFilePath(), report);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof IOException io) throw io;
            if (cause instanceof InterruptedException interrupted) throw interrupted;
            throw new IOException("Concurrent post-trade processing failed", cause);
        } finally {
            queue.close();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        }
    }

    public record ProcessingResult(int processedTradeCount, Path csvPath,
                                   TradeAnalyticsReport analyticsReport) {
        public ProcessingResult {
            Objects.requireNonNull(csvPath);
            Objects.requireNonNull(analyticsReport);
        }
    }
}
