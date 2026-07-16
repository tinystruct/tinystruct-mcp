package org.tinystruct.mcp.websearch;

import org.tinystruct.mcp.MCPServer;
import org.tinystruct.mcp.MCPTool;

import java.util.logging.Logger;

/**
 * WebSearch MCP Server
 */
public class WebSearch extends MCPServer {
    private static final Logger LOGGER = Logger.getLogger(WebSearch.class.getName());
    private static final String WEBSEARCH_PROTOCOL_VERSION = "1.0.0";

    @Override
    public void init() {
        super.init();

        // Register WebSearch tool methods
        WebSearchTool websearchTool = new WebSearchTool();
        this.registerTool(websearchTool);
    }

    @Override
    public String version() {
        return WEBSEARCH_PROTOCOL_VERSION;
    }
}
