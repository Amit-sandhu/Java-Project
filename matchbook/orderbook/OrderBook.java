package matchbook.orderbook;

import matchbook.model.Order;
import matchbook.model.OrderSide;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class OrderBook {
    private final TreeMap<Long, ArrayDeque<Order>> buyOrders;
    private final TreeMap<Long, ArrayDeque<Order>> sellOrders;

    public OrderBook() {
        buyOrders = new TreeMap<>(Comparator.reverseOrder());
        sellOrders = new TreeMap<>();
    }

    public void add(Order order) {
        TreeMap<Long, ArrayDeque<Order>> side = getSide(order.getSide());
        ArrayDeque<Order> level = side.get(order.getPricePaise());
        if (level == null) {
            level = new ArrayDeque<>();
            side.put(order.getPricePaise(), level);
        }
        level.addLast(order);
    }

    public Order getBestOppositeOrder(Order incoming) {
        TreeMap<Long, ArrayDeque<Order>> opposite;
        if (incoming.getSide() == OrderSide.BUY) {
            opposite = sellOrders;
        } else {
            opposite = buyOrders;
        }

        if (opposite.isEmpty()) {
            return null;
        }

        Map.Entry<Long, ArrayDeque<Order>> best = opposite.firstEntry();
        long bestPrice = best.getKey();

        if (incoming.getSide() == OrderSide.BUY && incoming.getPricePaise() < bestPrice) {
            return null;
        }
        if (incoming.getSide() == OrderSide.SELL && incoming.getPricePaise() > bestPrice) {
            return null;
        }

        return best.getValue().peekFirst();
    }

    public void removeIfFinished(Order order) {
        if (order.getRemainingQuantity() > 0) {
            return;
        }

        TreeMap<Long, ArrayDeque<Order>> side = getSide(order.getSide());
        ArrayDeque<Order> level = side.get(order.getPricePaise());

        if (level != null) {
            level.remove(order);
            if (level.isEmpty()) {
                side.remove(order.getPricePaise());
            }
        }
    }

    public boolean cancel(String orderId, int userId) {
        return removeFromSide(buyOrders, orderId, userId)
                || removeFromSide(sellOrders, orderId, userId);
    }

    private boolean removeFromSide(TreeMap<Long, ArrayDeque<Order>> side,
                                   String orderId, int userId) {
        List<Long> emptyPrices = new ArrayList<>();
        boolean removed = false;

        for (Map.Entry<Long, ArrayDeque<Order>> entry : side.entrySet()) {
            ArrayDeque<Order> level = entry.getValue();
            Order found = null;

            for (Order order : level) {
                if (order.getOrderId().equals(orderId) && order.getUserId() == userId) {
                    found = order;
                    break;
                }
            }

            if (found != null) {
                level.remove(found);
                removed = true;
                break;
            }

            if (level.isEmpty()) {
                emptyPrices.add(entry.getKey());
            }
        }

        for (Long price : emptyPrices) {
            side.remove(price);
        }
        return removed;
    }

    public List<Order> getAllOrders() {
        List<Order> result = new ArrayList<>();
        addSideToList(buyOrders, result);
        addSideToList(sellOrders, result);
        return result;
    }

    private void addSideToList(TreeMap<Long, ArrayDeque<Order>> side, List<Order> result) {
        for (ArrayDeque<Order> level : side.values()) {
            result.addAll(level);
        }
    }

    public TreeMap<Long, ArrayDeque<Order>> getBuyOrders() {
        return buyOrders;
    }

    public TreeMap<Long, ArrayDeque<Order>> getSellOrders() {
        return sellOrders;
    }

    private TreeMap<Long, ArrayDeque<Order>> getSide(OrderSide side) {
        if (side == OrderSide.BUY) {
            return buyOrders;
        }
        return sellOrders;
    }
}
