-- V5: Drop citext for users.email; switch to plain varchar(254) with app-level
-- lowercase normalization. Hibernate's strict schema validator reports a type
-- mismatch (expects VARCHAR, gets OTHER for citext) and the cleaner path is
-- to remove the vendor dependency rather than annotate around it.
--
-- 254 = max email length per RFC 5321 (320 total, 64 local + @ + 255 domain),
-- but practical limit is 254. Anything longer is invalid email anyway.

ALTER TABLE users
    ALTER COLUMN email TYPE varchar(254) USING email::varchar(254);

-- citext extension stays installed in case other tables adopt it later; the
-- unused extension costs nothing.
