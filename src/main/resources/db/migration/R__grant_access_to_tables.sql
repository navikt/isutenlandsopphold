-- Repeatable migrasjon: kjøres på nytt hver gang innholdet (checksummen) endres.
-- Endre denne fila når en ny tabell legges til, slik at rettighetene også dekker den.
-- Gjeldende tabeller: soknad, soknad_periode, behandling, vedtak_periode, brev.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";
