-- Legger til utfallet IKKE_AKTUELL for søknader som ikke skal realitetsbehandles her.
--
-- Grunnen ligger som kolonne på BEHANDLING framfor i egen tabell: det er én verdi som
-- settes én gang og aldri endres, uten egen livssyklus slik BREV har.
--
-- En behandling merket ikke aktuell sender ikke brev, og får derfor ingen rad i BREV.
-- Cronjobbene for journalføring og distribusjon bruker INNER JOIN mot BREV, så slike
-- behandlinger faller automatisk ut av dem.

ALTER TABLE BEHANDLING
    DROP CONSTRAINT check_behandling_utfall;

ALTER TABLE BEHANDLING
    ADD CONSTRAINT check_behandling_utfall
        CHECK (utfall IN ('INNVILGET', 'DELVIS_INNVILGET', 'AVSLAG', 'HENLAGT', 'IKKE_AKTUELL'));

ALTER TABLE BEHANDLING
    ADD COLUMN ikke_aktuell_grunn VARCHAR(50);

ALTER TABLE BEHANDLING
    ADD CONSTRAINT check_behandling_ikke_aktuell_grunn
        CHECK ((utfall = 'IKKE_AKTUELL') = (ikke_aktuell_grunn IS NOT NULL));

ALTER TABLE BEHANDLING
    ADD CONSTRAINT check_behandling_ikke_aktuell_grunn_verdi
        CHECK (ikke_aktuell_grunn IS NULL OR
               ikke_aktuell_grunn IN ('BEHANDLET_I_INFOTRYGD', 'DUPLIKAT', 'ANNET'));
