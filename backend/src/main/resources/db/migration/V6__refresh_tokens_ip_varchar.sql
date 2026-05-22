-- V6: Same family of fix as V5 — drop the vendor-specific Postgres type
-- (inet) on refresh_tokens.ip in favour of varchar. Hibernate binds Strings
-- as VARCHAR, and Postgres refuses to implicitly cast varchar → inet.
--
-- varchar(45) accommodates IPv4 (15 chars max) and IPv6 (45 chars max,
-- including zone identifier). We don't need range queries or inet operators
-- here; we just store the client IP for audit on each refresh_tokens row.

ALTER TABLE refresh_tokens
    ALTER COLUMN ip TYPE varchar(45) USING ip::varchar(45);
