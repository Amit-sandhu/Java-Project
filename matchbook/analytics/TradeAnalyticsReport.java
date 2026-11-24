package matchbook.analytics;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable summary of a collection of executed trades.
 * Prices are stored as paise (1/100 of a rupee) in the Trade model.
 */
public final class TradeAnalyticsReport {

    private final long tradeCount;
    private final long totalQuantity;
    private final BigDecimal totalTradedValue;
    private final Optional<BigDecimal> averageExecutionPrice;
    private final Optional<BigDecimal> volumeWeightedAveragePrice;
    private final Optional<BigDecimal> highestExecutionPrice;
    private final Optional<BigDecimal> lowestExecutionPrice;
    private final Map<Long, Long> quantityByPricePaise;

    TradeAnalyticsReport(
            long tradeCount,
            long totalQuantity,
            BigDecimal totalTradedValue,
            Optional<BigDecimal> averageExecutionPrice,
            Optional<BigDecimal> volumeWeightedAveragePrice,
            Optional<BigDecimal> highestExecutionPrice,
            Optional<BigDecimal> lowestExecutionPrice,
            Map<Long, Long> quantityByPricePaise) {
        this.tradeCount = tradeCount;
        this.totalQuantity = totalQuantity;
        this.totalTradedValue = totalTradedValue;
        this.averageExecutionPrice = averageExecutionPrice;
        this.volumeWeightedAveragePrice = volumeWeightedAveragePrice;
        this.highestExecutionPrice = highestExecutionPrice;
        this.lowestExecutionPrice = lowestExecutionPrice;
        this.quantityByPricePaise = Collections.unmodifiableMap(new TreeMap<>(quantityByPricePaise));
    }

    public long getTradeCount() {
        return tradeCount;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    /**
     * Total executed value in rupees (not paise).
     */
    public BigDecimal getTotalTradedValue() {
        return totalTradedValue;
    }

    /**
     * Simple average of each trade's execution price, in rupees.
     */
    public Optional<BigDecimal> getAverageExecutionPrice() {
        return averageExecutionPrice;
    }

    /**
     * Quantity-weighted average execution price (VWAP), in rupees.
     */
    public Optional<BigDecimal> getVolumeWeightedAveragePrice() {
        return volumeWeightedAveragePrice;
    }

    public Optional<BigDecimal> getHighestExecutionPrice() {
        return highestExecutionPrice;
    }

    public Optional<BigDecimal> getLowestExecutionPrice() {
        return lowestExecutionPrice;
    }

    /**
     * Returns total executed quantity grouped by price.
     * Keys are the original integer paise prices (100 paise = ₹1.00).
     */
    public Map<Long, Long> getQuantityByPricePaise() {
        return quantityByPricePaise;
    }

    @Override
    public String toString() {
        return "Trade Analytics Report\n"
                + "----------------------\n"
                + "Total trades: " + tradeCount + "\n"
                + "Total quantity: " + totalQuantity + "\n"
                + "Total traded value: ₹" + totalTradedValue.toPlainString() + "\n"
                + "Average execution price: " + display(averageExecutionPrice) + "\n"
                + "Volume-weighted average price (VWAP): " + display(volumeWeightedAveragePrice) + "\n"
                + "Highest execution price: " + display(highestExecutionPrice) + "\n"
                + "Lowest execution price: " + display(lowestExecutionPrice) + "\n"
                + "Quantity by price (paise): " + quantityByPricePaise;
    }

    private static String display(Optional<BigDecimal> value) {
        return value.map(v -> "₹" + v.toPlainString()).orElse("N/A");
    }
}