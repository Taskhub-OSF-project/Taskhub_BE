-- Fix TEXT columns that may have been created as VARCHAR(255) due to NVARCHAR mapping
DO $$
BEGIN
    -- Fix precheck_submitted_file_paths_json
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

    -- Fix submission_ai_result_json
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tasks'
        AND column_name = 'submission_ai_result_json'
        AND data_type = 'character varying'
    ) THEN
        ALTER TABLE tasks ALTER COLUMN submission_ai_result_json TYPE TEXT USING submission_ai_result_json::TEXT;
        RAISE NOTICE 'Fixed submission_ai_result_json column type to TEXT';
    ELSE
        RAISE NOTICE 'submission_ai_result_json already has correct type or does not exist';
    END IF;

    -- Fix dispute_ai_report_json
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tasks'
        AND column_name = 'dispute_ai_report_json'
        AND data_type = 'character varying'
    ) THEN
        ALTER TABLE tasks ALTER COLUMN dispute_ai_report_json TYPE TEXT USING dispute_ai_report_json::TEXT;
        RAISE NOTICE 'Fixed dispute_ai_report_json column type to TEXT';
    ELSE
        RAISE NOTICE 'dispute_ai_report_json already has correct type or does not exist';
    END IF;
END $$;
