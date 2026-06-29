DO $$
BEGIN
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";
    EXCEPTION WHEN undefined_object THEN
    RAISE NOTICE 'role isyfo-analyse does not exist, skipping grants';
END
$$;
