package matchbook.analytics;

import matchbook.model.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Stream-based analytics for the completed trades produced by the matching engine.
 * Trade prices are stored as integer paise, where 100 paise = ₹1.00.
 */
public final class TradeAnalytics {

    private static final int REPORT_SCALE = 4;

    private TradeAnalytics() {
        // Utility class; create reports through the static method below.
    }

    /**
     * Calculates a report from the supplied trades.
     * Prices in Trade are stored as integer paise (1/100 of a rupee).
     */
    public static TradeAnalyticsReport generateReport(Collection<Trade> trades) {
        Objects.requireNonNull(trades, "trades cannot be null");
        List<Trade> snapshot = List.copyOf(trades);

        if (snapshot.isEmpty()) {
            return new TradeAnalyticsReport(
                    0,
                    0,
                    BigDecimal.ZERO.setScale(2),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Map.of());
        }

        long tradeCount = snapshot.size();

        long highestPricePaise = snapshot.stream()
                .mapToLong(Trade::getPricePaise)
                .max()
                .orElseThrow();

        long lowestPricePaise = snapshot.stream()
                .mapToLong(Trade::getPricePaise)
                .min()
                .orElseThrow();

        long totalQuantity = snapshot.stream()
                .mapToLong(Trade::getQuantity)
                .reduce(0L, Math::addExact);

        BigDecimal totalTradedValue = snapshot.stream()
                .map(TradeAnalytics::calculateTradeValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);

        BigDecimal sumOfExecutionPrices = snapshot.stream()
                .map(trade -> BigDecimal.valueOf(trade.getPricePaise()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageExecutionPrice = sumOfExecutionPrices
                .movePointLeft(2)
                .divide(BigDecimal.valueOf(tradeCount), REPORT_SCALE, RoundingMode.HALF_UP);

        BigDecimal volumeWeightedAveragePrice = totalTradedValue
                .divide(BigDecimal.valueOf(totalQuantity), REPORT_SCALE, RoundingMode.HALF_UP);

        Map<Long, Long> quantityByPrice = snapshot.stream()
                .collect(Collectors.groupingBy(
                        Trade::getPricePaise,
                        TreeMap::new,
                        Collectors.summingLong(Trade::getQuantity)));

        return new TradeAnalyticsReport(
                tradeCount,
                totalQuantity,
                totalTradedValue,
                Optional.of(averageExecutionPrice),
                Optional.of(volumeWeightedAveragePrice),
                Optional.of(BigDecimal.valueOf(highestPricePaise, 2)),
                Optional.of(BigDecimal.valueOf(lowestPricePaise, 2)),
                quantityByPrice);
    }

    /**
     * Returns trades whose execution price is at least the supplied minimum.
     * Results are ordered by timestamp (oldest first).
     */
    public static List<Trade> findTradesAtOrAbovePrice(
            Collection<Trade> trades, long minimumPricePaise) {
        Objects.requireNonNull(trades, "trades cannot be null");
        if (minimumPricePaise < 0) {
            throw new IllegalArgumentException("Minimum price cannot be negative");
        }

        return trades.stream()
                .filter(trade -> trade.getPricePaise() >= minimumPricePaise)
                .sorted(Comparator.comparingLong(Trade::getTimestamp))
                .toList();
    }

    /** Returns the most recent trades, newest first, limited to the requested count. */
    public static List<Trade> getRecentTrades(Collection<Trade> trades, int count) {
        Objects.requireNonNull(trades, "trades cannot be null");
        if (count < 0) {
            throw new IllegalArgumentException("Trade count cannot be negative");
        }

        return trades.stream()
                .sorted(Comparator.comparingLong(Trade::getTimestamp).reversed())
                .limit(count)
                .toList();
    }

    /** Returns one page of trades; page numbering starts at zero. */
    public static List<Trade> getTradesPage(
            Collection<Trade> trades, int pageNumber, int pageSize) {
        Objects.requireNonNull(trades, "trades cannot be null");
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }

        long recordsToSkip = (long) pageNumber * pageSize;
        return trades.stream()
                .sorted(Comparator.comparingLong(Trade::getTimestamp))
                .skip(recordsToSkip)
                .limit(pageSize)
                .toList();
    }

    private static BigDecimal calculateTradeValue(Trade trade) {
        // pricePaise is in paise; movePointLeft(2) converts paise to rupees.
        return BigDecimal.valueOf(trade.getPricePaise())
                .multiply(BigDecimal.valueOf(trade.getQuantity()))
                .movePointLeft(2);
    }
}