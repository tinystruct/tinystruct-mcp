package org.tinystruct.mcp;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.http.Request;
import org.tinystruct.http.Response;
import org.tinystruct.http.ResponseStatus;
import org.tinystruct.system.annotation.Action;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.tinystruct.mcp.MCPSpecification.*;

/**
 * GitHub-specific MCP Application that extends the base MCP functionality
 * Implements Git and GitHub operations for repository management with SSE support
 */
// Reference: https://github.com/tinystruct/tinystruct/blob/master/src/main/java/org/tinystruct/mcp/MCPServerApplication.java
public class GitHub extends MCPServerApplication {
    private static final Logger LOGGER = Logger.getLogger(GitHub.class.getName());

    // GitHub MCP specific constants
    private static final String GITHUB_PROTOCOL_VERSION = "1.0.0";
    private static final String[] GITHUB_FEATURES = {
            "base", "lifecycle", "resources", "tools", "github",
            "git.clone", "git.pull", "git.push", "git.status",
            "github.issues", "github.prs", "github.actions"
    };
    private static final String DEFAULT_CLONE_DIR = "cloned-repos";
    private boolean initialized = false;

    @Override
    public void init() {
        super.init();

        // Register GitHub tool methods as individual tools
        GitHubTool githubTool = new GitHubTool("github", "GitHub Tool", new Builder(), null);
        this.registerToolMethods(githubTool);

        // Register a sample prompt (can be customized for GitHub context)
        Builder promptSchema = new Builder();
        Builder properties = new Builder();

        Builder nameParam = new Builder();
        nameParam.put("type", "string");
        nameParam.put("description", "The name to greet");

        properties.put("name", nameParam);
        promptSchema.put("type", "object");
        promptSchema.put("properties", properties);
        promptSchema.put("required", new String[]{"name"});

        MCPPrompt greetingPrompt = new MCPPrompt(
            "greeting",
            "A simple greeting prompt",
            "Hello, {{name}}! Welcome to the GitHub MCP server.",
            promptSchema,
            null
        ) {
            @Override
            protected boolean supportsLocalExecution() {
                return true;
            }
        };

        this.registerPrompt(greetingPrompt);
    }

    @Override
    public String version() {
        return GITHUB_PROTOCOL_VERSION;
    }

    /**
     * Inner class GitHubTool for performing Git and GitHub operations.
     */
    public static class GitHubTool extends MCPTool {
        private static final Logger LOGGER = Logger.getLogger(GitHubTool.class.getName());
        private static final String DEFAULT_CLONE_DIR = "cloned-repos";

        public GitHubTool(String name, String description, Builder schema, MCPClient client) {
            super(name, description, schema, client);
        }

        public GitHubTool(String name, String description, Builder schema, MCPClient client, boolean supportsLocalExecution) {
            super(name, description, schema, client, supportsLocalExecution);
        }

        @Override
        public String getName() {
            return "github";
        }

        @Override
        public String getDescription() {
            return "Tool for performing Git and GitHub operations";
        }

        /**
         * Returns the schema for the GitHub tool, describing all supported operations and parameters.
         */
        @Override
        public Builder getSchema() {
            Builder schema = new Builder();
            Builder properties = new Builder();

            // Operation parameter (required)
            Builder operation = new Builder();
            operation.put("type", "string");
            operation.put("description", "The operation to perform (clone, pull, push, status, issues, prs, actions)");
            operation.put("enum", new String[]{"clone", "pull", "push", "status", "issues", "prs", "actions"});

            // Repository parameter (required for most operations)
            Builder repository = new Builder();
            repository.put("type", "string");
            repository.put("description", "The repository URL or path (for git operations) or owner/repo (for GitHub API operations)");

            // Branch parameter (optional)
            Builder branch = new Builder();
            branch.put("type", "string");
            branch.put("description", "The branch name (optional, defaults to 'main')");

            // Token parameter (required for GitHub API operations)
            Builder token = new Builder();
            token.put("type", "string");
            token.put("description", "GitHub API token for authentication (required for issues, prs, actions)");

            // State parameter (optional for issues/prs)
            Builder state = new Builder();
            state.put("type", "string");
            state.put("description", "State of issues or PRs (open, closed, all)");
            state.put("enum", new String[]{"open", "closed", "all"});

            // Remote parameter (optional for push)
            Builder remote = new Builder();
            remote.put("type", "string");
            remote.put("description", "Remote name for push operation (optional, defaults to 'origin')");

            // Target path parameter (optional for clone)
            Builder targetPath = new Builder();
            targetPath.put("type", "string");
            targetPath.put("description", "Target path for clone operation (optional)");

            // Add all parameters to properties
            properties.put("operation", operation);
            properties.put("repository", repository);
            properties.put("branch", branch);
            properties.put("token", token);
            properties.put("state", state);
            properties.put("remote", remote);
            properties.put("target_path", targetPath);

            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", new String[]{"operation", "repository"});

            return schema;
        }

