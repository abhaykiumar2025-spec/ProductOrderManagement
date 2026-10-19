# DBMS ASSESSMENT – 9
## JDBC CONNECTIVITY – PRODUCT ORDER MANAGEMENT SYSTEM (Backend + Frontend)
**Name / Reg. No.:** Abhay Kumar / 25BCE2537  **Dataset:** Product Order Management System (8 tables)

---
## STEPS TO REACHING CONNECTIVITY
### 1. REQUIREMENTS
1. MySQL 8.0  2. MySQL Connector/J JAR file  3. JDK 25  4. VS Code

### PART 1. INSTALLING THE JAR FILE
Extract Connector/J and copy **only** `mysql-connector-j-26.7.0.jar` directly into `connector` (not the whole extracted folder).
> 📷 Screenshot: connector folder showing the .jar

### MYSQL / JDK CHECK
```
mysql --version
java -version
javac -version
```
> 📷 Screenshot: version outputs

### EXECUTION FLOW
```
Java 25 → Check MySQL → Create database (order_management) → Create 8 tables + data (warehouse/shop_schema.sql)
→ Test SQL → VS Code → Add JDBC .jar to connector → Write JDBC program + backend → Execute SQL through Java → Frontend
```

### CREATING THE DATABASE
```
mysql -u root -p < warehouse/shop_schema.sql
```
Tables: `customers, suppliers, products, orders, order_items, payments, shipments, returns_reviews`
> 📷 Screenshot: SHOW TABLES; SELECT * FROM customers;

---
## PART 2. ESTABLISHING CONNECTIVITY THROUGH JAVA IN VS CODE
### Project structure
```
ProductOrderManagement
├── connector        (mysql-connector-j-26.7.0.jar)
├── warehouse        (shop_schema.sql – tables, data, view, procedures)
├── storefront       (index.html – frontend)
├── ShopConsole.java (console JDBC demo)
├── ShopServer.java  (backend)
├── start_shop.bat
└── REPORT.md
```
### Connection code
```java
String url = "jdbc:mysql://localhost:3306/order_management";
Connection con = DriverManager.getConnection(url, "root", password);
System.out.println("JDBC CONNECTION SUCCESSFUL!");
```
Password comes from the environment variable `ORDER_DB_PASS`.

### Compile and run (PowerShell, inside the project folder)
```
$env:ORDER_DB_PASS = "your_mysql_password"
javac -cp ".;connector\*" ShopConsole.java ShopServer.java
java -cp ".;connector\*" ShopConsole
```
### QUERIES EXECUTED
| # | Query | Purpose |
|---|---|---|
| 1 | SELECT from customers | All customers |
| 2 | products JOIN suppliers | Product with supplier |
| 3 | orders JOIN customers | Orders with customer name |
| 4 | SUM(amount_paid) WHERE Successful | Total revenue |
| 5 | GROUP BY order_status | Orders per status |
| 6 | GROUP BY category HAVING COUNT(*)>=2 | Busy categories |
| 7 | order_items JOIN orders JOIN products | Order details |
| 8 | AVG(rating) GROUP BY product | Average rating |
| 9 | WHERE city IN (...) | Selected cities |
| 10 | WHERE full_name LIKE 'A%' | Name pattern |

### EXPECTED OUTPUT (with the supplied sample data)
```
=================================
   JDBC CONNECTION SUCCESSFUL
=================================
4. TOTAL REVENUE        10796.00
5. ORDERS PER STATUS    Cancelled | 1   Delivered | 1   Processing | 1   Shipped | 1
6. CATEGORIES WITH 2+   Kitchen | 2
8. AVERAGE RATING       Cotton Bedsheet Set | 5.0000   Wireless Earbuds | 4.0000
10. LIKE 'A%'           CUST-1001 | Ananya Rao
```
(Queries 4, 5, 6 and 8 were checked against the supplied data; paste your own screenshot of the real run.)

### CRUD OPERATIONS (customers, PreparedStatement)
```
CRUD 1. CREATE → 1 customer inserted.      CRUD 2. READ → CUST-1099 | Arun Kumar | Vellore
CRUD 3. UPDATE → 1 customer updated.       CRUD 4. DELETE → 1 customer deleted.
```
---
## PART 3. BACKEND + FRONTEND
```
Browser (storefront/index.html) ──fetch /api/*──► ShopServer.java ──JDBC──► MySQL: order_management
```
Run: `java -cp ".;connector\*" ShopServer`, then open **http://localhost:8081** (badge turns green: "JDBC connected · MySQL").

| Method | Path | Action |
|---|---|---|
| GET | `/api/summary`, `customers`, `suppliers`, `products`, `orders`, `items`, `payments`, `shipments`, `reviews` | Read data |
| GET | `/api/sales`, `reorder`, `invoices` | View and stored procedure (extra DBMS parts) |
| POST | `/api/orders/place` | Validate, reserve stock, create order + item + payment + shipment (one DB transaction) |
| POST | `/api/orders/advance` | Processing → Shipped → Delivered, updates shipment |
| POST | `/api/orders/cancel` | Processing only: refund payment, restock, log cancellation |
| POST | `/api/reviews/add` | Review (delivered orders, rating 1-5), damage report or return |
| POST | `/api/products/restock`, `/api/customers/add · standing · delete` | Stock and customer CRUD |

