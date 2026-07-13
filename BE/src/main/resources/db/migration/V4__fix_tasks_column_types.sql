-- Fix precheck_submitted_file_paths_json column type (may have been created as VARCHAR(255) instead of TEXT)
DO $$
BEGIN
    -- Check if column exists and is not TEXT type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tasks'
        AND column_name = 'precheck_submitted_file_paths_json'
        AND data_type = 'character varying'
    ) THEN
        ALTER TABLE tasks ALTER COLUMN precheck_submitted_file_paths_json TYPE TEXT USING precheck_submitted_file_paths_json::TEXT;
        RAISE NOTICE 'Fixed precheck_submitted_file_paths_json column type to TEXT';
    ELSE
        RAISE NOTICE 'precheck_submitted_file_paths_json already has correct type or does not exist';
    END IF;
END $$;
