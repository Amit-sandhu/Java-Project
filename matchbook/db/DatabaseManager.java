
package matchbook.db;

import matchbook.model.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private Connection connection;

    // CHANGE THESE TWO VALUES
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/matchbook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String DB_USER = "matchbook";
    private static final String DB_PASSWORD = "matchbook123";

    public void initialize() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found. Check your JAR classpath.", e);
        }

        connection = DriverManager.getConnection(
                DB_URL, DB_USER, DB_PASSWORD);

        try (Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "user_id INT PRIMARY KEY AUTO_INCREMENT," +
                "username VARCHAR(100) NOT NULL UNIQUE," +
                "email VARCHAR(255) NOT NULL UNIQUE," +
                "password_hash VARCHAR(255) NOT NULL," +
                "password_salt VARCHAR(255) NOT NULL," +
                "recovery_hash VARCHAR(255) NOT NULL," +
                "recovery_salt VARCHAR(255) NOT NULL," +
                "created_at BIGINT NOT NULL" +
                ") ENGINE=InnoDB"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS orders (" +
                "order_id VARCHAR(100) PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "username VARCHAR(100) NOT NULL," +
                "side VARCHAR(10) NOT NULL," +
                "price_paise BIGINT NOT NULL," +
                "original_quantity INT NOT NULL," +
                "remaining_quantity INT NOT NULL," +
                "status VARCHAR(30) NOT NULL," +
                "created_at BIGINT NOT NULL," +
                "FOREIGN KEY(user_id) REFERENCES users(user_id)" +
                ") ENGINE=InnoDB"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS trades (" +
                "trade_id VARCHAR(100) PRIMARY KEY," +
                "buy_order_id VARCHAR(100) NOT NULL," +
                "sell_order_id VARCHAR(100) NOT NULL," +
                "symbol VARCHAR(50) NOT NULL," +
                "price_paise BIGINT NOT NULL," +
                "quantity INT NOT NULL," +
                "timestamp BIGINT NOT NULL" +
                ") ENGINE=InnoDB"
            );
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void createUser(String username, String email,
            String passwordHash, String passwordSalt,
            String recoveryHash, String recoverySalt)
            throws SQLException {

        String sql = "INSERT INTO users(username,email,password_hash,password_salt," +
                "recovery_hash,recovery_salt,created_at) VALUES(?,?,?,?,?,?,?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, passwordSalt);
            statement.setString(5, recoveryHash);
            statement.setString(6, recoverySalt);
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public User authenticate(String username, String passwordHash)
            throws SQLException {

        String sql = "SELECT user_id, username, email FROM users " +
                "WHERE username = ? AND password_hash = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new User(
                            result.getInt("user_id"),
                            result.getString("username"),
                            result.getString("email"));
                }
            }
        }
        return null;
    }

    public boolean resetPassword(String username, String email,
            String recoveryHash, String newHash, String newSalt)
            throws SQLException {

        String sql = "UPDATE users SET password_hash=?, password_salt=? " +
                "WHERE username=? AND email=? AND recovery_hash=?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newHash);
            statement.setString(2, newSalt);
            statement.setString(3, username);
            statement.setString(4, email);
            statement.setString(5, recoveryHash);

            return statement.executeUpdate() == 1;
        }
    }

    public void saveOrder(Order order) throws SQLException {

        String sql = "INSERT INTO orders(order_id,user_id,username,side,price_paise," +
                "original_quantity,remaining_quantity,status,created_at) " +
                "VALUES(?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, order.getOrderId());
            statement.setInt(2, order.getUserId());
            statement.setString(3, order.getUsername());
            statement.setString(4, order.getSide().name());
            statement.setLong(5, order.getPricePaise());
            statement.setInt(6, order.getOriginalQuantity());
            statement.setInt(7, order.getRemainingQuantity());
            statement.setString(8, order.getStatus().name());
            statement.setLong(9, order.getCreatedAt());
            statement.executeUpdate();
        }
    }

    public void updateOrder(Order order) throws SQLException {

        String sql = "UPDATE orders SET remaining_quantity=?, status=? WHERE order_id=?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, order.getRemainingQuantity());
            statement.setString(2, order.getStatus().name());
            statement.setString(3, order.getOrderId());
            statement.executeUpdate();
        }
    }

    public void saveTrade(Trade trade) throws SQLException {

        String sql = "INSERT INTO trades(trade_id,buy_order_id,sell_order_id,symbol," +
                "price_paise,quantity,timestamp) VALUES(?,?,?,?,?,?,?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trade.getTradeId());
            statement.setString(2, trade.getBuyOrderId());
            statement.setString(3, trade.getSellOrderId());
            statement.setString(4, trade.getSymbol());
            statement.setLong(5, trade.getPricePaise());
            statement.setInt(6, trade.getQuantity());
            statement.setLong(7, trade.getTimestamp());
            statement.executeUpdate();
        }
    }

    public List<Order> loadOpenOrders() throws SQLException {

        List<Order> orders = new ArrayList<>();

        String sql = "SELECT * FROM orders WHERE remaining_quantity > 0 " +
                "AND status IN ('OPEN','PARTIALLY_FILLED') ORDER BY created_at";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                Order order = new Order(
                        result.getString("order_id"),
                        result.getInt("user_id"),
                        result.getString("username"),
                        OrderSide.valueOf(result.getString("side")),
                        result.getLong("price_paise"),
                        result.getInt("original_quantity"),
                        result.getLong("created_at"));

                order.restore(
                        result.getInt("remaining_quantity"),
                        OrderStatus.valueOf(result.getString("status")));

                orders.add(order);
            }
        }
        return orders;
    }

    public List<Order> getUserOrders(int userId) throws SQLException {

        List<Order> orders = new ArrayList<>();

        String sql = "SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {

                    Order order = new Order(
                            result.getString("order_id"),
                            result.getInt("user_id"),
                            result.getString("username"),
                            OrderSide.valueOf(result.getString("side")),
                            result.getLong("price_paise"),
                            result.getInt("original_quantity"),
                            result.getLong("created_at"));

                    order.restore(
                            result.getInt("remaining_quantity"),
                            OrderStatus.valueOf(result.getString("status")));

                    orders.add(order);
                }
            }
        }
        return orders;
    }

    public List<Trade> getTrades() throws SQLException {

        List<Trade> trades = new ArrayList<>();

        String sql = "SELECT * FROM trades ORDER BY timestamp DESC";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                trades.add(new Trade(
                        result.getString("trade_id"),
                        result.getString("buy_order_id"),
                        result.getString("sell_order_id"),
                        result.getString("symbol"),
                        result.getLong("price_paise"),
                        result.getInt("quantity"),
                        result.getLong("timestamp")));
            }
        }
        return trades;
    }

    public List<Trade> getUserTrades(int userId) throws SQLException {

        List<Trade> trades = new ArrayList<>();

        String sql = "SELECT DISTINCT t.* FROM trades t " +
                "JOIN orders o ON (t.buy_order_id=o.order_id OR t.sell_order_id=o.order_id) " +
                "WHERE o.user_id=? ORDER BY t.timestamp DESC";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    trades.add(new Trade(
                            result.getString("trade_id"),
                            result.getString("buy_order_id"),
                            result.getString("sell_order_id"),
                            result.getString("symbol"),
                            result.getLong("price_paise"),
                            result.getInt("quantity"),
                            result.getLong("timestamp")));
                }
            }
        }
        return trades;
    }

    public boolean orderBelongsToUser(String orderId, int userId)
            throws SQLException {

        String sql = "SELECT order_id FROM orders WHERE order_id=? AND user_id=? " +
                "AND remaining_quantity > 0";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            statement.setInt(2, userId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
