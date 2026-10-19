/* Product Order Management System - Abhay Kumar, 25BCE2537 */
import com.sun.net.httpserver.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Shop backend: tiny HTTP server (JDK only) for the shop counter and warehouse, over JDBC. */
public class ShopServer {
    static final String URL  = env("ORDER_DB_URL", "jdbc:mysql://localhost:3306/order_management");
    static final String USER = env("ORDER_DB_USER", "root");
    static final String PASS = env("ORDER_DB_PASS", "");
    static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    static final Map<String, String> SHELF_QUERIES = new LinkedHashMap<>();
    static {
        SHELF_QUERIES.put("customers", "SELECT * FROM customers ORDER BY customer_number");
        SHELF_QUERIES.put("suppliers", "SELECT * FROM suppliers ORDER BY supplier_number");
        SHELF_QUERIES.put("products",  "SELECT p.*, s.supplier_name FROM products p JOIN suppliers s ON p.supplied_by = s.supplier_number ORDER BY p.product_number");
        SHELF_QUERIES.put("orders",    "SELECT o.*, c.full_name AS customer_name FROM orders o JOIN customers c ON o.placed_by = c.customer_number ORDER BY o.order_number DESC");
        SHELF_QUERIES.put("items",     "SELECT i.*, p.product_name, o.order_status FROM order_items i JOIN products p ON i.product_number = p.product_number JOIN orders o ON i.order_number = o.order_number ORDER BY i.item_ref");
        SHELF_QUERIES.put("payments",  "SELECT y.*, c.full_name AS customer_name FROM payments y JOIN orders o ON y.order_number = o.order_number JOIN customers c ON o.placed_by = c.customer_number ORDER BY y.payment_ref");
        SHELF_QUERIES.put("shipments", "SELECT h.*, c.full_name AS customer_name FROM shipments h JOIN orders o ON h.order_number = o.order_number JOIN customers c ON o.placed_by = c.customer_number ORDER BY h.shipment_ref");
        SHELF_QUERIES.put("sales",     "SELECT * FROM product_sales_summary ORDER BY revenue DESC, product_number");   // view from shop_schema.sql
        SHELF_QUERIES.put("reorder",   "SELECT * FROM reorder_suggestions ORDER BY stock_on_hand, product_number");   // view, new
        SHELF_QUERIES.put("invoices",  "CALL order_invoice(NULL)");   // stored procedure, new (NULL = all orders)
        SHELF_QUERIES.put("reviews",   "SELECT r.*, c.full_name AS customer_name, p.product_name FROM returns_reviews r JOIN customers c ON r.raised_by = c.customer_number JOIN products p ON r.product_number = p.product_number ORDER BY r.reference_number");
    }

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("JDBC CONNECTION SUCCESSFUL -> " + URL);
        } catch (SQLException e) {
            System.out.println("JDBC CONNECTION FAILED: " + e.getMessage());
            System.out.println("Check: MySQL running, warehouse/shop_schema.sql loaded, ORDER_DB_PASS correct, connector jar in the connector folder.");
            return;
        }
        HttpServer s = HttpServer.create(new InetSocketAddress("0.0.0.0", Integer.parseInt(env("PORT", "8080"))), 0);
        s.createContext("/", ShopServer::handleRequest);
        s.start();
        System.out.println("Open http://localhost:" + env("PORT", "8080"));
    }

    static void handleRequest(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        try {
            if (p.equals("/")) { send(ex, 200, "text/html; charset=utf-8", new String(Files.readAllBytes(Path.of("storefront", "index.html")), StandardCharsets.UTF_8)); return; }
            if (!p.startsWith("/api/")) { send(ex, 404, "application/json", "{\"error\":\"not found\"}"); return; }
            Map<String, String> f = form(ex);
            try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
                String r = handleApi(c, ex.getRequestMethod(), p.substring(5), f);
                if (r == null) send(ex, 404, "application/json", "{\"error\":\"not found\"}");
                else send(ex, 200, "application/json", r);
            }
        } catch (Exception e) {
            send(ex, 400, "application/json", "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    static String handleApi(Connection c, String m, String p, Map<String, String> f) throws Exception {
        if (m.equals("GET")) {
            if (p.equals("summary")) return json(c, "SELECT (SELECT COUNT(*) FROM customers) AS customers, (SELECT COUNT(*) FROM products) AS products, "
                + "(SELECT COUNT(*) FROM orders) AS orders, (SELECT COALESCE(SUM(amount_paid),0) FROM payments WHERE payment_status='Successful') AS revenue_inr, "
                + "(SELECT COUNT(*) FROM shipments WHERE delivery_status IN ('Preparing','In Transit')) AS open_shipments, "
                + "(SELECT COUNT(*) FROM products WHERE stock_on_hand < 50) AS low_stock_products");
            if (SHELF_QUERIES.containsKey(p)) return json(c, SHELF_QUERIES.get(p));
            return null;
        }
        switch (p) {
            case "orders/place":   return placeOrder(c, f);
            case "orders/advance": return advanceOrder(c, f);
            case "orders/cancel":  return cancelOrder(c, f);
            case "reviews/add":    return addReview(c, f);
            case "products/restock":
                if (update(c, "UPDATE products SET stock_on_hand = stock_on_hand + ? WHERE product_number=?", Integer.parseInt(f.get("quantity")), f.get("product")) == 0)
                    throw new IllegalArgumentException("Unknown product");
                return "{\"ok\":true}";
            case "customers/add": {
                String id = nextId(c, "customers", "customer_number", "CUST-", 1000);
                update(c, "INSERT INTO customers (customer_number, full_name, email, phone, city, joined_on, standing) VALUES (?,?,?,?,?,CURDATE(),'Active')",
                       id, f.get("name"), f.get("email"), f.get("phone"), f.get("city"));
                return "{\"ok\":true,\"customer\":\"" + id + "\"}";
            }
            case "customers/standing":
                update(c, "UPDATE customers SET standing=? WHERE customer_number=?", f.get("standing"), f.get("customer"));
                return "{\"ok\":true}";
            case "customers/delete":
                update(c, "DELETE FROM customers WHERE customer_number=?", f.get("customer"));   // FK error if customer has orders
                return "{\"ok\":true}";
            default: return null;
        }
    }

    /** Place a single-product order: validates, reserves stock, creates order + item + payment + shipment atomically. */
    static String placeOrder(Connection c, Map<String, String> f) throws Exception {
        String cust = f.get("customer"), prod = f.get("product"), addr = f.get("address");
        int qty = Integer.parseInt(f.get("quantity"));
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be at least 1");
        if (addr == null || addr.isBlank()) throw new IllegalArgumentException("Delivery address is required");
        if (!"Active".equals(scalar(c, "SELECT standing FROM customers WHERE customer_number=?", cust)))
            throw new IllegalArgumentException("Customer is not active");
        String price = scalar(c, "SELECT price FROM products WHERE product_number=?", prod);
        if (price == null) throw new IllegalArgumentException("Unknown product");
        if (!"Active".equals(scalar(c, "SELECT s.standing FROM products p JOIN suppliers s ON p.supplied_by = s.supplier_number WHERE p.product_number=?", prod)))
            throw new IllegalArgumentException("Supplier of this product is On Hold - product cannot be ordered");
        BigDecimal each = new BigDecimal(price), total = each.multiply(BigDecimal.valueOf(qty));

        c.setAutoCommit(false);
        try {
            if (update(c, "UPDATE products SET stock_on_hand = stock_on_hand - ? WHERE product_number=? AND stock_on_hand >= ?", qty, prod, qty) == 0)
                throw new IllegalArgumentException("Insufficient stock");
            String o = nextId(c, "orders", "order_number", "ORD-", 5000);
            update(c, "INSERT INTO orders (order_number, placed_by, order_date, delivery_address, order_total, currency, order_status) VALUES (?,?,CURDATE(),?,?,'INR','Processing')", o, cust, addr, total);
            update(c, "INSERT INTO order_items (item_ref, order_number, product_number, quantity, price_each, line_total) VALUES (?,?,?,?,?,?)",
                   nextId(c, "order_items", "item_ref", "ITEM-", 7000), o, prod, qty, each, total);
            update(c, "INSERT INTO payments (payment_ref, order_number, amount_paid, currency, payment_method, paid_on, payment_status) VALUES (?,?,?,'INR',?,CURDATE(),'Successful')",
                   nextId(c, "payments", "payment_ref", "PAY-", 4000), o, total, f.getOrDefault("method", "UPI"));
            update(c, "INSERT INTO shipments (shipment_ref, order_number, expected_by, delivery_status) VALUES (?,?,DATE_ADD(CURDATE(), INTERVAL 5 DAY),'Preparing')",
                   nextId(c, "shipments", "shipment_ref", "SHIP-", 6000), o);
            c.commit();
            return "{\"order\":\"" + o + "\",\"total\":" + total + "}";
        } catch (Exception e) { c.rollback(); throw e; }
    }

    /** Processing -> Shipped -> Delivered, keeping the shipment record in step. */
    static String advanceOrder(Connection c, Map<String, String> f) throws SQLException {
        String o = f.get("order"), cur = scalar(c, "SELECT order_status FROM orders WHERE order_number=?", o);
        if (cur == null) throw new SQLException("Unknown order");
        String next = switch (cur) { case "Processing" -> "Shipped"; case "Shipped" -> "Delivered"; default -> null; };
        if (next == null) throw new SQLException("Order is already " + cur);
        update(c, "UPDATE orders SET order_status=? WHERE order_number=?", next, o);
        if (next.equals("Shipped")) {
            String courier = (f.get("courier") == null || f.get("courier").isBlank()) ? "Speedex Logistics" : f.get("courier");
            update(c, "UPDATE shipments SET delivery_status='In Transit', dispatched_on=CURDATE(), courier_partner=?, tracking_number=? WHERE order_number=?",
                   courier, "TRK" + (long) (Math.random() * 90000000L + 10000000L), o);
        } else update(c, "UPDATE shipments SET delivery_status='Delivered' WHERE order_number=?", o);
        return "{\"ok\":true,\"status\":\"" + next + "\"}";
    }

    /** Only Processing orders: cancel, refund payment, restock, log a Cancellation record. */
    static String cancelOrder(Connection c, Map<String, String> f) throws Exception {
        String o = f.get("order"), cur = scalar(c, "SELECT order_status FROM orders WHERE order_number=?", o);
        if (!"Processing".equals(cur)) throw new SQLException("Only Processing orders can be cancelled (this one is " + cur + ")");
        c.setAutoCommit(false);
        try {
            update(c, "UPDATE orders SET order_status='Cancelled' WHERE order_number=?", o);
            update(c, "UPDATE payments SET payment_status='Refunded' WHERE order_number=?", o);
            update(c, "UPDATE shipments SET delivery_status='Not Applicable' WHERE order_number=?", o);
            update(c, "UPDATE products p JOIN order_items i ON i.product_number = p.product_number SET p.stock_on_hand = p.stock_on_hand + i.quantity WHERE i.order_number=?", o);
            update(c, "INSERT INTO returns_reviews (reference_number, order_number, product_number, raised_by, entry_type, reason_or_feedback, rating, status) "
                    + "SELECT ?, o.order_number, (SELECT MIN(product_number) FROM order_items WHERE order_number = o.order_number), o.placed_by, 'Cancellation', ?, NULL, 'Refund Issued' FROM orders o WHERE o.order_number=?",
                   nextId(c, "returns_reviews", "reference_number", "RTRN-", 8000), f.getOrDefault("reason", "Cancelled by customer"), o);
            c.commit();
            return "{\"ok\":true}";
        } catch (Exception e) { c.rollback(); throw e; }
    }

    static String addReview(Connection c, Map<String, String> f) throws SQLException {
        String o = f.get("order"), prod = f.get("product"), type = f.get("type");
        String st = scalar(c, "SELECT order_status FROM orders WHERE order_number=?", o);
        String owner = scalar(c, "SELECT placed_by FROM orders WHERE order_number=?", o);
        if (st == null) throw new SQLException("Unknown order");
        if (scalar(c, "SELECT 1 FROM order_items WHERE order_number=? AND product_number=?", o, prod) == null)
            throw new SQLException("That product is not part of this order");
        Integer rating = null;
        if (type.equals("Review")) {
            if (!st.equals("Delivered")) throw new SQLException("Only delivered orders can be reviewed");
            rating = Integer.parseInt(f.get("rating"));
            if (rating < 1 || rating > 5) throw new SQLException("Rating must be 1-5");
        } else if (!(st.equals("Shipped") || st.equals("Delivered"))) throw new SQLException(type + " needs a shipped or delivered order");
        String ref = nextId(c, "returns_reviews", "reference_number", type.equals("Review") ? "RVW-" : "RTRN-", 8000);
        update(c, "INSERT INTO returns_reviews (reference_number, order_number, product_number, raised_by, entry_type, reason_or_feedback, rating, status) VALUES (?,?,?,?,?,?,?,?)",
               ref, o, prod, owner, type, f.get("text"), rating, type.equals("Review") ? "Published" : "Under Review");
        return "{\"ok\":true,\"reference\":\"" + ref + "\"}";
    }

    // ---------- helpers ----------
    static String nextId(Connection c, String table, String col, String prefix, int base) throws SQLException {
        return prefix + scalar(c, "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(" + col + ",'-',-1) AS UNSIGNED)), " + base + ") + 1 FROM " + table);
    }
    static String scalar(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }
    static int update(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
            return ps.executeUpdate();
        }
    }
    static String json(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(','); first = false;
                sb.append('{');
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    if (i > 1) sb.append(',');
                    Object v = rs.getObject(i);
                    sb.append('"').append(md.getColumnLabel(i)).append("\":");
                    if (v == null) sb.append("null");
                    else if (v instanceof Number) sb.append(v);
                    else sb.append('"').append(esc(v.toString())).append('"');
                }
                sb.append('}');
            }
            return sb.append(']').toString();
        }
    }
    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(' ');
            else b.append(ch);
        }
        return b.toString();
    }
    static Map<String, String> form(HttpExchange ex) throws IOException {
        Map<String, String> m = new HashMap<>();
        if (!ex.getRequestMethod().equals("POST")) return m;
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String kv : body.split("&")) {
            if (kv.isEmpty()) continue;
            String[] x = kv.split("=", 2);
            m.put(URLDecoder.decode(x[0], StandardCharsets.UTF_8), x.length > 1 ? URLDecoder.decode(x[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }
}
