package org.tinystruct.mcp;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.DatabaseOperator;
import org.tinystruct.data.component.Builder;
import org.tinystruct.data.component.Builders;
import org.tinystruct.data.repository.Type;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseTool extends MCPTool {

    private static final Logger logger = Logger.getLogger(DatabaseTool.class.getName());

    /** Hard cap on rows returned by db/query. */
    private static final int MAX_ROWS = 1000;

    public DatabaseTool() {
        super("database", "A set of tools for interacting with a relational database via SQL.");
    }

    private static Type detectDialect(DatabaseMetaData meta) {
        try {
            String name = meta.getDatabaseProductName().toUpperCase(Locale.ROOT);
            if (name.contains("MYSQL") || name.contains("MARIADB")) return Type.MySQL;
            if (name.contains("SQLITE"))                              return Type.SQLite;
            if (name.contains("H2"))                                  return Type.H2;
            if (name.contains("MICROSOFT") || name.contains("SQL SERVER")) return Type.SQLServer;
        } catch (SQLException ignored) { /* fall through to null */ }
        return null;
    }

    private static String quoteChar(DatabaseMetaData meta, Type dialect) {
        try {
            String q = meta.getIdentifierQuoteString();
            if (q != null && !q.trim().isEmpty()) return q;
        } catch (SQLException ignored) {}
        return dialect == Type.SQLServer ? "\"" : "`";
    }

    private static String quoteIdentifier(String safe, String q) {
        return q + safe + q;
    }

    @Action(
            value = "db/list-tables",
            description = "List all tables in the connected database."
    )
    public String listTables() throws MCPException {
        Builder result = new Builder();
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            DatabaseMetaData meta = operator.getMetaData();
            Type dialect = detectDialect(meta);

            String catalog = operator.getCatalog();
            String schema  = dialect == Type.SQLite ? null : operator.getSchema();

            Builders tables = new Builders();
            try (ResultSet rs = meta.getTables(catalog, schema, "%",
                    new String[]{"TABLE", "VIEW", "SYSTEM TABLE"})) {
                while (rs.next()) {
                    String tableType = rs.getString("TABLE_TYPE");
                    if ("SYSTEM TABLE".equalsIgnoreCase(tableType)) continue;

                    Builder table = new Builder();
                    table.put("name", rs.getString("TABLE_NAME"));
                    table.put("type", tableType);
                    String remarks = rs.getString("REMARKS");
                    if (remarks != null && !remarks.isEmpty()) {
                        table.put("remarks", remarks);
                    }
                    tables.add(table);
                }
            }

            result.put("success", true);
            result.put("tables", tables);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "list-tables failed", e);
            throw new MCPException("list-tables failed: " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Action(
            value = "db/describe",
            description = "Describe the columns and types of a specific table.",
            arguments = {
                    @Argument(key = "table", description = "The table name to describe", type = "string")
            }
    )
    public String describe(String table) throws MCPException {
        if (table == null || table.trim().isEmpty()) {
            throw new MCPException("Missing 'table' parameter.");
        }
        String safeTable = sanitizeIdentifier(table);

        Builder result = new Builder();
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            DatabaseMetaData meta = operator.getMetaData();
            Type dialect = detectDialect(meta);

            String catalog = operator.getCatalog();
            String schema  = dialect == Type.SQLite ? null : operator.getSchema();

            Set<String> pkColumns = new HashSet<>();
            try (ResultSet pkRs = meta.getPrimaryKeys(catalog, schema, safeTable)) {
                while (pkRs.next()) {
                    pkColumns.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            Builders columns = new Builders();
            try (ResultSet rs = meta.getColumns(catalog, schema, safeTable, "%")) {
                while (rs.next()) {
                    Builder col = new Builder();
                    String colName = rs.getString("COLUMN_NAME");
                    col.put("name", colName);
                    col.put("type", rs.getString("TYPE_NAME"));
                    int size = rs.getInt("COLUMN_SIZE");
                    col.put("size", rs.wasNull() ? 0 : size);
                    col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    col.put("primary_key", pkColumns.contains(colName));
                    String def = rs.getString("COLUMN_DEF");
                    col.put("default", def != null ? def : "");
                    String autoInc = rs.getString("IS_AUTOINCREMENT");
                    col.put("auto_increment", "YES".equalsIgnoreCase(autoInc));
                    columns.add(col);
                }
            }

            if (columns.isEmpty()) {
                throw new MCPException("Table not found or has no columns: " + safeTable);
            }

            result.put("success", true);
            result.put("table", safeTable);
            result.put("columns", columns);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "describe failed for table: " + safeTable, e);
            throw new MCPException("describe failed for table '" + safeTable + "': " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Action(
            value = "db/query",
            description = "SELECT rows from a table without WHERE or LIMIT.",
            arguments = {
                    @Argument(key = "table", description = "The table name to query", type = "string")
            }
    )
    public String query(String table) throws MCPException {
        return query(table, null, 100);
    }

    @Action(
            value = "db/query",
            description = "SELECT rows from a table with optional WHERE and LIMIT.",
            arguments = {
                    @Argument(key = "table", description = "The table name to query", type = "string"),
                    @Argument(key = "where", description = "Optional SQL WHERE clause (e.g. id=1)", type = "string"),
                    @Argument(key = "limit", description = "Max number of rows to return (1-1000, default 100)", type = "number")
            }
    )
    public String query(String table, String where, Integer limit) throws MCPException {
        if (table == null || table.trim().isEmpty()) {
            throw new MCPException("Missing 'table' parameter.");
        }
        String safeTable = sanitizeIdentifier(table);
        if (limit == null || limit <= 0 || limit > MAX_ROWS) limit = 100;

        if (where != null && !where.trim().isEmpty()) {
            validateWhereClause(where);
        }

        Builder result = new Builder();
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            DatabaseMetaData meta = operator.getMetaData();
            Type dialect = detectDialect(meta);
            String q = quoteChar(meta, dialect);
            String quotedTable = quoteIdentifier(safeTable, q);

            String sql = buildSelectSql(quotedTable, where, limit, dialect);

            ResultSet rs = operator.query(sql);
            Builders rows;
            try {
                rows = resultSetToBuilders(rs);
            } finally {
                rs.close();
            }

            result.put("success", true);
            result.put("table", safeTable);
            result.put("sql", sql);
            result.put("count", rows.size());
            result.put("rows", rows);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "query failed on: " + safeTable, e);
            throw new MCPException("query failed on '" + safeTable + "': " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Action(
            value = "db/insert",
            description = "INSERT a row into a table.",
            arguments = {
                    @Argument(key = "table", description = "The table name", type = "string"),
                    @Argument(key = "data", description = "JSON object with column→value pairs", type = "object")
            }
    )
    public String insert(String table, Builder data) throws MCPException {
        if (table == null || table.trim().isEmpty()) {
            throw new MCPException("Missing 'table' parameter.");
        }
        if (data == null || data.isEmpty()) {
            throw new MCPException("Missing 'data' parameter or it's empty.");
        }
        String safeTable = sanitizeIdentifier(table);

        Builder result = new Builder();
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            DatabaseMetaData meta = operator.getMetaData();
            String q = quoteChar(meta, detectDialect(meta));

            List<String> cols = new ArrayList<>(data.keySet());
            StringBuilder sql = new StringBuilder("INSERT INTO ")
                    .append(quoteIdentifier(safeTable, q)).append(" (");
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < cols.size(); i++) {
                sql.append(quoteIdentifier(sanitizeIdentifier(cols.get(i)), q));
                placeholders.append("?");
                if (i < cols.size() - 1) { sql.append(", "); placeholders.append(", "); }
            }
            sql.append(") VALUES (").append(placeholders).append(")");

            Object[] params = cols.stream().map(data::get).toArray();
            PreparedStatement ps = operator.preparedStatement(sql.toString(), params);
            int affected = operator.executeUpdate(ps);

            result.put("success", true);
            result.put("table", safeTable);
            result.put("rows_affected", affected);
            result.put("sql", sql.toString());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "insert failed on: " + safeTable, e);
            throw new MCPException("insert failed on '" + safeTable + "': " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Action(
            value = "db/update",
            description = "UPDATE rows in a table matching a WHERE clause.",
            arguments = {
                    @Argument(key = "table", description = "The table name", type = "string"),
                    @Argument(key = "data", description = "JSON object with column→value pairs to set", type = "object"),
                    @Argument(key = "where", description = "SQL WHERE clause (required for safety)", type = "string")
            }
    )
    public String update(String table, Builder data, String where) throws MCPException {
        if (table == null || table.trim().isEmpty()) {
            throw new MCPException("Missing 'table' parameter.");
        }
        if (data == null || data.keySet().isEmpty()) {
            throw new MCPException("Missing 'data' parameter or it's empty.");
        }
        if (where == null || where.trim().isEmpty()) {
            throw new MCPException("Missing 'where' parameter. A WHERE clause is required for safety.");
        }
        validateWhereClause(where);

        String safeTable = sanitizeIdentifier(table);

        Builder result = new Builder();
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            DatabaseMetaData meta = operator.getMetaData();
            String q = quoteChar(meta, detectDialect(meta));

            List<String> cols = new ArrayList<>(data.keySet());
            StringBuilder sql = new StringBuilder("UPDATE ")
                    .append(quoteIdentifier(safeTable, q)).append(" SET ");
            for (int i = 0; i < cols.size(); i++) {
                sql.append(quoteIdentifier(sanitizeIdentifier(cols.get(i)), q)).append(" = ?");
                if (i < cols.size() - 1) sql.append(", ");
            }
            sql.append(" WHERE ").append(where);

            Object[] params = cols.stream().map(data::get).toArray();
            PreparedStatement ps = operator.preparedStatement(sql.toString(), params);
            int affected = operator.executeUpdate(ps);

            result.put("success", true);
            result.put("table", safeTable);
            result.put("rows_affected", affected);
            result.put("sql", sql.toString());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "update failed on: " + safeTable, e);
            throw new MCPException("update failed on '" + safeTable + "': " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Action(
            value = "db/delete",
            description = "DELETE rows from a table matching a WHERE clause.",
            arguments = {
                    @Argument(key = "table", description = "The table name", type = "string"),
                    @Argument(key = "where", description = "SQL WHERE clause (required for safety)", type = "string")
            }
    )
    public String delete(String table, String where) throws MCPException {
        if (table == null || table.trim().isEmpty()) {
            throw new MCPException("Missing 'table' parameter.");
        }
        if (where == null || where.trim().isEmpty()) {
            throw new MCPException("Missing 'where' parameter. A WHERE clause is required for safety.");
        }
        validateWhereClause(where);

        String safeTable = sanitizeIdentifier(table);

        Builder result = new Builder();
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            DatabaseMetaData meta = operator.getMetaData();
            String q = quoteChar(meta, detectDialect(meta));

            String sql = "DELETE FROM " + quoteIdentifier(safeTable, q) + " WHERE " + where;
            int affected = operator.update(sql);

            result.put("success", true);
            result.put("table", safeTable);
            result.put("rows_affected", affected);
            result.put("sql", sql);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "delete failed on: " + safeTable, e);
            throw new MCPException("delete failed on '" + safeTable + "': " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Action(
            value = "db/execute",
            description = "Execute arbitrary SQL. Returns rows for SELECT/SHOW/DESCRIBE/EXPLAIN/WITH/VALUES, affected rows otherwise.",
            arguments = {
                    @Argument(key = "sql", description = "The raw SQL statement to execute", type = "string")
            }
    )
    public String execute(String sql) throws MCPException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new MCPException("Missing 'sql' parameter.");
        }
        sql = sql.trim();
        Builder result = new Builder();

        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();

            String upper = sql.toUpperCase(Locale.ROOT);
            if (upper.startsWith("SELECT") || upper.startsWith("WITH") ||
                upper.startsWith("SHOW") || upper.startsWith("DESCRIBE") ||
                upper.startsWith("EXPLAIN") || upper.startsWith("VALUES") ||
                upper.startsWith("PRAGMA") || upper.startsWith("HELP")) {

                ResultSet rs = operator.query(sql);
                Builders rows;
                try {
                    rows = resultSetToBuilders(rs);
                } finally {
                    rs.close();
                }
                result.put("success", true);
                result.put("type", "query");
                result.put("count", rows.size());
                result.put("rows", rows);
            } else {
                int affected = operator.update(sql);
                result.put("success", true);
                result.put("type", "update");
                result.put("rows_affected", affected);
            }
            result.put("sql", sql);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "execute failed", e);
            throw new MCPException("execute failed: " + e.getMessage(), e);
        }
        return result.toString();
    }

    @Override
    protected Object executeLocally(Builder builder) throws MCPException {
        throw new MCPException("Use individual @Action-annotated methods via MCPServer.registerTool().");
    }

    private static String buildSelectSql(String quotedTable, String where,
                                         int limit, Type dialect) {
        StringBuilder sql = new StringBuilder();
        if (dialect == Type.SQLServer) {
            sql.append("SELECT TOP ").append(limit).append(" * FROM ").append(quotedTable);
        } else {
            sql.append("SELECT * FROM ").append(quotedTable);
        }
        if (where != null && !where.trim().isEmpty()) {
            sql.append(" WHERE ").append(where);
        }
        if (dialect != Type.SQLServer) {
            sql.append(" LIMIT ").append(limit);
        }
        return sql.toString();
    }

    private static Builders resultSetToBuilders(ResultSet rs) throws SQLException {
        Builders rows = new Builders();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        while (rs.next()) {
            Builder row = new Builder();
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                row.put(meta.getColumnLabel(i), val != null ? val.toString() : null);
            }
            rows.add(row);
        }
        return rows;
    }

    private static String sanitizeIdentifier(String name) throws MCPException {
        if (name == null || name.trim().isEmpty()) {
            throw new MCPException("SQL identifier must not be null or blank.");
        }
        String safe = name.replaceAll("[^a-zA-Z0-9_]", "");
        if (safe.isEmpty()) {
            throw new MCPException("SQL identifier '" + name + "' contains no valid characters.");
        }
        return safe;
    }

    private static void validateWhereClause(String where) throws MCPException {
        if (where == null) return;
        if (where.contains(";")) {
            throw new MCPException("WHERE clause must not contain a statement terminator (;).");
        }
        String upper = where.toUpperCase(Locale.ROOT);
        if (upper.contains("--") || upper.contains("/*") || upper.contains("*/")) {
            throw new MCPException("WHERE clause must not contain SQL comment sequences.");
        }
    }
}
