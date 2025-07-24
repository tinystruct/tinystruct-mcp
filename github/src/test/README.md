# GitHub MCP End-to-End Tests

This directory contains end-to-end tests for the GitHub MCP functionality.

## Test Types

**GitHubIntegrationTest**: Comprehensive integration tests for GitHub functionality.
   - Starts the MCP server programmatically
   - Connects to the server using the MCPClient
   - Tests both local and remote execution
   - Includes mocked tests for predictable results
   - Tests parameter validation
   - Tests all GitHub operations (issues, PRs, actions, clone, status)

## Running the Tests

To run the integration tests, use one of the following methods:

### Using the provided scripts:

- **Windows**: Run `run-integration-tests.bat` from the github directory
- **Linux/Mac**: Run `run-integration-tests.sh` from the github directory

### Using Maven directly:

```bash
mvn test -Dtest=GitHubIntegrationTest
```

## Test Coverage

The client tests cover:

1. Client-server communication
2. GitHub API operations:
   - Fetching issues
   - Fetching pull requests
   - Fetching GitHub Actions workflows
3. Git operations:
   - Cloning repositories
   - Checking repository status

## Implementation Details

### MCPClient

The tests use the standard `MCPClient` class from the tinystruct framework that:

- Connects to the MCP server
- Discovers available resources (tools, data resources, prompts)
- Executes resources with parameters
- Handles errors appropriately

## Notes

- Some tests might fail if you don't have a valid GitHub token
- The Git clone and status tests use temporary local repositories
- The server is started on port 8000 by default
