-- Døper om ikke_aktuell_grunn til ikke_aktuell_arsak for å bruke samme ord som resten
-- av domenet. Bare navnet endres: verdiene er de samme enumnavnene som før.

ALTER TABLE BEHANDLING
    RENAME COLUMN ikke_aktuell_grunn TO ikke_aktuell_arsak;

ALTER TABLE BEHANDLING
    RENAME CONSTRAINT check_behandling_ikke_aktuell_grunn TO check_behandling_ikke_aktuell_arsak;

ALTER TABLE BEHANDLING
    RENAME CONSTRAINT check_behandling_ikke_aktuell_grunn_verdi TO check_behandling_ikke_aktuell_arsak_verdi;
