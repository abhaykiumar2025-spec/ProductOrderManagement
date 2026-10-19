/* Product Order Management System - Abhay Kumar, 25BCE2537 */
import java.sql.*;

/** Shop console JDBC demo: connect, run queries, then CRUD on the customers table. */
public class ShopConsole {
    static final String URL  = env("ORDER_DB_URL", "jdbc:mysql://localhost:3306/order_management");
    static final String USER = env("ORDER_DB_USER", "root");
    static final String PASS = env("ORDER_DB_PASS", "");
    static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    static void printQuery(Connection c, String title, String sql) throws SQLException {
        System.out.println("\n" + title);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= n; i++) { if (i > 1) sb.append(" | "); sb.append(rs.getString(i)); }
                System.out.println(sb);
            }
        }
    }
    static int runUpdate(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        }
    }

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("=================================");
            System.out.println("   JDBC CONNECTION SUCCESSFUL");
            System.out.println("=================================");

            printQuery(con, "1. ALL CUSTOMERS", "SELECT customer_number, full_name, city, standing FROM customers");
            printQuery(con, "2. PRODUCTS WITH SUPPLIER (JOIN)",
                "SELECT p.product_number, p.product_name, s.supplier_name, p.price FROM products p JOIN suppliers s ON p.supplied_by = s.supplier_number");
            printQuery(con, "3. ORDERS WITH CUSTOMER NAME (JOIN)",
                "SELECT o.order_number, c.full_name, o.order_total, o.order_status FROM orders o JOIN customers c ON o.placed_by = c.customer_number ORDER BY o.order_number");
            printQuery(con, "4. TOTAL REVENUE (SUM of successful payments)",
                "SELECT SUM(amount_paid) AS revenue FROM payments WHERE payment_status = 'Successful'");
            printQuery(con, "5. GROUP BY - ORDERS PER STATUS",
                "SELECT order_status, COUNT(*) AS orders FROM orders GROUP BY order_status ORDER BY order_status");
            printQuery(con, "6. GROUP BY + HAVING - CATEGORIES WITH 2+ PRODUCTS",
                "SELECT category, COUNT(*) AS products FROM products GROUP BY category HAVING COUNT(*) >= 2");
            printQuery(con, "7. ORDER DETAILS (3-TABLE JOIN)",
                "SELECT o.order_number, p.product_name, i.quantity, i.line_total FROM order_items i JOIN orders o ON i.order_number = o.order_number JOIN products p ON i.product_number = p.product_number ORDER BY o.order_number");
            printQuery(con, "8. AVERAGE RATING PER PRODUCT",
                "SELECT p.product_name, AVG(r.rating) AS avg_rating FROM returns_reviews r JOIN products p ON r.product_number = p.product_number WHERE r.entry_type = 'Review' GROUP BY p.product_name");
            printQuery(con, "9. CUSTOMERS IN SELECTED CITIES (IN)", "SELECT full_name, city FROM customers WHERE city IN ('Chennai','Mumbai')");
            printQuery(con, "10. NAMES STARTING WITH 'A' (LIKE)", "SELECT customer_number, full_name FROM customers WHERE full_name LIKE 'A%'");

            // ---- CRUD on customers ----
            String id = "CUST-1099";
            runUpdate(con, "DELETE FROM customers WHERE customer_number = ?", id);   // safe to re-run
            System.out.println("\nCRUD 1. CREATE - INSERT");
            System.out.println(runUpdate(con, "INSERT INTO customers (customer_number, full_name, email, phone, city, joined_on, standing) VALUES (?,?,?,?,?,CURDATE(),?)",
                id, "Arun Kumar", "arun.kumar@example.com", "+91 90000 00000", "Vellore", "Active") + " customer inserted.");
            printQuery(con, "CRUD 2. READ - SELECT", "SELECT customer_number, full_name, city FROM customers WHERE customer_number = '" + id + "'");
            System.out.println("\nCRUD 3. UPDATE");
            System.out.println(runUpdate(con, "UPDATE customers SET city = ? WHERE customer_number = ?", "Coimbatore", id) + " customer updated.");
            printQuery(con, "Customer after UPDATE:", "SELECT customer_number, full_name, city FROM customers WHERE customer_number = '" + id + "'");
            System.out.println("\nCRUD 4. DELETE");
            System.out.println(runUpdate(con, "DELETE FROM customers WHERE customer_number = ?", id) + " customer deleted.");
        } catch (SQLException e) {
            System.out.println("Database Error:");
            e.printStackTrace();
            return;
        }
        System.out.println("\n=================================");
        System.out.println("   DATABASE CONNECTION CLOSED");
        System.out.println("=================================");
    }
}
