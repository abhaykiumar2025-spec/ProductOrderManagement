-- Product Order Management System: schema + sample data (MySQL 8)
-- Abhay Kumar, 25BCE2537
CREATE DATABASE IF NOT EXISTS order_management;
USE order_management;
DROP TABLE IF EXISTS returns_reviews, shipments, payments, order_items, orders, products, suppliers, customers;

CREATE TABLE customers (
  customer_number VARCHAR(12) PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(120) NOT NULL,
  phone VARCHAR(20),
  city VARCHAR(60),
  joined_on DATE,
  standing VARCHAR(20) NOT NULL              -- Active / Inactive
);
CREATE TABLE suppliers (
  supplier_number VARCHAR(12) PRIMARY KEY,
  supplier_name VARCHAR(80) NOT NULL,
  contact_person VARCHAR(80),
  contact_email VARCHAR(120),
  city VARCHAR(60),
  partner_since DATE,
  standing VARCHAR(20) NOT NULL              -- Active / On Hold
);
CREATE TABLE products (
  product_number VARCHAR(12) PRIMARY KEY,
  product_name VARCHAR(100) NOT NULL,
  supplied_by VARCHAR(12) NOT NULL,
  category VARCHAR(40),
  price DECIMAL(10,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  stock_on_hand INT NOT NULL,
  listed_on DATE,
  FOREIGN KEY (supplied_by) REFERENCES suppliers(supplier_number)
);
CREATE TABLE orders (
  order_number VARCHAR(12) PRIMARY KEY,
  placed_by VARCHAR(12) NOT NULL,
  order_date DATE NOT NULL,
  delivery_address VARCHAR(200),
  order_total DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  order_status VARCHAR(20) NOT NULL,         -- Processing / Shipped / Delivered / Cancelled
  FOREIGN KEY (placed_by) REFERENCES customers(customer_number)
);
CREATE TABLE order_items (
  item_ref VARCHAR(12) PRIMARY KEY,
  order_number VARCHAR(12) NOT NULL,
  product_number VARCHAR(12) NOT NULL,
  quantity INT NOT NULL,
  price_each DECIMAL(10,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  FOREIGN KEY (order_number) REFERENCES orders(order_number),
  FOREIGN KEY (product_number) REFERENCES products(product_number)
);
CREATE TABLE payments (
  payment_ref VARCHAR(12) PRIMARY KEY,
  order_number VARCHAR(12) NOT NULL,
  amount_paid DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  payment_method VARCHAR(30),
  paid_on DATE,
  payment_status VARCHAR(20) NOT NULL,       -- Successful / Refunded / Failed
  FOREIGN KEY (order_number) REFERENCES orders(order_number)
);
CREATE TABLE shipments (
  shipment_ref VARCHAR(12) PRIMARY KEY,
  order_number VARCHAR(12) NOT NULL,
  courier_partner VARCHAR(60),
  tracking_number VARCHAR(30),
  dispatched_on DATE,
  expected_by DATE,
  delivery_status VARCHAR(20) NOT NULL,      -- Preparing / In Transit / Delivered / Not Applicable
  FOREIGN KEY (order_number) REFERENCES orders(order_number)
);
CREATE TABLE returns_reviews (
  reference_number VARCHAR(12) PRIMARY KEY,
  order_number VARCHAR(12) NOT NULL,
  product_number VARCHAR(12) NOT NULL,
  raised_by VARCHAR(12) NOT NULL,
  entry_type VARCHAR(20) NOT NULL,           -- Review / Cancellation / Damage Report / Return
  reason_or_feedback VARCHAR(250),
  rating INT NULL,
  status VARCHAR(20) NOT NULL,
  FOREIGN KEY (order_number) REFERENCES orders(order_number),
  FOREIGN KEY (product_number) REFERENCES products(product_number),
  FOREIGN KEY (raised_by) REFERENCES customers(customer_number)
);

INSERT INTO customers VALUES
('CUST-1001','Ananya Rao','ananya.rao@example.com','+91 98450 11223','Chennai','2023-02-11','Active'),
('CUST-1002','Vikram Sethi','vikram.sethi@example.com','+91 98220 33445','Mumbai','2022-11-05','Active'),
('CUST-1003','Meera Iyer','meera.iyer@example.com','+91 99010 55667','Bengaluru','2021-06-18','Active'),
('CUST-1004','Rohan Das','rohan.das@example.com','+91 97170 77889','Delhi','2024-01-27','Inactive');
INSERT INTO suppliers VALUES
('SUPP-9001','Nova Textiles','Rakesh Menon','rakesh@novatextiles.com','Coimbatore','2019-05-10','Active'),
('SUPP-9002','BrightHome Goods','Priya Nambiar','priya@brighthome.com','Ahmedabad','2020-08-22','Active'),
('SUPP-9003','Everline Electronics','Suresh Kumar','suresh@everline.com','Pune','2018-03-14','Active'),
('SUPP-9004','Urban Kitchenware','Divya Pillai','divya@urbankitchen.com','Hyderabad','2021-11-02','On Hold');
INSERT INTO products VALUES
('PROD-3001','Cotton Bedsheet Set','SUPP-9001','Home & Living',1499,'INR',120,'2022-01-15'),
('PROD-3002','Ceramic Dinner Set','SUPP-9002','Kitchen',2299,'INR',65,'2022-03-02'),
('PROD-3003','Wireless Earbuds','SUPP-9003','Electronics',3499,'INR',200,'2023-06-19'),
('PROD-3004','Non-Stick Pan','SUPP-9004','Kitchen',899,'INR',150,'2023-09-05');
INSERT INTO orders VALUES
('ORD-5001','CUST-1001','2026-07-10','12 Lake View Road, Chennai',1499,'INR','Delivered'),
('ORD-5002','CUST-1002','2026-07-12','45 Marine Drive, Mumbai',5798,'INR','Shipped'),
('ORD-5003','CUST-1003','2026-07-15','78 MG Road, Bengaluru',3499,'INR','Processing'),
('ORD-5004','CUST-1004','2026-07-16','9 Connaught Place, Delhi',899,'INR','Cancelled');
INSERT INTO order_items VALUES
('ITEM-7001','ORD-5001','PROD-3001',1,1499,1499),
('ITEM-7002','ORD-5002','PROD-3002',1,2299,2299),
('ITEM-7003','ORD-5002','PROD-3004',1,3499,3499),
('ITEM-7004','ORD-5003','PROD-3003',1,3499,3499),
('ITEM-7005','ORD-5004','PROD-3004',1,899,899);
INSERT INTO payments VALUES
('PAY-4001','ORD-5001',1499,'INR','UPI','2026-07-10','Successful'),
('PAY-4002','ORD-5002',5798,'INR','Credit Card','2026-07-12','Successful'),
('PAY-4003','ORD-5003',3499,'INR','Net Banking','2026-07-15','Successful'),
('PAY-4004','ORD-5004',899,'INR','UPI','2026-07-16','Refunded');
INSERT INTO shipments VALUES
('SHIP-6001','ORD-5001','Speedex Logistics','SPX88451209','2026-07-10','2026-07-13','Delivered'),
('SHIP-6002','ORD-5002','QuickRoute Couriers','QRC77213890','2026-07-13','2026-07-17','In Transit'),
('SHIP-6003','ORD-5003','Speedex Logistics','SPX88452317','2026-07-16','2026-07-19','Preparing'),
('SHIP-6004','ORD-5004',NULL,NULL,NULL,NULL,'Not Applicable');
INSERT INTO returns_reviews VALUES
('RTRN-8001','ORD-5004','PROD-3004','CUST-1004','Cancellation','Changed my mind before dispatch',NULL,'Refund Issued'),
('RVW-8002','ORD-5001','PROD-3001','CUST-1001','Review','Soft fabric, great quality for the price',5,'Published'),
('RVW-8003','ORD-5003','PROD-3003','CUST-1003','Review','Good sound but battery could be better',4,'Published'),
('RTRN-8004','ORD-5002','PROD-3002','CUST-1002','Damage Report','One plate arrived cracked',NULL,'Under Review');

-- ---------- extra DBMS parts ----------

-- 1. view: sales per product (cancelled orders not counted)
CREATE OR REPLACE VIEW product_sales_summary AS
SELECT p.product_number, p.product_name, s.supplier_name, p.stock_on_hand,
  COALESCE(SUM(CASE WHEN o.order_status <> 'Cancelled' THEN i.quantity END), 0)   AS units_sold,
  COALESCE(SUM(CASE WHEN o.order_status <> 'Cancelled' THEN i.line_total END), 0) AS revenue,
  (SELECT ROUND(AVG(r.rating), 1) FROM returns_reviews r
     WHERE r.product_number = p.product_number AND r.entry_type = 'Review') AS avg_rating
FROM products p
JOIN suppliers s ON p.supplied_by = s.supplier_number
LEFT JOIN order_items i ON i.product_number = p.product_number
LEFT JOIN orders o ON i.order_number = o.order_number
GROUP BY p.product_number, p.product_name, s.supplier_name, p.stock_on_hand;

-- 2. indexes on columns the dashboard counts by
CREATE INDEX idx_order_status    ON orders (order_status);
CREATE INDEX idx_delivery_status ON shipments (delivery_status);
CREATE INDEX idx_stock           ON products (stock_on_hand);

-- 3. stored procedure: all orders of one customer with payment and shipment
--    CALL customer_orders('CUST-1001');
DROP PROCEDURE IF EXISTS customer_orders;
DELIMITER $$
CREATE PROCEDURE customer_orders(IN cust VARCHAR(12))
BEGIN
  SELECT o.order_number, o.order_date, o.order_total, o.order_status,
         y.payment_status, h.delivery_status
  FROM orders o
  LEFT JOIN payments y  ON y.order_number = o.order_number
  LEFT JOIN shipments h ON h.order_number = o.order_number
  WHERE o.placed_by = cust
  ORDER BY o.order_date;
END$$
DELIMITER ;


-- ---------- Shop warehouse additions ----------

-- 4. view: products to reorder (stock below the reorder level of 100, refill up to 150)
CREATE OR REPLACE VIEW reorder_suggestions AS
SELECT p.product_number, p.product_name, p.stock_on_hand,
       100 AS reorder_level,
       150 - p.stock_on_hand AS suggested_order_qty,
       (SELECT COALESCE(SUM(i.quantity), 0) FROM order_items i JOIN orders o ON i.order_number = o.order_number
          WHERE i.product_number = p.product_number AND o.order_status <> 'Cancelled') AS units_sold,
       s.supplier_name, s.contact_email, s.standing AS supplier_standing,
       CASE WHEN s.standing = 'On Hold' THEN 'Find another supplier'
            ELSE 'Reorder from supplier' END AS action
FROM products p
JOIN suppliers s ON p.supplied_by = s.supplier_number
WHERE p.stock_on_hand < 100;

-- 5. stored procedure: invoice lines for one order (NULL = all orders)
--    CALL order_invoice('ORD-5002');
DROP PROCEDURE IF EXISTS order_invoice;
DELIMITER $$
CREATE PROCEDURE order_invoice(IN ord VARCHAR(12))
BEGIN
  SELECT o.order_number, o.order_date, c.full_name AS customer, o.delivery_address,
         i.item_ref, p.product_name, i.quantity, i.price_each, i.line_total,
         o.order_total, y.payment_method, y.payment_status
  FROM orders o
  JOIN customers c   ON o.placed_by = c.customer_number
  JOIN order_items i ON i.order_number = o.order_number
  JOIN products p    ON i.product_number = p.product_number
  LEFT JOIN payments y ON y.order_number = o.order_number
  WHERE ord IS NULL OR o.order_number = ord
  ORDER BY o.order_number, i.item_ref;
END$$
DELIMITER ;
