DO $$
BEGIN
CREATE ROLE cloudsqliamuser WITH NOLOGIN;
EXCEPTION WHEN DUPLICATE_OBJECT THEN
  RAISE NOTICE 'not creating role cloudsqliamuser -- it already exists';
END
$$;

DO $$
BEGIN
CREATE ROLE "isyfo-analyse" WITH NOLOGIN;
EXCEPTION WHEN DUPLICATE_OBJECT THEN
  RAISE NOTICE 'not creating role isyfo-analyse -- it already exists';
END
$$;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";
