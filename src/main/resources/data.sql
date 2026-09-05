MERGE INTO customers (id, name, city, email) KEY (id) VALUES
    ('cust-101', 'Ava Sharma', 'Bengaluru', 'ava.sharma@example.com'),
    ('cust-102', 'Liam Chen', 'Singapore', 'liam.chen@example.com'),
    ('cust-103', 'Maya Patel', 'Mumbai', 'maya.patel@example.com');

MERGE INTO products (id, name, category, price, stock) KEY (id) VALUES
    ('prod-201', 'SwiftKart Air Pro Earbuds', 'Audio', 79.99, 42),
    ('prod-202', 'SwiftKart X1 Smartwatch', 'Wearables', 149.50, 8),
    ('prod-203', 'SwiftKart USB-C Fast Charger', 'Accessories', 24.95, 0),
    ('prod-204', 'SwiftKart Travel Backpack', 'Bags', 59.00, 17);

MERGE INTO orders (id, customer_id, product_name, status, expected_delivery_date) KEY (id) VALUES
    ('SK-1001', 'cust-101', 'SwiftKart Air Pro Earbuds', 'Shipped', '2026-09-06'),
    ('SK-1002', 'cust-102', 'SwiftKart X1 Smartwatch', 'Processing', '2026-09-09'),
    ('SK-1003', 'cust-103', 'SwiftKart Travel Backpack', 'Delivered', '2026-09-01');