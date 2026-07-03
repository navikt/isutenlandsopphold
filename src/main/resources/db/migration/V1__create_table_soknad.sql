CREATE TABLE SOKNAD
(
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID        NOT NULL UNIQUE,
    ekstern_id         UUID        NOT NULL UNIQUE,
    personident        VARCHAR(11) NOT NULL,
    innsendt_tidspunkt TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_soknad_personident ON SOKNAD (personident);

CREATE TABLE SOKNAD_PERIODE
(
    id        BIGSERIAL PRIMARY KEY,
    soknad_id BIGINT NOT NULL REFERENCES SOKNAD (id) ON DELETE CASCADE,
    fom       DATE   NOT NULL,
    tom       DATE   NOT NULL,
    CONSTRAINT check_soknad_periode_fom_tom CHECK (fom <= tom)
);
CREATE INDEX idx_soknad_periode_soknad_id ON SOKNAD_PERIODE (soknad_id);

CREATE TYPE VEDTAK_UTFALL AS ENUM ('INNVILGET');

CREATE TABLE VEDTAK
(
    id         BIGSERIAL PRIMARY KEY,
    uuid       UUID          NOT NULL UNIQUE,
    soknad_id  BIGINT        NOT NULL REFERENCES SOKNAD (id) ON DELETE CASCADE,
    utfall     VEDTAK_UTFALL NOT NULL,
    fattet_av  VARCHAR(30)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_vedtak_soknad_id ON VEDTAK (soknad_id);

CREATE TABLE VEDTAK_PERIODE
(
    id        BIGSERIAL PRIMARY KEY,
    vedtak_id BIGINT NOT NULL REFERENCES VEDTAK (id) ON DELETE CASCADE,
    fom       DATE   NOT NULL,
    tom       DATE   NOT NULL,
    CONSTRAINT check_vedtak_periode_fom_tom CHECK (fom <= tom)
);
CREATE INDEX idx_vedtak_periode_vedtak_id ON VEDTAK_PERIODE (vedtak_id);
