# GitHub Plugin for MCP based on tinystruct framework

## Overview

The GitHub Plugin is a tinystruct-based extension that provides seamless integration with Git and GitHub services. Built on the Machine Communication Protocol (MCP) framework, this plugin enables applications to perform Git operations and interact with GitHub APIs through a standardized JSON-RPC interface.

## Features

- **Git Operations**
  - Clone repositories to local storage
  - Pull updates from remote repositories
  - Push local changes to remote repositories
  - Check repository status with detailed file tracking

- **GitHub Integration**
  - Fetch and manage repository issues
  - List and interact with pull requests
  - Access GitHub Actions workflows

## Installation

### Prerequisites

- Java 8 or higher
- tinystruct framework
- Maven

### Adding to Your Project

1. Clone the repository:
   ```bash
   git clone https://github.com/tinystruct/github-plugin.git
   ```

2. Install with Maven:
   ```bash
   cd github-plugin
   mvn clean install
   ```

3. Add the dependency to your project's `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.tinystruct</groupId>
       <artifactId>github-mcp</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```

## Configuration

The plugin automatically creates a default directory named `cloned-repos` for storing cloned repositories. You can integrate the plugin with your tinystruct application by adding it to your application configuration.

## Usage

### Initializing the Plugin

The GitHub plugin follows the MCP initialization protocol:

```java
// Initialize the GitHub MCP application
GitHub githubApp = new GitHub();
githubApp.init();
```

### JSON-RPC Interface

All operations are accessible through the JSON-RPC endpoint at `/github/rpc`. Here are examples of the available operations:

#### Clone a Repository

```json
{
  "jsonrpc": "2.0",
  "id": "request-1",
  "method": "git.clone",
  "params": {
    "repository": "https://github.com/username/repo.git",
    "branch": "main"
  }
}
```

#### Pull Updates

```json
{
  "jsonrpc": "2.0",
  "id": "request-2",
  "method": "git.pull",
  "params": {
    "repository_path": "/path/to/local/repo",
    "branch": "main"
  }
}
```

#### Push Changes

```json
{
  "jsonrpc": "2.0",
  "id": "request-3",
  "method": "git.push",
  "params": {
    "repository_path": "/path/to/local/repo",
    "remote": "origin",
    "branch": "main"
  }
}
```

#### Check Repository Status

```json
{
  "jsonrpc": "2.0",
  "id": "request-4",
  "method": "git.status",
  "params": {
    "repository_path": "/path/to/local/repo"
  }
}
```

#### Fetch GitHub Issues

```json
{
  "jsonrpc": "2.0",
  "id": "request-5",
  "method": "github.issues",
  "params": {
    "repository": "username/repo",
    "token": "your-github-token",
    "state": "open"
  }
}
```

#### List Pull Requests

```json
{
  "jsonrpc": "2.0",
  "id": "request-6",
  "method": "github.prs",
  "params": {
    "repository": "username/repo",
    "token": "your-github-token",
    "state": "open"
  }
}
```

#### Access GitHub Actions Workflows

```json
{
  "jsonrpc": "2.0",
  "id": "request-7",
  "method": "github.actions",
  "params": {
    "repository": "username/repo",
    "token": "your-github-token"
  }
}
```

## Response Format

All responses follow the JSON-RPC 2.0 specification:

```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "result": {
    "status": "success",
    "repository": "https://github.com/username/repo.git",
    "branch": "main",
    "target_path": "cloned-repos/repo"
  }
}
```

## Authentication

- For GitHub API operations, you need to provide a valid GitHub personal access token with appropriate permissions.
- For Git operations that require authentication, you'll need to configure your Git credentials separately.

## Error Handling

The plugin follows the JSON-RPC 2.0 specification for error handling:

```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "error": {
    "code": -32603,
    "message": "Git clone failed: Authentication failed"
  }
}
```

## Integration with tinystruct

This plugin extends the `MCPApplication` class from the tinystruct framework, making it compatible with the standard tinystruct application lifecycle and configuration system.

## Development

### Building from Source

```bash
git clone https://github.com/tinystruct/github-plugin.git
cd github-plugin
mvn clean package
```

### Running Tests

```bash
mvn test
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For support and questions, please open an issue on the GitHub repository.
```
