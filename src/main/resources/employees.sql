-- Create a new database
CREATE DATABASE sample_db;

-- Connect to the database
\c sample_db;

-- Create a table named 'employees'
CREATE TABLE employees (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    hire_date DATE
);

-- Insert sample data into the 'employees' table
INSERT INTO employees (first_name, last_name, email, hire_date) VALUES
('John', 'Doe', 'john.doe@example.com', '2021-01-15'),
('Jane', 'Smith', 'jane.smith@example.com', '2020-02-20'),
('Alice', 'Johnson', 'alice.johnson@example.com', '2019-03-25'),
('Bob', 'Williams', 'bob.williams@example.com', '2018-04-30');

-- Select all records from the 'employees' table
SELECT * FROM employees;
