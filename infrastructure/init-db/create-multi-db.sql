-- Create databases
CREATE DATABASE auth_service;
CREATE DATABASE user_service;
CREATE DATABASE transaction_service;

-- Connect to each DB and create users
\connect auth_service
CREATE USER auth_user WITH ENCRYPTED PASSWORD 'auth_pass';
GRANT ALL PRIVILEGES ON DATABASE auth_service TO auth_user;

\connect user_service
CREATE USER user_user WITH ENCRYPTED PASSWORD 'user_pass';
GRANT ALL PRIVILEGES ON DATABASE user_service TO user_user;

\connect transaction_service
CREATE USER txn_user WITH ENCRYPTED PASSWORD 'txn_pass';
GRANT ALL PRIVILEGES ON DATABASE transaction_service TO txn_user;
