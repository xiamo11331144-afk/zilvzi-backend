-- VIP persistence and development-only mock payment. Safe to apply repeatedly.
-- DATETIME values are UTC; order timestamps are written with UTC_TIMESTAMP().
CREATE TABLE IF NOT EXISTS vip_membership (
    user_id BIGINT NOT NULL,
    vip_until DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_vip_membership_user FOREIGN KEY (user_id) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vip_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    price_fen INT NOT NULL,
    duration_days INT NOT NULL,
    payment_channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL,
    confirmed_time DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vip_order_no (order_no),
    KEY idx_vip_order_user_created (user_id, create_time),
    CONSTRAINT fk_vip_order_user FOREIGN KEY (user_id) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
