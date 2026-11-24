package matchbook.model;

public class Trade {
    private final String tradeId;
    private final String buyOrderId;
    private final String sellOrderId;
    private final String symbol;
    private final long pricePaise;
    private final int quantity;
    private final long timestamp;

    public Trade(String tradeId, String buyOrderId, String sellOrderId,
                 String symbol, long pricePaise, int quantity, long timestamp) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.pricePaise = pricePaise;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public String getTradeId() { return tradeId; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public String getSymbol() { return symbol; }
    public long getPricePaise() { return pricePaise; }
    public int getQuantity() { return quantity; }
    public long getTimestamp() { return timestamp; }
}
