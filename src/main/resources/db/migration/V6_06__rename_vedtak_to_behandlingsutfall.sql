ALTER TABLE VEDTAK
    RENAME TO BEHANDLINGSUTFALL;

ALTER TABLE BEHANDLINGSUTFALL
    RENAME COLUMN vedtak_published_at TO behandlingsutfall_published_at;

ALTER TABLE BEHANDLINGSUTFALL
    RENAME CONSTRAINT check_vedtak_utfall TO check_behandlingsutfall_utfall;

ALTER TABLE BEHANDLINGSUTFALL
    RENAME CONSTRAINT unique_vedtak_soknad_id TO unique_behandlingsutfall_soknad_id;

ALTER INDEX idx_vedtak_soknad_id RENAME TO idx_behandlingsutfall_soknad_id;

ALTER TABLE VEDTAK_PERIODE
    RENAME COLUMN vedtak_id TO behandlingsutfall_id;
