package org.tinystruct.mcp;

import org.tinystruct.data.component.Builder;

/**
 * Database MCP Server
 * Provides tools for inspecting and querying relational databases.
 */
public class Database extends MCPServer {
    private static final String DATABASE_PROTOCOL_VERSION = "1.0.0";

    @Override
    public void init() {
        super.init();

        // Register Database tool methods
        DatabaseTool databaseTool = new DatabaseTool();
        this.registerTool(databaseTool);

        // Register sql-query-helper prompt
        Builder promptSchema = new Builder();
        Builder properties = new Builder();

        Builder intentParam = new Builder();
        intentParam.put("type", "string");
        intentParam.put("description", "What the user wants to accomplish");

        properties.put("intent", intentParam);
        promptSchema.put("type", "object");
        promptSchema.put("properties", properties);
        promptSchema.put("required", new String[]{"intent"});

        MCPPrompt sqlHelperPrompt = new MCPPrompt(
            "sql-query-helper",
            "Generate a SQL query based on the user's intent",
            "Generate a valid SQL query for the user's intent: {{intent}}\n\nBefore returning the query, use the db/info tool to determine the database dialect, and use db/list-tables and db/describe to ensure the tables and columns exist.",
            promptSchema,
            null
        ) {
            @Override
            protected boolean supportsLocalExecution() {
                return true;
            }
        };

        this.registerPrompt(sqlHelperPrompt);
    }

    @Override
    public String version() {
        return DATABASE_PROTOCOL_VERSION;
    }
}
