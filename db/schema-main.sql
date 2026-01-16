-- ============================
-- Coffee Monitor Service (MySQL)
-- DB + utente dedicato + 2 tabelle
-- ============================

-- 1) Database
CREATE DATABASE IF NOT EXISTS coffee_monitor
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 2) Utente dedicato (NO root)
CREATE USER IF NOT EXISTS 'monitor_user'@'localhost' IDENTIFIED BY 'MonitorPass123!';

-- 3) Permessi SOLO su coffee_monitor
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
      ON coffee_monitor.* TO 'monitor_user'@'localhost';

FLUSH PRIVILEGES;

USE coffee_monitor;

-- 4) Tabella distributori (solo location_name + status)
CREATE TABLE IF NOT EXISTS monitor_distributors (
                                                    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    code          VARCHAR(50)  NOT NULL UNIQUE,
    location_name VARCHAR(120) NULL,
    status        ENUM('ACTIVE','MAINTENANCE','FAULT') NOT NULL DEFAULT 'ACTIVE',
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
    );

-- 5) Tabella heartbeat (ultimo segnale visto)
CREATE TABLE IF NOT EXISTS distributor_heartbeats (
                                                      distributor_code VARCHAR(50) PRIMARY KEY,
    last_seen        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hb_dist
    FOREIGN KEY (distributor_code) REFERENCES monitor_distributors(code)
    ON DELETE CASCADE
    );


CREATE INDEX idx_hb_last_seen ON distributor_heartbeats(last_seen);