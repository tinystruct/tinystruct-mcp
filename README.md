# tinystruct mcp

**tinystruct-mcp** is a modular Java MCP server framework based on Tinystruct, providing built-in tools for file system and GitHub operations via the Model Context Protocol (MCP). It's designed for easy integration, automation, and extensibility in modern DevOps and AI-driven workflows.

---

## Quick Start

### 1. Start the MCP Server

#### Java (Programmatic Startup)

```java
import org.tinystruct.ApplicationContext;
import org.tinystruct.application.Context;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.Settings;
import org.tinystruct.system.HttpServer;

public class Main {
    public static void main(String[] args) {
        Context ctx = new ApplicationContext();
        ctx.setAttribute("--server-port", "8080");
        ApplicationManager.init();
        ApplicationManager.install(new org.tinystruct.mcp.GitHub());
        ApplicationManager.install(new org.tinystruct.mcp.FileSystem());
        ApplicationManager.install(new HttpServer());
        ApplicationManager.call("start", ctx);
    }
}
```

#### CLI (Recommended for Most Users)

If you have the `bin/dispatcher` script (from the Tinystruct distribution), you can start the server directly from the command line:

To start the server with the built-in GitHub and FileSystem modules:
```sh
bin/dispatcher start --import org.tinystruct.system.HttpServer --import org.tinystruct.mcp.GitHub --import org.tinystruct.mcp.FileSystem --server-port 777 
```
- You can add or remove imports to customize which modules and tools are loaded.

---

## Built-in Tools

### GitHub Tool
Provides Git and GitHub API operations:
- **Clone repositories**: `github/clone`
- **Pull changes**: `github/pull` 
- **Push changes**: `github/push`
- **Check status**: `github/status`
- **Get issues**: `github/issues`
- **Get pull requests**: `github/prs`
- **Get workflows**: `github/actions`

### FileSystem Tool
Provides file system operations:
- **Get file info**: `filesystem/info`
- **Check existence**: `filesystem/exists`
- **Get file size**: `filesystem/size`
- **List directory**: `filesystem/list`
- **Read file**: `filesystem/read`
- **Write file**: `filesystem/write`
- **Copy file**: `filesystem/copy`
- **Move file**: `filesystem/move`
- **Delete file**: `filesystem/delete`
- **Create directory**: `filesystem/mkdir`

---

## Configuration

Configure the server using a properties file or by setting values in the `Settings` object:

```properties
# Example: mcp.properties
mcp.auth.token=your-secret-token
```

---

## More Advanced Usage

To learn how to develop your own MCP tools and extend the server, see [DEVELOPING_TOOLS.md](DEVELOPING_TOOLS.md).


**MCP stands for Model Context Protocol.**