        /**
         * Executes the requested Git or GitHub operation.
         * Throws MCPException for missing/invalid parameters or unsupported operations.
         */
        @Override
        public Object execute(Builder params) throws MCPException {
            if (params == null || !params.containsKey("operation") || !params.containsKey("repository")) {
                throw new MCPException("Missing required parameters: operation and repository");
            }

            String operation = params.get("operation").toString();
            String repository = params.get("repository").toString();
            String branch = params.containsKey("branch") ? params.get("branch").toString() : "main";

            LOGGER.info("Executing GitHub tool with operation: " + operation + " on repository: " + repository);

            try {
                switch (operation) {
                    case "clone":
                        // target_path is optional
                        return cloneRepository(repository, branch, params.containsKey("target_path") ? params.get("target_path").toString() : null);
                    case "pull":
                        return pullRepository(repository, branch);
                    case "push":
                        return pushRepository(
                            repository,
                            params.containsKey("remote") ? params.get("remote").toString() : "origin",
                            branch
                        );
                    case "status":
                        return getRepositoryStatus(repository);
                    case "issues":
                        if (!params.containsKey("token")) {
                            throw new MCPException("Missing required parameter: token for issues operation");
                        }
                        String issuesToken = params.get("token").toString();
                        String issuesState = params.containsKey("state") ? params.get("state").toString() : "open";
                        return getGitHubIssues(repository, issuesToken, issuesState);
                    case "prs":
                        if (!params.containsKey("token")) {
                            throw new MCPException("Missing required parameter: token for prs operation");
                        }
                        String prsToken = params.get("token").toString();
                        String prsState = params.containsKey("state") ? params.get("state").toString() : "open";
                        return getGitHubPullRequests(repository, prsToken, prsState);
                    case "actions":
                        if (!params.containsKey("token")) {
                            throw new MCPException("Missing required parameter: token for actions operation");
                        }
                        String actionsToken = params.get("token").toString();
                        return getGitHubActions(repository, actionsToken);
                    default:
                        throw new MCPException("Unsupported operation: " + operation);
                }
            } catch (MCPException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing GitHub tool: " + e.getMessage(), e);
                throw new MCPException("Error executing GitHub tool: " + e.getMessage());
            }
        }

        private Builder cloneRepository(String repository, String branch, String targetPath) throws Exception {
            // Determine target path
            Path finalTargetPath;
            if (targetPath != null) {
                finalTargetPath = Paths.get(targetPath);
            } else {
                // Extract repository name from URL for the target directory
                String repoName = repository.substring(repository.lastIndexOf('/') + 1, repository.lastIndexOf(".git"));
                finalTargetPath = Paths.get(DEFAULT_CLONE_DIR, repoName);
            }

            LOGGER.info("Target directory: " + finalTargetPath);

            // Check if directory exists and is not empty
            File targetDir = finalTargetPath.toFile();
            if (targetDir.exists()) {
                if (targetDir.list() != null && targetDir.list().length > 0) {
                    throw new MCPException("Target directory exists and is not empty: " + finalTargetPath);
                }
                // If directory exists but is empty, delete it to ensure clean clone
                targetDir.delete();
            }

            // Clone the repository
            Git result = Git.cloneRepository()
                    .setURI(repository)
                    .setBranch(branch)
                    .setDirectory(targetDir)
                    .call();

            LOGGER.info("Successfully cloned " + repository + " to " + finalTargetPath);
            result.close();

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("repository", repository);
            responseResult.put("branch", branch);
            responseResult.put("target_path", finalTargetPath.toString());

            return responseResult;
        }

