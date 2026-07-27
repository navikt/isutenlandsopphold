DO $$
BEGIN
CREATE ROLE cloudsqliamuser WITH NOLOGIN;
EXCEPTION WHEN DUPLICATE_OBJECT THEN
  RAISE NOTICE 'not creating role cloudsqliamuser -- it already exists';
END
$$;

DO $$
BEGIN
CREATE ROLE "isyfo-analyse" WITH LOGIN;
EXCEPTION WHEN DUPLICATE_OBJECT THEN
  RAISE NOTICE 'not creating role isyfo-analyse -- it already exists';
END
$$;

ALTER ROLE "isyfo-analyse" WITH LOGIN;
