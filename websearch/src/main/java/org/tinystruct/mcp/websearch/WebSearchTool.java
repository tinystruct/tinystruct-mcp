package org.tinystruct.mcp.websearch;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.mcp.MCPClient;
import org.tinystruct.mcp.MCPException;
import org.tinystruct.mcp.MCPTool;
import org.tinystruct.net.URLRequest;
import org.tinystruct.net.handlers.HTTPHandler;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSearch tool using DuckDuckGo Instant Answer API (JSON)
 */
public class WebSearchTool extends MCPTool {
    private static final Logger LOGGER = Logger.getLogger(WebSearchTool.class.getName());

    public WebSearchTool() {
        super("websearch", "Tool for performing web searches", null, null, true);
    }

    public WebSearchTool(MCPClient client) {
        super("websearch", "Tool for performing web searches", null, client, true);
    }

    @Action(value = "websearch/search", description = "Perform a web search using DuckDuckGo Instant Answer API", arguments = {
            @Argument(key = "query", description = "The search query", type = "string")
    })
    public Builder search(String query) throws MCPException {
        try {
            return searchInternal(query);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error performing web search: " + e.getMessage(), e);
            throw new MCPException("Error performing web search: " + e.getMessage());
        }
    }

    private Builder searchInternal(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String apiUrl = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json";

        URL url = URI.create(apiUrl).toURL();
        URLRequest request = new URLRequest(url);
        request.setMethod("GET");

        HTTPHandler handler = new HTTPHandler();
        final StringBuilder content = new StringBuilder();

        try {
            handler.handleRequest(request, chunk -> {
                content.append(chunk);
            });
        } catch (ApplicationException e) {
            throw new MCPException("HTTP request failed: " + e.getMessage());
        }

        Builder json = new Builder();
        json.parse(content.toString());

        Builder result = new Builder();
        result.put("status", "success");
        result.put("query", query);
        result.put("results", json);

        LOGGER.info("Successfully fetched search results for query: " + query);
        return result;
    }
}
