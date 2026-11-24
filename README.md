# MatchBook - Limit Order Book Simulator

A Java-based **Limit Order Book (LOB)** simulator that mimics a real stock exchange matching engine. Built with core Java (no frameworks), it demonstrates order matching, persistence, concurrency, and trade analytics — all running in a single terminal application.

---

## 📖 What is a Limit Order Book (LOB)?

A **Limit Order Book** is the core data structure used by modern electronic exchanges (NSE, BSE, NYSE, NASDAQ) to match buy and sell orders.

| Concept | Description |
|---------|-------------|
| **Order** | An instruction to buy or sell a quantity of a symbol at a specific price (or better). |
| **Limit Order** | An order with a maximum buy price or minimum sell price. It only executes at that price or better. |
| **Bid (Buy Side)** | Highest price buyers are willing to pay. Sorted **descending** (best bid first). |
| **Ask / Offer (Sell Side)** | Lowest price sellers are willing to accept. Sorted **ascending** (best ask first). |
| **Spread** | Difference between best bid and best ask. Tight spread = high liquidity. |
| **Price-Time Priority** | Orders at the same price are matched FIFO (first-in-first-out). |
| **Match / Trade** | When a buy order's price ≥ sell order's price, a **trade** executes at the resting order's price. |

**In MatchBook**: Prices are stored as **integer paise** (100 paise = ₹1.00) to avoid floating-point precision errors. The symbol is fixed to `RELIANCE` for simplicity.

---

## 📁 Folder Structure

```
MatchBook_Integrated_Final/
├── lib/                          # External dependencies
│   └── mysql-connector-j-26.10.0.jar   # MySQL JDBC driver
├── matchbook/                    # Main source package
│   ├── analytics/               # Trade analytics & reporting
│   │   ├── TradeAnalytics.java       # Stream-based analytics engine
│   │   └── TradeAnalyticsReport.java # Immutable report DTO
│   ├── cli/                     # Command-line interface
│   │   └── TerminalMenu.java         # Interactive menu system
│   ├── concurrency/             # Concurrent post-trade processing
│   │   ├── ConcurrentTradeProcessor.java # Orchestrates producer/consumer/analytics
│   │   ├── TradeProcessingQueue.java   # Bounded thread-safe queue (wait/notifyAll)
│   │   └── OrderEvent.java             # Event for order lifecycle
│   ├── db/                      # Database layer (JDBC)
│   │   └── DatabaseManager.java      # MySQL connection, schema, CRUD
│   ├── exceptions/              # Custom checked exceptions
│   │   ├── AuthenticationException.java
│   │   ├── InvalidOrderException.java
│   │   └── OrderNotFoundException.java
│   ├── model/                   # Domain entities (POJOs)
│   │   ├── User.java
│   │   ├── Order.java
│   │   ├── Trade.java
│   │   ├── OrderSide.java            # Enum: BUY / SELL
│   │   └── OrderStatus.java          # Enum: OPEN, PARTIALLY_FILLED, FILLED, CANCELLED
│   ├── orderbook/               # Core matching engine
│   │   └── OrderBook.java            # TreeMap-based price-level order book
│   ├── persistence/             # CSV persistence (new module)
│   │   ├── TradeRepository.java      # Repository interface
│   │   ├── CsvMapper.java            # Generic CSV mapping contract
│   │   ├── CsvRepository.java        # Generic UTF-8 CSV file operations
│   │   ├── TradeCsvMapper.java       # Trade ↔ CSV row mapping
│   │   └── TradeCsvRepository.java   # Trade-specific CSV repository
│   ├── service/                 # Business logic services
│   │   ├── AuthenticationService.java  # User auth (register/login/reset)
│   │   └── ExchangeService.java      # Order placement, matching, cancellation
│   ├── storage/                 # Legacy CSV import/export
│   │   └── CsvService.java           # Order CSV import, trade CSV export
│   └── Main.java                # Application entry point
├── out/                         # Compiled .class files (generated)
├── data/                        # CSV output directory (created at runtime)
├── sources.txt                  # Windows source file list
├── sources-linux.txt            # Linux/macOS source file list
├── README.md                    # This file
├── README_INTEGRATION.md        # Integration documentation
└── INTEGRATION_CHANGELOG.md     # Change log
```

---

## 🗄️ JDBC & Database Setup

MatchBook uses **MySQL** via JDBC for persistent storage of users, orders, and trades.

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 17+ (tested on 17, 21) |
| MySQL Server | 8.0+ |
| MySQL Connector/J | 26.10.0 (included in `lib/`) |

### Database Configuration

Edit `matchbook/db/DatabaseManager.java` (lines 13–18):

```java
// CHANGE THESE TWO VALUES
private static final String DB_URL =
    "jdbc:mysql://localhost:3306/matchbook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

private static final String DB_USER = "matchbook";
private static final String DB_PASSWORD = "matchbook123";
```

### Create Database & User

```sql
-- Run in MySQL shell
CREATE DATABASE matchbook;
CREATE USER 'matchbook'@'localhost' IDENTIFIED BY 'matchbook123';
GRANT ALL PRIVILEGES ON matchbook.* TO 'matchbook'@'localhost';
FLUSH PRIVILEGES;
```

