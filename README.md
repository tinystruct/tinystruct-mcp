# tinystruct MCP project

**tinystruct MCP** is a demonstration project that showcases how to develop [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) tools using the [tinystruct framework](https://www.tinystruct.org). It provides a modular Java-based MCP server with built-in tools for file system management and GitHub operations.

This project is designed to bridge the gap between Large Language Models (LLMs) and external systems, enabling AI agents to interact with the physical file system and version control systems securely and efficiently.

---

## 🌟 Key Features

*   **Modular Architecture**: Built with Maven, allowing for easy expansion and isolation of tool sets.
*   **Annotation-Driven**: Simplify tool development using `@Action` and `@Argument` annotations for automatic schema generation.
*   **Ready-to-Use Tools**: Includes robust implementations for Git, GitHub API, and local FileSystem operations.
*   **Extensible Design**: Inherits the flexibility of the tinystruct framework for building high-performance Java applications.
*   **Standardized Communication**: Uses `org.tinystruct.data.component.Builder` for consistent JSON-based interaction.

---

## 🏗 Project Structure

The project is organized into submodules, each representing a specific domain of tools:

*   **`github/`**: Implements Git operations (clone, pull, push, status) and GitHub API integrations (issues, PRs, actions).
*   **`filesystem/`**: Provides comprehensive local file system management (read, write, copy, move, delete, list).
*   **`bin/`**: Contains scripts for server management and dispatching commands.

---

## 🚀 Getting Started

### Prerequisites

*   Java 17 or higher
*   Maven 3.8+

### 1. Build the Project

Clone the repository and build the modules using Maven:

```sh
git clone https://github.com/tinystruct/tinystruct-mcp.git
cd tinystruct-mcp
mvn clean install
```

### 2. Start the MCP Server

You can start the server programmatically or via the command line.

#### Command Line (CLI)

Use the `bin/dispatcher` (for Linux/macOS) or `bin/dispatcher.cmd` (for Windows) script to start the server:

```sh
# On Linux / macOS
bin/dispatcher start --import org.tinystruct.system.HttpServer --import org.tinystruct.mcp.GitHub --import org.tinystruct.mcp.FileSystem --server-port 777

# On Windows
bin\dispatcher.cmd start --import org.tinystruct.system.HttpServer --import org.tinystruct.mcp.GitHub --import org.tinystruct.mcp.FileSystem --server-port 777
```

#### Programmatic Startup (Java)

Include the following in your `Main` class to boot the server with integrated tools:

```java
import org.tinystruct.ApplicationContext;
import org.tinystruct.application.Context;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.HttpServer;
import org.tinystruct.mcp.GitHub;
import org.tinystruct.mcp.FileSystem;

public class Main {
    public static void main(String[] args) {
        Context ctx = new ApplicationContext();
        ctx.setAttribute("--server-port", "777");
        
        ApplicationManager.init();
        ApplicationManager.install(new GitHub());
        ApplicationManager.install(new FileSystem());
        ApplicationManager.install(new HttpServer());
        
        ApplicationManager.call("start", ctx);
    }
}
```

---

## 🛠 Built-in Tools

### GitHub Tool (`github/`)
| Action | Description |
| :--- | :--- |
| `github/clone` | Clone a Git repository |
| `github/pull` | Pull changes from a remote repository |
| `github/push` | Push changes to a remote repository |
| `github/status` | Get the current status of a local repository |
| `github/issues` | Fetch GitHub issues for a repository |
| `github/prs` | Fetch GitHub pull requests |
| `github/actions` | List GitHub Actions workflows |

### FileSystem Tool (`filesystem/`)
| Action | Description |
| :--- | :--- |
| `filesystem/info` | Get metadata about a file or directory |
| `filesystem/exists` | Check if a path exists |
| `filesystem/read` | Read contents of a file (UTF-8 or Base64) |
| `filesystem/write` | Write content to a file (overwrite or append) |
| `filesystem/list` | List items in a directory |
| `filesystem/copy` | Copy a file or directory |
| `filesystem/move` | Move or rename a file or directory |
| `filesystem/delete` | Delete a file or directory (recursive support) |
| `filesystem/mkdir` | Create a new directory |

---

## 👩‍💻 Developing Your Own Tools

Creating new MCP tools is straightforward. Simply extend the `MCPTool` class and annotate your methods.

```java
@Action(value = "mytool/greet", description = "Greet a user", arguments = {
    @Argument(key = "name", description = "User's name", type = "string")
})
public String greet(String name) {
    return "Hello, " + name + "!";
}
```

For a detailed guide on creating custom tools, check out [**DEVELOPING_TOOLS.md**](DEVELOPING_TOOLS.md).

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

**Note**: *MCP stands for Model Context Protocol.*
