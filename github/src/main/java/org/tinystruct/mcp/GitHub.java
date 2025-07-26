package org.tinystruct.mcp;

import org.eclipse.jgit.api.Git;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        GitHubTool githubTool = new GitHubTool();
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

        /**
         * Constructs a new GitHubTool with local execution support.
         */
        public GitHubTool() {
            // Note the true parameter at the end to enable local execution
            super("github", "Tool for performing Git and GitHub operations");
        }

        /**
         * Constructs a new GitHubTool with a client.
         *
         * @param client The MCP client
         */
        public GitHubTool(MCPClient client) {
            // Note the true parameter at the end to enable local execution
            super("github", "Tool for performing Git and GitHub operations", null, client, true);
        }



        /**
         * Clones a Git repository.
         * @param repository The repository URL
         * @param branch The branch to clone (optional, defaults to 'main')
         * @param targetPath The target path for cloning (optional)
         * @return The result of the clone operation
         * @throws MCPException If cloning fails
         */
        @Action(value = "github/clone", description = "Clone a Git repository", arguments = {
                @Argument(key = "repository", description = "The repository URL", type = "string"),
                @Argument(key = "branch", description = "The branch to clone", type = "string"),
                @Argument(key = "target_path", description = "The target path for cloning", type = "string")
        })
        public Builder cloneRepository(String repository, String branch, String targetPath) throws MCPException {
            try {
                return cloneRepositoryInternal(repository, branch != null ? branch : "main", targetPath);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error cloning repository: " + e.getMessage(), e);
                throw new MCPException("Error cloning repository: " + e.getMessage());
            }
        }

        /**
         * Pulls changes from a Git repository.
         * @param repositoryPath The path to the local repository
         * @param branch The branch to pull from (optional, defaults to 'main')
         * @return The result of the pull operation
         * @throws MCPException If pulling fails
         */
        @Action(value = "github/pull", description = "Pull changes from a Git repository", arguments = {
                @Argument(key = "repository", description = "The path to the local repository", type = "string"),
                @Argument(key = "branch", description = "The branch to pull from", type = "string")
        })
        public Builder pullRepository(String repositoryPath, String branch) throws MCPException {
            try {
                return pullRepositoryInternal(repositoryPath, branch != null ? branch : "main");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error pulling repository: " + e.getMessage(), e);
                throw new MCPException("Error pulling repository: " + e.getMessage());
            }
        }

        /**
         * Pushes changes to a Git repository.
         * @param repositoryPath The path to the local repository
         * @param remote The remote name (optional, defaults to 'origin')
         * @param branch The branch to push (optional, defaults to 'main')
         * @return The result of the push operation
         * @throws MCPException If pushing fails
         */
        @Action(value = "github/push", description = "Push changes to a Git repository", arguments = {
                @Argument(key = "repository", description = "The path to the local repository", type = "string"),
                @Argument(key = "remote", description = "The remote name", type = "string"),
                @Argument(key = "branch", description = "The branch to push", type = "string")
        })
        public Builder pushRepository(String repositoryPath, String remote, String branch) throws MCPException {
            try {
                return pushRepositoryInternal(repositoryPath, remote != null ? remote : "origin", branch != null ? branch : "main");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error pushing repository: " + e.getMessage(), e);
                throw new MCPException("Error pushing repository: " + e.getMessage());
            }
        }

        /**
         * Gets the status of a Git repository.
         * @param repositoryPath The path to the local repository
         * @return The repository status
         * @throws MCPException If getting status fails
         */
        @Action(value = "github/status", description = "Get the status of a Git repository", arguments = {
                @Argument(key = "repository", description = "The path to the local repository", type = "string")
        })
        public Builder getRepositoryStatus(String repositoryPath) throws MCPException {
            try {
                return getRepositoryStatusInternal(repositoryPath);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error getting repository status: " + e.getMessage(), e);
                throw new MCPException("Error getting repository status: " + e.getMessage());
            }
        }

        /**
         * Gets GitHub issues for a repository.
         * @param repository The repository in format 'owner/repo'
         * @param token The GitHub API token
         * @param state The state of issues (open, closed, all)
         * @return The issues data
         * @throws MCPException If fetching issues fails
         */
        @Action(value = "github/issues", description = "Get GitHub issues for a repository", arguments = {
                @Argument(key = "repository", description = "The repository in format 'owner/repo'", type = "string"),
                @Argument(key = "token", description = "The GitHub API token", type = "string"),
                @Argument(key = "state", description = "The state of issues", type = "string")
        })
        public Builder getGitHubIssues(String repository, String token, String state) throws MCPException {
            try {
                return getGitHubIssuesInternal(repository, token, state != null ? state : "open");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error fetching GitHub issues: " + e.getMessage(), e);
                throw new MCPException("Error fetching GitHub issues: " + e.getMessage());
            }
        }

        /**
         * Gets GitHub pull requests for a repository.
         * @param repository The repository in format 'owner/repo'
         * @param token The GitHub API token
         * @param state The state of PRs (open, closed, all)
         * @return The pull requests data
         * @throws MCPException If fetching PRs fails
         */
        @Action(value = "github/prs", description = "Get GitHub pull requests for a repository", arguments = {
                @Argument(key = "repository", description = "The repository in format 'owner/repo'", type = "string"),
                @Argument(key = "token", description = "The GitHub API token", type = "string"),
                @Argument(key = "state", description = "The state of PRs", type = "string")
        })
        public Builder getGitHubPullRequests(String repository, String token, String state) throws MCPException {
            try {
                return getGitHubPullRequestsInternal(repository, token, state != null ? state : "open");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error fetching GitHub pull requests: " + e.getMessage(), e);
                throw new MCPException("Error fetching GitHub pull requests: " + e.getMessage());
            }
        }

        /**
         * Gets GitHub Actions workflows for a repository.
         * @param repository The repository in format 'owner/repo'
         * @param token The GitHub API token
         * @return The workflows data
         * @throws MCPException If fetching workflows fails
         */
        @Action(value = "github/actions", description = "Get GitHub Actions workflows for a repository", arguments = {
                @Argument(key = "repository", description = "The repository in format 'owner/repo'", type = "string"),
                @Argument(key = "token", description = "The GitHub API token", type = "string")
        })
        public Builder getGitHubActions(String repository, String token) throws MCPException {
            try {
                return getGitHubActionsInternal(repository, token);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error fetching GitHub Actions workflows: " + e.getMessage(), e);
                throw new MCPException("Error fetching GitHub Actions workflows: " + e.getMessage());
            }
        }

        private Builder cloneRepositoryInternal(String repository, String branch, String targetPath) throws Exception {
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

        private Builder pullRepositoryInternal(String repositoryPath, String branch) throws Exception {
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

        private Builder pushRepositoryInternal(String repositoryPath, String remote, String branch) throws Exception {
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

        private Builder getRepositoryStatusInternal(String repositoryPath) throws Exception {
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

        protected Builder getGitHubIssuesInternal(String repository, String token, String state) throws MCPException {
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

        protected Builder getGitHubPullRequestsInternal(String repository, String token, String state) throws MCPException {
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

        protected Builder getGitHubActionsInternal(String repository, String token) throws MCPException {
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