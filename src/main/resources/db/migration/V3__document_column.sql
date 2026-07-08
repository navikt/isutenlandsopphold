ALTER TABLE VEDTAK
    ADD COLUMN document              JSONB NOT NULL,
    ADD COLUMN journalpost_id        VARCHAR(50),
    ADD COLUMN journalfort_tidspunkt timestamptz;
