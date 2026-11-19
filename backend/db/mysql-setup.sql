-- AI Shield: one-time MySQL setup.
-- Run as a MySQL administrator. It creates ONLY a new database `aishield` and a new user
-- `aishield` that can access nothing else. It does not read or change any other database.
--
--   mysql -u root -p < backend\db\mysql-setup.sql
--
-- BEFORE RUNNING: replace CHANGE_ME_STRONG_PASSWORD below, and put the same value in
-- DB_PASSWORD in the project's .env file.

CREATE DATABASE IF NOT EXISTS aishield
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'aishield'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
-- Re-running the script also resets the password (for example if it was first run with the placeholder).
ALTER USER 'aishield'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

-- Least privilege: only this database, only what Flyway and the app need.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
    ON aishield.* TO 'aishield'@'localhost';

FLUSH PRIVILEGES;

-- Tables are created automatically by Flyway when the backend starts.