        private Builder pullRepository(String repositoryPath, String branch) throws Exception {
            File gitDir = new File(repositoryPath);
            if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
                throw new MCPException("Not a valid git repository: " + repositoryPath);
            }

            Git git = Git.open(gitDir);

            // Configure pull command
            org.eclipse.jgit.api.PullCommand pullCmd = git.pull();

            // Set branch if specified
            if (branch != null && !branch.isEmpty()) {
                pullCmd.setRemoteBranchName(branch);
            }

            // Execute pull
            org.eclipse.jgit.api.PullResult result = pullCmd.call();
            git.close();

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("repository_path", repositoryPath);
            responseResult.put("updated", result.isSuccessful());
            responseResult.put("fetch_result", result.getFetchResult().getMessages());
            responseResult.put("merge_result", result.getMergeResult().getMergeStatus().name());

            return responseResult;
        }

        private Builder pushRepository(String repositoryPath, String remote, String branch) throws Exception {
            File gitDir = new File(repositoryPath);
            if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
                throw new MCPException("Not a valid git repository: " + repositoryPath);
            }

            Git git = Git.open(gitDir);

            // Configure and execute push command
            org.eclipse.jgit.transport.PushResult result = git.push()
                    .setRemote(remote)
                    .setRefSpecs(new org.eclipse.jgit.transport.RefSpec(branch))
                    .call()
                    .iterator()
                    .next();

            git.close();

            // Check for errors in the push result
            boolean success = true;
            StringBuilder messages = new StringBuilder();