### Schema (Auto-Created on Startup)

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `users` | Authentication & accounts | `user_id` (PK), `username`, `email`, `password_hash`, `password_salt`, `recovery_hash`, `recovery_salt`, `created_at` |
| `orders` | Live & historical orders | `order_id` (PK), `user_id` (FK), `side`, `price_paise`, `original_quantity`, `remaining_quantity`, `status`, `created_at` |
| `trades` | Executed trades | `trade_id` (PK), `buy_order_id`, `sell_order_id`, `symbol`, `price_paise`, `quantity`, `timestamp` |

> Tables are created automatically via `DatabaseManager.initialize()` on first run.

---

## 🧠 Topics Covered

| Category | Topics Demonstrated |
|----------|---------------------|
| **Core Java** | Collections (`TreeMap`, `ArrayDeque`), Streams API, Generics, Records, Enums, `Optional`, `BigDecimal` |
| **JDBC** | `DriverManager`, `PreparedStatement`, `ResultSet`, connection lifecycle, transactions (auto-commit) |
| **Concurrency** | `ExecutorService`, `Future`, `Callable`, `Runnable`, `wait()`/`notifyAll()`, bounded queue, thread-safe snapshots (`List.copyOf`) |
| **Design Patterns** | Repository, Mapper, Strategy (CSV mapping), Factory (ExecutorService), Producer-Consumer |
| **Financial Engineering** | Price-time priority matching, paise-based integer pricing, VWAP, volume-weighted analytics |
| **Persistence** | CSV serialization (UTF-8, headers), safe atomic writes (temp file + rename), append mode |
| **CLI** | Interactive menu, input validation, scanner-based I/O |

---

## 💡 Key Concepts Used

### 1. Order Book (`OrderBook.java`)
- **Buy side**: `TreeMap<Long, ArrayDeque<Order>>` with reverse-order comparator (highest price first)
- **Sell side**: `TreeMap<Long, ArrayDeque<Order>>` natural order (lowest price first)
- Each price level = a queue (FIFO) → **price-time priority**

### 2. Matching Engine (`ExchangeService.java`)
- Incoming order → `OrderBook.getBestOppositeOrder()`
- If crossable → execute trade at **resting order's price**
- Update remaining quantities, statuses
- Persist trade to MySQL (`DatabaseManager.saveTrade()`)
- Generate `OrderEvent` for event queue

### 3. Integer Paise Pricing
```java
// ₹1245.50 → 124550 paise (long)
long pricePaise = 124550;
BigDecimal rupees = BigDecimal.valueOf(pricePaise).movePointLeft(2); // 1245.50
```
- Eliminates floating-point rounding errors
- Exact arithmetic for financial calculations

### 4. Concurrent Post-Trade Processing (`ConcurrentTradeProcessor.java`)
```
┌─────────────────────────────────────────────────────────────┐
│                    ConcurrentTradeProcessor                  │
├─────────────────────────────────────────────────────────────┤
│  1. Snapshot trades (List.copyOf) — immutable, thread-safe  │
│  2. FixedThreadPool(3)                                      │
│     ├─ Producer:  pushes trades → TradeProcessingQueue      │
│     ├─ Consumer:  drains queue → TradeCsvRepository.saveAll │
│     └─ Analytics: TradeAnalytics.generateReport(snapshot)   │
│  3. Future.get() — wait for all, retrieve report            │
└─────────────────────────────────────────────────────────────┘
```

### 5. TradeProcessingQueue (`wait()` / `notifyAll()`)
- Bounded capacity (default 32)
- `put()` blocks when full
- `take()` blocks when empty
- `close()` signals end-of-stream (returns `null`)

### 6. Analytics (`TradeAnalytics.java`)
| Metric | Formula |
|--------|---------|
| **Trade Count** | `trades.size()` |
| **Total Quantity** | `Σ quantity` |
| **Total Traded Value** | `Σ (pricePaise × quantity) / 100` |
| **Average Execution Price** | `Σ price / count` |
| **VWAP** | `Total Traded Value / Total Quantity` |
| **High / Low Price** | `max/min pricePaise` |
| **Quantity by Price** | `Map<pricePaise, totalQty>` (TreeMap) |

---

## ⚙️ Workflow

