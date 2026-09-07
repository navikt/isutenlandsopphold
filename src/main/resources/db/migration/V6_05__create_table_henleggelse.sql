CREATE TABLE HENLEGGELSE
(
    id                       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID        NOT NULL UNIQUE,
    soknad_id                INT         NOT NULL UNIQUE REFERENCES SOKNAD (id) ON DELETE CASCADE,
    begrunnelse              TEXT        NOT NULL,
    henlagt_av               VARCHAR(30) NOT NULL,
    henlagt_tidspunkt        TIMESTAMPTZ NOT NULL,
    document                 JSONB       NOT NULL,
    journalpost_id           VARCHAR(50),
    journalfort_tidspunkt    TIMESTAMPTZ,
    distribuert_tidspunkt    TIMESTAMPTZ,
    henleggelse_published_at TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_henleggelse_soknad_id ON HENLEGGELSE (soknad_id);
