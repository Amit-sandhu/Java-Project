# MatchBook — Integrated Limit Order Book Simulator

This archive preserves the original MatchBook team application and adds the Trade Data Persistence & Analytics contribution as a separate, integrated module.

## Run

From this directory, compile all Java source files:

**Windows PowerShell**
```powershell
New-Item -ItemType Directory -Force out | Out-Null
$files = Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp "lib/*" -d out $files
java -cp "out;lib/*" matchbook.Main
```

**Linux / macOS**
```bash
mkdir -p out
find matchbook -name "*.java" > sources-linux.txt
javac -encoding UTF-8 -cp "lib/*" -d out @sources-linux.txt
java -cp "out:lib/*" matchbook.Main
```

The application requires the configured MySQL database used by the original MatchBook project. Database setup and credentials remain part of the teammate application and were not changed by this integration.

## Integrated Trade Data Persistence & Analytics

The existing team `Trade` model is retained. The new CSV mapper is adapted to its seven fields:

`tradeId,buyOrderId,sellOrderId,symbol,pricePaise,quantity,timestamp`

Prices remain integer paise to preserve exact price precision.

New package: `matchbook.persistence`
- `TradeRepository` — repository contract (`saveAll`, `appendAll`, `findAll`)
- `CsvMapper<T>` — generic object/CSV mapping contract
- `CsvRepository<T>` — generic UTF-8 CSV file operations, header validation and safe snapshot replacement
- `TradeCsvMapper` — mapping for the integrated seven-field Trade model
- `TradeCsvRepository` — trade-specific repository

The original `matchbook.storage.CsvService` is retained for the teammate's order-import and CSV-export features.

## Concurrent post-trade processing

After login, choose **11. Concurrent CSV persistence + analytics**.

The menu action:
1. Retrieves completed trades from the existing `ExchangeService`.
2. Creates an immutable snapshot (`List.copyOf`).
3. Runs three tasks using a fixed `ExecutorService` pool:
   - Producer `Runnable` sends trades into a bounded queue.
   - CSV consumer `Runnable` drains the queue and calls `TradeCsvRepository.saveAll`.
   - Analytics `Callable` calculates a report using the existing `TradeAnalytics`.
4. Uses `Future.get()` to wait for task completion and retrieve the report.

`matchbook.concurrency.TradeProcessingQueue` demonstrates `synchronized`, `wait()` and `notifyAll()`.

The existing team matching thread, order event queue, automated traders, database access and analytics implementation are retained. The matching engine/database service is not accessed from the new worker threads; only the copied trade snapshot is processed.

Default CSV output: `data/concurrent_trades.csv`.

## Existing team features retained

- Authentication and user registration
- MySQL persistence
- Order placement and cancellation
- Matching engine and order book
- Existing order event queue and matching thread
- Automated trader threads
- Order CSV import and trade CSV export
- Existing analytics menu option (10)

The new concurrent CSV persistence and analytics feature is added as menu option 11. Logout is now menu option 12.

## Integration verification

- All original Java source files are preserved.
- Only `matchbook/cli/TerminalMenu.java` is modified among teammate Java source files, to add the new menu option and handler.
- The integrated source tree compiles with `javac`.
- A standalone smoke test verified producer/consumer execution, analytics result retrieval, CSV save/read/append, and preservation of symbol and integer price fields.
