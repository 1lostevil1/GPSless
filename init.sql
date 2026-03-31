-- =====================================================
-- 1. Создание enum типов
-- =====================================================

-- Тип для network_type (WIFI, CELLULAR, BLUETOOTH)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'network_type') THEN
CREATE TYPE network_type AS ENUM ('WIFI', 'CELLULAR', 'BLUETOOTH');
END IF;
END $$;

-- Тип для status (READY, DONE, TRASHED, ERROR)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'status_type') THEN
CREATE TYPE status_type AS ENUM ('READY', 'DONE', 'TRASHED', 'ERROR');
END IF;
END $$;

-- Тип для role (ROLE_USER, ROLE_ADMIN)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'role') THEN
CREATE TYPE role AS ENUM ('ROLE_USER', 'ROLE_ADMIN');
END IF;
END $$;

-- =====================================================
-- 2. Создание таблиц
-- =====================================================

-- Таблица user (с кавычками, как в Java)
CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role role
    );

-- Таблица refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id BIGSERIAL PRIMARY KEY,
                                              token VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP NOT NULL
    );

-- Таблица network
CREATE TABLE IF NOT EXISTS network (
                                       id BIGSERIAL PRIMARY KEY,
                                       cluster_key VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    geohash VARCHAR(255),
    name VARCHAR(255),
    signal_strength INTEGER,
    type network_type NOT NULL,
    status status_type NOT NULL,
    created_by role NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Таблица cluster
CREATE TABLE IF NOT EXISTS cluster (
                                       id BIGSERIAL PRIMARY KEY,
                                       cluster_key VARCHAR(255),
    name VARCHAR(255),
    type network_type NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius DOUBLE PRECISION,
    geohash VARCHAR(255),
    signal_count INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cluster_geohash_key UNIQUE (geohash, cluster_key)
    );

-- =====================================================
-- 3. Создание индексов
-- =====================================================

-- Индексы для network
CREATE INDEX IF NOT EXISTS idx_network_geohash ON network(geohash);
CREATE INDEX IF NOT EXISTS idx_network_type ON network(type);
CREATE INDEX IF NOT EXISTS idx_network_status ON network(status);

-- Индексы для cluster
CREATE INDEX IF NOT EXISTS idx_cluster_geohash ON cluster(geohash);
CREATE INDEX IF NOT EXISTS idx_cluster_type ON cluster(type);
CREATE INDEX IF NOT EXISTS idx_cluster_cluster_key ON cluster(cluster_key);


-- Индексы для refresh_tokens
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_username ON refresh_tokens(username);