```mermaid
flowchart TD
    A[Start Main] --> B[DatabaseManager.initialize]
    B --> C[Create tables if not exist]
    C --> D[Load open orders from MySQL]
    D --> E[ExchangeService starts matching thread]
    E --> F[AuthenticationService ready]
    F --> G[TerminalMenu.start]
    G --> H{User Action}
    H -->|1. Register| I[Create user in DB]
    H -->|2. Login| J[Validate credentials]
    H -->|3. Place Order| K[ExchangeService.placeOrder]
    K --> L[OrderBook.add / match]
    L --> M[Save order to MySQL]
    L --> N[Save trade to MySQL]
    H -->|4. Cancel Order| O[OrderBook.cancel]
    O --> P[Update MySQL]
    H -->|5. View Orders| Q[Query MySQL]
    H -->|6. View Trades| R[Query MySQL]
    H -->|7. Import Orders CSV| S[CsvService.importOrders]
    H -->|8. Export Trades CSV| T[CsvService.exportTrades]
    H -->|9. Analytics| U[TradeAnalytics.generateReport]
    H -->|10. Concurrent CSV + Analytics| V[ConcurrentTradeProcessor.process]
    V --> W[Producer → Queue → Consumer (CSV)]
    V --> X[Analytics Task (Callable)]
    W --> Y[Save to data/concurrent_trades.csv]
    X --> Z[Return TradeAnalyticsReport]
    H -->|12. Logout| AA[Return to login]
    H -->|11. Exit| AB[Shutdown executor, close DB]
```

---

## 🚀 Commands to Compile & Run

### Linux / macOS

```bash
# 1. Navigate to project root
cd /home/amit/Desktop/MatchBook_Integrated_Final

# 2. Compile all Java sources
mkdir -p out
find matchbook -name "*.java" > sources-linux.txt
javac -encoding UTF-8 -cp "lib/*" -d out @sources-linux.txt

# 3. Run the application
java -cp "out:lib/*" matchbook.Main
```

### Windows PowerShell

```powershell
# 1. Navigate to project root
cd C:\path\to\MatchBook_Integrated_Final

# 2. Compile all Java sources
New-Item -ItemType Directory -Force out | Out-Null
$files = Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp "lib/*" -d out $files

# 3. Run the application
java -cp "out;lib/*" matchbook.Main
```

### Verify Compilation

```bash
# Check compiled classes
ls out/matchbook/
# Should show: analytics/ cli/ concurrency/ db/ exceptions/ model/ orderbook/ persistence/ service/ storage/ Main.class
```

---

## 📋 Menu Options (Terminal)

| # | Option | Description |
|---|--------|-------------|
| 1 | **Register** | Create new user account |
| 2 | **Login** | Authenticate with username + password |
| 3 | **Place Order** | Submit BUY/SELL limit order for RELIANCE |
| 4 | **Cancel Order** | Cancel your own open order by Order ID |
| 5 | **View My Orders** | List your orders (open + history) |
| 6 | **View My Trades** | List your executed trades |
| 7 | **Import Orders (CSV)** | Bulk import orders from CSV file |
| 8 | **Export Trades (CSV)** | Export your trades to CSV |
| 9 | **Analytics** | Run `TradeAnalytics` on your trades |
| 10 | **Order Book Snapshot** | Display current bid/ask levels |
| 11 | **Concurrent CSV + Analytics** | **NEW** — Producer/Consumer + Analytics in parallel |
| 12 | **Logout** | Return to login screen |
| 13 | **Exit** | Shutdown application |

> **Option 11** demonstrates the new concurrent post-trade processing module. Output CSV: `data/concurrent_trades.csv`

---

## 📄 CSV Format (Trade Persistence)

### Trade CSV Columns (7 fields)

| Column | Type | Example | Description |
|--------|------|---------|-------------|
| `tradeId` | String | `TRD-a1b2c3d4` | Unique trade identifier |
| `buyOrderId` | String | `ORD-xyz789` | Buy order ID |
| `sellOrderId` | String | `ORD-abc123` | Sell order ID |
| `symbol` | String | `RELIANCE` | Trading symbol |
| `pricePaise` | Long | `124550` | Price in paise (₹1245.50) |
| `quantity` | Integer | `50` | Executed quantity |
| `timestamp` | Long | `1700000000000` | Epoch milliseconds |

### Sample CSV Row

```csv
tradeId,buyOrderId,sellOrderId,symbol,pricePaise,quantity,timestamp
TRD-a1b2c3d4,ORD-xyz789,ORD-abc123,RELIANCE,124550,50,1700000000000
```

---

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | Ensure `lib/mysql-connector-j-26.10.0.jar` exists and classpath includes `lib/*` |
| `Communications link failure` | Check MySQL is running on `localhost:3306`; verify `DB_URL` |
| `Access denied for user` | Verify `DB_USER`/`DB_PASSWORD`; run GRANT statements |
| `javac: command not found` | Install JDK 17+ and ensure `javac` is in PATH |
| `Unsupported class file major version` | Use Java 17+ (class file version 61+) |
| CSV not found | Check `data/` directory exists; app creates it on first write |

---

## 📚 Further Reading

- **Original Integration Docs**: [`README_INTEGRATION.md`](README_INTEGRATION.md)
- **Change Log**: [`INTEGRATION_CHANGELOG.md`](INTEGRATION_CHANGELOG.md)
- **Order Book Algorithm**: Price-time priority matching (standard exchange model)
- **Concurrency**: Java `ExecutorService`, `Future`, `wait/notifyAll` patterns
- **Financial Precision**: Always use integer paise or `BigDecimal` — never `float`/`double`

---

## 📝 License

Educational / Academic project. No warranty implied.

---

*Built with ☕ Java 17+ | MySQL | Zero external frameworks*
