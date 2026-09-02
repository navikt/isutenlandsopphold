ALTER TABLE VEDTAK
    RENAME TO BEHANDLINGSUTFALL;

-- "utfall" gjenbrukes som diskriminator for både Vedtak sitt utfall
-- (INNVILGET/DELVIS_INNVILGET/AVSLAG) og for HENLEGGELSE, og kalles derfor
-- om til det mer generelle "type".
ALTER TABLE BEHANDLINGSUTFALL
    RENAME COLUMN utfall TO type;

ALTER TABLE BEHANDLINGSUTFALL
    DROP CONSTRAINT check_vedtak_utfall;

ALTER TABLE BEHANDLINGSUTFALL
    ADD CONSTRAINT check_behandlingsutfall_type CHECK (type IN ('INNVILGET', 'DELVIS_INNVILGET', 'AVSLAG', 'HENLEGGELSE'));
