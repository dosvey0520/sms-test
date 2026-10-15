CREATE DATABASE IF NOT EXISTS sms_gateway
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sms_gateway;

CREATE TABLE IF NOT EXISTS sms_queue (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  phone       VARCHAR(20)  NOT NULL,
  message     TEXT         NOT NULL,
  ref         VARCHAR(64)  NULL COMMENT 'your own reference, e.g. invoice/order no.',
  status      ENUM('pending','sending','sent','failed') NOT NULL DEFAULT 'pending',
  attempts    TINYINT UNSIGNED NOT NULL DEFAULT 0,
  error       VARCHAR(255) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  picked_at   DATETIME NULL,
  sent_at     DATETIME NULL,
  KEY idx_status_id (status, id),
  KEY idx_ref (ref)
) ENGINE=InnoDB;

-- Create a DB user that can only touch this table:
-- CREATE USER 'sms_user'@'localhost' IDENTIFIED BY 'CHANGE_ME';
-- GRANT SELECT, INSERT, UPDATE ON sms_gateway.sms_queue TO 'sms_user'@'localhost';
