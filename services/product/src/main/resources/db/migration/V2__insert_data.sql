INSERT INTO category (id, name, description)
VALUES
    (nextval('category_seq'), 'Electronics', 'Devices and gadgets'),
    (nextval('category_seq'), 'Books', 'Educational and fictional books'),
    (nextval('category_seq'), 'Clothing', 'Men and women apparel'),
    (nextval('category_seq'), 'Groceries', 'Daily household groceries'),
    (nextval('category_seq'), 'Furniture', 'Home and office furniture');

INSERT INTO product (id, name, description, available_quantity, price, category_id)
VALUES
    (nextval('product_seq'), 'Smartphone', 'Latest Android phone', 100, 29999.99, 1),
    (nextval('product_seq'), 'Laptop', 'High performance laptop', 50, 75999.50, 1),
    (nextval('product_seq'), 'Novel - Fiction', 'Bestselling novel', 200, 499.00, 51),
    (nextval('product_seq'), 'T-Shirt', 'Cotton round neck T-shirt', 300, 799.00, 101),
    (nextval('product_seq'), 'Office Chair', 'Ergonomic office chair', 40, 5499.99, 201);

INSERT INTO product (id, name, description, available_quantity, price, category_id)
VALUES (nextval('product_seq'), 'Refrigerator', 'Double door fridge', 25, 45999.99, 1);

INSERT INTO product (id, name, description, available_quantity, price, category_id)
VALUES (nextval('product_seq'), 'Textbook - Mathematics', 'University level textbook', 120, 1299.00, 51);

INSERT INTO product (id, name, description, available_quantity, price, category_id)
VALUES (nextval('product_seq'), 'Jeans', 'Denim jeans for men', 180, 1499.00, 101);

INSERT INTO product (id, name, description, available_quantity, price, category_id)
VALUES (nextval('product_seq'), 'Rice - Basmati', 'Premium quality basmati rice 5kg', 250, 699.00, 151);

INSERT INTO product (id, name, description, available_quantity, price, category_id)
VALUES (nextval('product_seq'), 'Wooden Dining Table', '6-seater wooden dining table', 15, 18999.50, 201);
