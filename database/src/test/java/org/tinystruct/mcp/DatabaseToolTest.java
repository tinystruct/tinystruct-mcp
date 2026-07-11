package org.tinystruct.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinystruct.data.DatabaseOperator;
import org.tinystruct.data.component.Builder;
import org.tinystruct.data.component.Builders;
import org.tinystruct.ApplicationException;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseToolTest {

    private DatabaseTool tool;

    // Use a unique database name to avoid collisions
    private static final String DB_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    @BeforeEach
    void setUp() throws Exception {
        // We set system properties instead of application.properties to ensure it works isolated in the test phase
        System.setProperty("driver", "org.h2.Driver");
        System.setProperty("database.url", DB_URL);
        System.setProperty("database.user", DB_USER);
        System.setProperty("database.password", DB_PASS);

        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            operator.update("DROP TABLE IF EXISTS test_users");
            operator.update("CREATE TABLE test_users (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL, active BOOLEAN)");
            operator.update("INSERT INTO test_users (username, active) VALUES ('alice', true)");
            operator.update("INSERT INTO test_users (username, active) VALUES ('bob', false)");
        }

        tool = new DatabaseTool();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (DatabaseOperator operator = new DatabaseOperator()) {
            operator.disableSafeCheck();
            operator.update("DROP TABLE IF EXISTS test_users");
        }
    }

    @Test
    void testListTables() throws Exception {
        String result = tool.listTables();
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        Builders tables = (Builders) response.get("tables");
        
        boolean found = false;
        for (int i = 0; i < tables.size(); i++) {
            Builder table = (Builder) tables.get(i);
            if ("TEST_USERS".equalsIgnoreCase(table.get("name").toString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Table test_users should be in the list");
    }

    @Test
    void testDescribe() throws Exception {
        String result = tool.describe("TEST_USERS");
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        Builders columns = (Builders) response.get("columns");
        assertEquals(3, columns.size());
    }

    @Test
    void testQuery() throws Exception {
        String result = tool.query("TEST_USERS");
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals(2, (Integer) response.get("count"));
        Builders rows = (Builders) response.get("rows");
        assertEquals("alice", ((Builder)rows.get(0)).get("USERNAME").toString().toLowerCase());
    }

    @Test
    void testQueryWithWhere() throws Exception {
        String result = tool.query("TEST_USERS", "USERNAME = 'bob'", 10);
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals(1, (Integer) response.get("count"));
        Builders rows = (Builders) response.get("rows");
        assertEquals("bob", ((Builder)rows.get(0)).get("USERNAME").toString().toLowerCase());
    }

    @Test
    void testInsert() throws Exception {
        Builder data = new Builder();
        data.put("USERNAME", "charlie");
        data.put("ACTIVE", true);

        String result = tool.insert("TEST_USERS", data);
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals(1, (Integer) response.get("rows_affected"));

        // Verify insertion
        String queryResult = tool.query("TEST_USERS", "USERNAME = 'charlie'", 10);
        Builder queryResponse = new Builder();
        queryResponse.parse(queryResult);
        assertEquals(1, (Integer) queryResponse.get("count"));
    }

    @Test
    void testUpdate() throws Exception {
        Builder data = new Builder();
        data.put("ACTIVE", true);

        String result = tool.update("TEST_USERS", data, "USERNAME = 'bob'");
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals(1, (Integer) response.get("rows_affected"));
    }

    @Test
    void testDelete() throws Exception {
        String result = tool.delete("TEST_USERS", "USERNAME = 'bob'");
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals(1, (Integer) response.get("rows_affected"));
    }

    @Test
    void testExecuteSelect() throws Exception {
        String result = tool.execute("SELECT COUNT(*) as cnt FROM TEST_USERS");
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals("query", response.get("type"));
        Builders rows = (Builders) response.get("rows");
        assertEquals("2", ((Builder)rows.get(0)).get("CNT").toString());
    }

    @Test
    void testExecuteUpdate() throws Exception {
        String result = tool.execute("UPDATE TEST_USERS SET ACTIVE = true");
        Builder response = new Builder();
        response.parse(result);

        assertTrue((Boolean) response.get("success"));
        assertEquals("update", response.get("type"));
        assertEquals(2, (Integer) response.get("rows_affected"));
    }

    @Test
    void testSecurityValidateWhere() {
        assertThrows(MCPException.class, () -> tool.query("TEST_USERS", "1=1; DROP TABLE TEST_USERS", 10));
        assertThrows(MCPException.class, () -> tool.query("TEST_USERS", "1=1 -- comments", 10));
    }
}
