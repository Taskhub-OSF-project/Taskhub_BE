package com.taskhub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseSchemaFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        fixVarcharToTextColumns();
    }

    private void fixVarcharToTextColumns() {
        String[] columnsToFix = {
            "submission_ai_result_json",
            "dispute_ai_report_json",
            "precheck_submitted_file_paths_json"
        };

        for (String columnName : columnsToFix) {
            try {
                alterColumnToText(columnName);
            } catch (Exception e) {
                log.warn("Could not alter column {}: {}", columnName, e.getMessage());
            }
        }
    }

    private void alterColumnToText(String columnName) {
        String checkSql = """
            SELECT data_type FROM information_schema.columns 
            WHERE table_name = 'tasks' AND column_name = ?
            """;

        List<String> types = jdbcTemplate.queryForList(checkSql, String.class, columnName);

        if (types.isEmpty()) {
            log.info("[SCHEMA FIX] Column {} does not exist in tasks table, skipping", columnName);
            return;
        }

        String currentType = types.get(0);
        if ("character varying".equals(currentType) || "varchar".equals(currentType)) {
            String alterSql = "ALTER TABLE tasks ALTER COLUMN " + columnName + " TYPE TEXT USING " + columnName + "::TEXT";
            jdbcTemplate.execute(alterSql);
            log.info("[SCHEMA FIX] Successfully altered column {} from VARCHAR to TEXT", columnName);
        } else {
            log.info("[SCHEMA FIX] Column {} is already type {}, skipping", columnName, currentType);
        }
    }
}
