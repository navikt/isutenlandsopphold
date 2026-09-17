-- Skiller VEDTAK i BEHANDLING (hva saksbehandler bestemte) og DOKUMENT (hva som ble sendt ut).
--
-- Tabellene døpes om i stedet for å kopieres, slik at interne id-er, uuid-er og
-- fremmednøkler bevares. Dokumentet arver uuid fra det gamle vedtaket, fordi denne verdien
-- brukes som eksternReferanseId mot dokarkiv — deduplisering av allerede journalførte
-- dokument-id-en er avhengig av at den er uendret.
--
-- VEDTAK_PERIODE beholder navnet sitt med vilje: perioder hører kun til utfall som er
-- vedtak, ikke til enhver behandling. Fremmednøkkelen følger automatisk med når VEDTAK
-- døpes om til BEHANDLING, men kolonnen døpes om siden den nå peker på BEHANDLING (id).

ALTER TABLE VEDTAK RENAME TO BEHANDLING;

ALTER TABLE BEHANDLING RENAME COLUMN fattet_av TO behandlet_av;
ALTER TABLE BEHANDLING RENAME COLUMN fattet_tidspunkt TO behandlet_tidspunkt;
ALTER TABLE BEHANDLING RENAME COLUMN vedtak_published_at TO behandling_published_at;
ALTER TABLE VEDTAK_PERIODE RENAME COLUMN vedtak_id TO behandling_id;

ALTER TABLE BEHANDLING RENAME CONSTRAINT check_vedtak_utfall TO check_behandling_utfall;
ALTER TABLE BEHANDLING RENAME CONSTRAINT unique_vedtak_soknad_id TO unique_behandling_soknad_id;

ALTER INDEX idx_vedtak_soknad_id RENAME TO idx_behandling_soknad_id;
ALTER INDEX idx_vedtak_periode_vedtak_id RENAME TO idx_vedtak_periode_behandling_id;

CREATE TABLE DOKUMENT
(
    id                    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID        NOT NULL UNIQUE,
    behandling_id         INT         NOT NULL UNIQUE REFERENCES BEHANDLING (id) ON DELETE CASCADE,
    dokumenttype          VARCHAR(50) NOT NULL,
    innhold               JSONB       NOT NULL,
    journalpost_id        VARCHAR(50),
    journalfort_tidspunkt TIMESTAMPTZ,
    distribuert_tidspunkt TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT check_dokument_dokumenttype CHECK (
        dokumenttype IN ('VEDTAK_INNVILGET', 'VEDTAK_DELVIS_INNVILGET', 'VEDTAK_AVSLAG', 'HENLEGGELSE')
        ),
    -- Journalpost-id og journalføringstidspunkt settes alltid sammen, og distribusjon
    -- kan ikke skje før dokumentet er journalført.
    CONSTRAINT check_dokument_journalfort CHECK ((journalpost_id IS NULL) = (journalfort_tidspunkt IS NULL)),
    CONSTRAINT check_dokument_distribuert CHECK (distribuert_tidspunkt IS NULL OR journalpost_id IS NOT NULL)
);

INSERT INTO DOKUMENT (uuid,
                      behandling_id,
                      dokumenttype,
                      innhold,
                      journalpost_id,
                      journalfort_tidspunkt,
                      distribuert_tidspunkt,
                      created_at)
SELECT behandling.uuid,
       behandling.id,
       CASE behandling.utfall
           WHEN 'INNVILGET' THEN 'VEDTAK_INNVILGET'
           WHEN 'DELVIS_INNVILGET' THEN 'VEDTAK_DELVIS_INNVILGET'
           WHEN 'AVSLAG' THEN 'VEDTAK_AVSLAG'
           WHEN 'HENLAGT' THEN 'HENLEGGELSE'
           END,
       behandling.document,
       behandling.journalpost_id,
       behandling.journalfort_tidspunkt,
       behandling.distribuert_tidspunkt,
       behandling.created_at
FROM BEHANDLING behandling;

DO $$
    DECLARE avvik BIGINT;
    BEGIN
        SELECT count(*) INTO avvik
        FROM BEHANDLING b JOIN DOKUMENT d ON d.behandling_id = b.id
        WHERE b.document              IS DISTINCT FROM d.innhold
           OR b.journalpost_id        IS DISTINCT FROM d.journalpost_id
           OR b.journalfort_tidspunkt IS DISTINCT FROM d.journalfort_tidspunkt
           OR b.distribuert_tidspunkt IS DISTINCT FROM d.distribuert_tidspunkt;
        IF avvik > 0 OR (SELECT count(*) FROM BEHANDLING) <> (SELECT count(*) FROM DOKUMENT) THEN
            RAISE EXCEPTION 'Avbryter: % dokumenter avviker fra behandlingen', avvik;
        END IF;
    END $$;

ALTER TABLE BEHANDLING
    DROP COLUMN document,
    DROP COLUMN journalpost_id,
    DROP COLUMN journalfort_tidspunkt,
    DROP COLUMN distribuert_tidspunkt;