            for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                if (update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                        update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                    success = false;
                    messages.append(update.getRemoteName()).append(": ").append(update.getStatus()).append("; ");
                }
            }

            // Prepare response
            Builder responseResult = new Builder();
            responseResult.put("status", success ? "success" : "error");
            responseResult.put("repository_path", repositoryPath);
            responseResult.put("pushed", success);
            responseResult.put("remote", remote);
            responseResult.put("branch", branch);

            if (!success) {
                responseResult.put("messages", messages.toString());
            }

            return responseResult;
        }

        private Builder getRepositoryStatus(String repositoryPath) throws Exception {
            File gitDir = new File(repositoryPath);
            if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
                throw new MCPException("Not a valid git repository: " + repositoryPath);
            }

            Git git = Git.open(gitDir);

            // Get status
            org.eclipse.jgit.api.Status status = git.status().call();
            git.close();

            boolean isClean = status.isClean();

            // Prepare response with detailed status information
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("repository_path", repositoryPath);
            responseResult.put("clean", isClean);

            // Add detailed status information
            Builder changes = new Builder();

            // Added files
            Builder added = new Builder();
            int addedIndex = 0;
            for (String file : status.getAdded()) {
                added.put(String.valueOf(addedIndex++), file);
            }
            changes.put("added", added);

            // Changed files
            Builder changed = new Builder();
            int changedIndex = 0;
            for (String file : status.getChanged()) {
                changed.put(String.valueOf(changedIndex++), file);
            }
            changes.put("changed", changed);

            // Removed files
            Builder removed = new Builder();
            int removedIndex = 0;
            for (String file : status.getRemoved()) {
                removed.put(String.valueOf(removedIndex++), file);
            }
            changes.put("removed", removed);

            // Modified files
            Builder modified = new Builder();
            int modifiedIndex = 0;
            for (String file : status.getModified()) {
                modified.put(String.valueOf(modifiedIndex++), file);
            }
            changes.put("modified", modified);

            // Untracked files
            Builder untracked = new Builder();
            int untrackedIndex = 0;
            for (String file : status.getUntracked()) {
                untracked.put(String.valueOf(untrackedIndex++), file);
            }
            changes.put("untracked", untracked);

            // Conflicting files
            Builder conflicting = new Builder();
            int conflictingIndex = 0;
            for (String file : status.getConflicting()) {
                conflicting.put(String.valueOf(conflictingIndex++), file);
            }
            changes.put("conflicting", conflicting);

            responseResult.put("changes", changes);

            return responseResult;
        }

        protected Builder getGitHubIssues(String repository, String token, String state) throws MCPException {
            try {
                // Format: owner/repo
                String[] repoParts = repository.split("/");
                if (repoParts.length != 2) {
                    throw new MCPException("Invalid repository format. Expected 'owner/repo'");
                }

                String owner = repoParts[0];
                String repo = repoParts[1];

                // Create GitHub API URL
                String apiUrl = getGitHubApiUrl(owner, repo, "issues?state=" + state);

                // Make HTTP request to GitHub API
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("Authorization", "token " + token);

                int responseCode = conn.getResponseCode();
                LOGGER.info("GitHub API response code: " + responseCode);

                if (responseCode == 200) {
                    // Read response
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();

                    // Parse JSON response
                    Builder issuesJson = new Builder();
                    issuesJson.parse(content.toString());

                    // Create response
                    Builder result = new Builder();
                    result.put("status", "success");
                    result.put("repository", repository);
                    result.put("total", issuesJson.size());
                    result.put("issues", issuesJson);

                    LOGGER.info("Successfully fetched " + issuesJson.size() + " issues for " + repository);
                    return result;
                } else {
                    // Handle error
                    throw new MCPException("GitHub API error: " + responseCode);
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to fetch GitHub issues: " + e.getMessage(), e);
                throw new MCPException("Failed to fetch GitHub issues: " + e.getMessage());
            }
        }

        protected Builder getGitHubPullRequests(String repository, String token, String state) throws MCPException {
            try {
                // Format: owner/repo
                String[] repoParts = repository.split("/");
                if (repoParts.length != 2) {
                    throw new MCPException("Invalid repository format. Expected 'owner/repo'");
                }

                String owner = repoParts[0];
                String repo = repoParts[1];

                // Create GitHub API URL for pull requests
                String apiUrl = getGitHubApiUrl(owner, repo, "pulls?state=" + state);

                // Make HTTP request to GitHub API
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("Authorization", "token " + token);

                int responseCode = conn.getResponseCode();
                LOGGER.info("GitHub API response code: " + responseCode);

                if (responseCode == 200) {
                    // Read response
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();

                    // Parse JSON response
                    Builder prsJson = new Builder();
                    prsJson.parse(content.toString());

                    // Create response
                    Builder result = new Builder();
                    result.put("status", "success");
                    result.put("repository", repository);
                    result.put("total", prsJson.size());
                    result.put("pull_requests", prsJson);

                    LOGGER.info("Successfully fetched " + prsJson.size() + " pull requests for " + repository);
                    return result;
                } else {
                    // Handle error
                    throw new MCPException("GitHub API error: " + responseCode);
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to fetch GitHub pull requests: " + e.getMessage(), e);
                throw new MCPException("Failed to fetch GitHub pull requests: " + e.getMessage());
            }
        }

        protected Builder getGitHubActions(String repository, String token) throws MCPException {
            try {
                // Format: owner/repo
                String[] repoParts = repository.split("/");
                if (repoParts.length != 2) {
                    throw new MCPException("Invalid repository format. Expected 'owner/repo'");
                }

                String owner = repoParts[0];
                String repo = repoParts[1];

                // Create GitHub API URL for workflows
                String apiUrl = getGitHubApiUrl(owner, repo, "actions/workflows");

                // Make HTTP request to GitHub API
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("Authorization", "token " + token);

                int responseCode = conn.getResponseCode();
                LOGGER.info("GitHub API response code: " + responseCode);

                if (responseCode == 200) {
                    // Read response
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();

                    // Parse JSON response
                    Builder workflowsJson = new Builder();
                    workflowsJson.parse(content.toString());

                    // Extract workflows array if present
                    Builder workflows = workflowsJson.containsKey("workflows") ?
                        (Builder) workflowsJson.get("workflows") : workflowsJson;

                    // Create response
                    Builder result = new Builder();
                    result.put("status", "success");
                    result.put("repository", repository);
                    result.put("total", workflows.size());
                    result.put("workflows", workflows);

                    LOGGER.info("Successfully fetched GitHub Actions workflows for " + repository);
                    return result;
                } else {
                    // Handle error
                    throw new MCPException("GitHub API error: " + responseCode);
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to fetch GitHub Actions workflows: " + e.getMessage(), e);
                throw new MCPException("Failed to fetch GitHub Actions workflows: " + e.getMessage());
            }
        }
        /**
         * Helper method to get the GitHub API URL
         * This can be overridden in tests to use a mock server
         */
        protected String getGitHubApiUrl(String owner, String repo, String endpoint) {
            return "https://api.github.com/repos/" + owner + "/" + repo + "/" + endpoint;
        }
    }
}