### Business rules enforced by the backend
- Inactive customers cannot order; products from suppliers that are *On Hold* cannot be ordered.
- Orders fail with "Insufficient stock" if quantity exceeds stock (atomic `UPDATE ... WHERE stock >= qty`).
- Cancelling refunds the payment, restocks the items and records a Cancellation row.
- Customers with orders cannot be deleted (foreign key).

### Try it
1. Orders → CUST-1001, Wireless Earbuds, qty 2 → order placed, stock 200 → 198.
2. Advance it twice → Shipped (tracking number created) → Delivered.
3. Returns & Reviews → add a 5-star review for that order.
4. Orders → place another, then cancel → payment Refunded, stock restored.
5. Order the Non-Stick Pan → rejected (supplier On Hold).

### Data note
In the source sheet, ITEM-7003 lists the Non-Stick Pan (PROD-3004, price 899) at INR 3,499 so that ORD-5002 totals 5,798. The data was loaded as given.

### Troubleshooting
Open the page through **http://localhost:8081** with the backend running; opening `storefront/index.html` alone shows "Backend not running". Extract the zip once and work inside the folder that directly contains `ShopConsole.java`.

---

## DEPLOYING ON RENDER
The project is online at **https://product-order-management-abhay.onrender.com** (backend: https://fraudmgmt.onrender.com/orders/).
It runs on Render's free plan inside the same Docker container as Nikitha's Fraud Management project, because the free plan has enough hours for only one service running all day. nginx sends `/orders/` to `ShopServer`, and the database is MariaDB (MySQL compatible, same SQL).

Changes for this:
- `ShopServer.java` reads the port from `PORT` (default 8080). `start_shop.bat` sets `PORT=8081`, so the local steps above still use **http://localhost:8081**.
- `storefront/index.html` calls `api/...` without the leading `/`, so it also works under `/orders/`.

Note: the free plan does not keep the disk, so the data goes back to the sample data after a restart.

---

## EXTRA DBMS PARTS
I added three small things at the end of `warehouse/shop_schema.sql`. The 8 tables, the sample data and the old endpoints are unchanged.

### 1. View `product_sales_summary`
Units sold, revenue and average review rating for each product, with its supplier and stock. Cancelled orders are not counted.
```sql
SELECT * FROM product_sales_summary;
```
Shown in the new **Product Sales** tab (`GET /api/sales`).

### 2. Indexes
```sql
CREATE INDEX idx_order_status    ON orders (order_status);
CREATE INDEX idx_delivery_status ON shipments (delivery_status);
CREATE INDEX idx_stock           ON products (stock_on_hand);
```
These are the columns the dashboard counts by (open shipments, low stock). Check with `SHOW INDEX FROM orders;`.

### 3. Stored procedure `customer_orders`
Lists one customer's orders with payment and delivery status. It needs `DELIMITER $$` because the body has `;` inside.
```sql
CALL customer_orders('CUST-1001');
```
> 📷 *Screenshot: Product Sales tab, SHOW INDEX output, CALL customer_orders output*

---

## SHOP THEME AND TWO NEW FEATURES

### Theme
I gave the project a shop / warehouse theme: the database is the warehouse, the web page is the storefront, and the classes are named after the shop. The title is still **Product Order Management System**, and all old routes, JSON, tables and sample data are the same.

| Old name | New name |
|---|---|
| `LIB/` | `connector/` |
| `sql/schema.sql` | `warehouse/shop_schema.sql` |
| `web/index.html` | `storefront/index.html` |
| `run.bat` | `start_shop.bat` |
| `OrderServer.java` | `ShopServer.java` |
| `JDBCExample.java` | `ShopConsole.java` |
| `VIEWS`, `handle`, `route`, `advance`, `cancel` | `SHELF_QUERIES`, `handleRequest`, `handleApi`, `advanceOrder`, `cancelOrder` |
| `show`, `exec` (console) | `printQuery`, `runUpdate` |

`start_shop.bat` does the same as the old `run.bat` (optionally load the schema, compile both classes, start the server on 8081) with the new names. The console demo runs with `java -cp ".;connector\*" ShopConsole`.

### 4. View `reorder_suggestions` (tab **Reorder List**, `GET /api/reorder`)
Products whose stock is below the reorder level of 100, with how many to order to get back to 150:
- `WHERE p.stock_on_hand < 100` keeps only low-stock products, and `150 - stock_on_hand` is the suggested quantity;
- a small subquery adds the units already sold (cancelled orders are not counted);
- the join with `suppliers` shows who to contact, and a `CASE` says "Find another supplier" when that supplier is *On Hold*.
```sql
SELECT * FROM reorder_suggestions;
```
With the sample data only PROD-3002 (Ceramic Dinner Set, stock 65) is listed, with a suggested order of 85.

### 5. Stored procedure `order_invoice` (tab **Invoices**, `GET /api/invoices`)
One line per item of an order, like an invoice: order date, customer, delivery address, product, quantity, price, line total, order total and payment status. It joins `orders → customers → order_items → products`, and uses a `LEFT JOIN payments` so an order still shows even without a payment row. The parameter picks one order; `NULL` means all (that is what the tab uses). It needs `DELIMITER $$` like `customer_orders`.
```sql
CALL order_invoice('ORD-5002');
CALL order_invoice(NULL);
```
> 📷 *Screenshot: Reorder List tab, Invoices tab, CALL order_invoice output*

I used AI assistance for parts of the code and deployment, as allowed for this project.
