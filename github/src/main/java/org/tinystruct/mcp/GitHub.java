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
public class GitHub extends MCPApplication {
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

        // Create the default directory for cloned repositories
        new File(DEFAULT_CLONE_DIR).mkdirs();
    }

    @Override
    public String version() {
        return GITHUB_PROTOCOL_VERSION;
    }

    /**
     * Handles JSON-RPC requests for Git and GitHub operations
     */
    @Action("github/rpc")
    public String handleGitHubRpcRequest(Request request, Response response) throws ApplicationException {
        try {
            // Validate authentication using parent class's auth handler
            authHandler.validateAuthHeader(request);

            // Parse the JSON-RPC request
            String jsonStr = request.body();
            LOGGER.fine("Received JSON: " + jsonStr);

            // Add batch request support
            if (jsonStr.trim().startsWith("[")) {
                return jsonRpcHandler.handleBatchRequest(jsonStr, response, this::handleGitHubMethod);
            }

            if (!jsonRpcHandler.validateJsonRpcRequest(jsonStr)) {
                response.setStatus(ResponseStatus.BAD_REQUEST);
                return jsonRpcHandler.createErrorResponse("Invalid JSON-RPC request", ErrorCodes.INVALID_REQUEST);
            }

            JsonRpcRequest rpcRequest = new JsonRpcRequest();
            rpcRequest.parse(jsonStr);

            JsonRpcResponse jsonResponse = new JsonRpcResponse();
            handleGitHubMethod(rpcRequest, jsonResponse);

            response.addHeader("Content-Type", Http.CONTENT_TYPE_JSON);
            return jsonResponse.toString();

        } catch (SecurityException e) {
            response.setStatus(ResponseStatus.UNAUTHORIZED);
            return jsonRpcHandler.createErrorResponse("Unauthorized", ErrorCodes.UNAUTHORIZED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "RPC request failed", e);
            response.setStatus(ResponseStatus.INTERNAL_SERVER_ERROR);
            return jsonRpcHandler.createErrorResponse("Internal server error: " + e.getMessage(), ErrorCodes.INTERNAL_ERROR);
        }
    }

    private void handleGitHubMethod(JsonRpcRequest request, JsonRpcResponse response) {
        String method = request.getMethod();
        switch (method) {
            case MCPLifecycle.METHOD_INITIALIZE:
                handleInitialize(request, response);
                break;
            case "git.clone":
                handleGitClone(request, response);
                break;
            case "git.pull":
                handleGitPull(request, response);
                break;
            case "git.push":
                handleGitPush(request, response);
                break;
            case "git.status":
                handleGitStatus(request, response);
                break;
            case "github.issues":
                handleGitHubIssues(request, response);
                break;
            case "github.prs":
                handleGitHubPRs(request, response);
                break;
            case "github.actions":
                handleGitHubActions(request, response);
                break;
            default:
                response.setError(new JsonRpcError(ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + method));
        }
    }

    public void handleInitialize(JsonRpcRequest request, JsonRpcResponse response) {
        if (!initialized) {
            // Create tools list
            Builder tools = new Builder();
            tools.put("git.clone", new Builder()
                    .put("name", "Clone Repository")
                    .put("description", "Clone a Git repository")
                    .put("parameters", new Builder()
                            .put("repository", new Builder()
                                    .put("type", "string")
                                    .put("description", "Repository URL to clone")
                                    .put("required", true))
                            .put("branch", new Builder()
                                    .put("type", "string")
                                    .put("description", "Branch to clone")
                                    .put("required", false))));

            tools.put("git.pull", new Builder()
                    .put("name", "Pull Changes")
                    .put("description", "Pull changes from remote repository")
                    .put("parameters", new Builder()
                            .put("repository_path", new Builder()
                                    .put("type", "string")
                                    .put("description", "Path to local repository")
                                    .put("required", true))));

            tools.put("git.push", new Builder()
                    .put("name", "Push Changes")
                    .put("description", "Push changes to remote repository")
                    .put("parameters", new Builder()
                            .put("repository_path", new Builder()
                                    .put("type", "string")
                                    .put("description", "Path to local repository")
                                    .put("required", true))
                            .put("remote", new Builder()
                                    .put("type", "string")
                                    .put("description", "Remote name")
                                    .put("required", false))
                            .put("branch", new Builder()
                                    .put("type", "string")
                                    .put("description", "Branch name")
                                    .put("required", false))));

            tools.put("git.status", new Builder()
                    .put("name", "Repository Status")
                    .put("description", "Get repository status")
                    .put("parameters", new Builder()
                            .put("repository_path", new Builder()
                                    .put("type", "string")
                                    .put("description", "Path to local repository")
                                    .put("required", true))));

            tools.put("github.issues", new Builder()
                    .put("name", "List Issues")
                    .put("description", "List GitHub repository issues")
                    .put("parameters", new Builder()
                            .put("repository", new Builder()
                                    .put("type", "string")
                                    .put("description", "Repository in owner/repo format")
                                    .put("required", true))
                            .put("token", new Builder()
                                    .put("type", "string")
                                    .put("description", "GitHub API token")
                                    .put("required", true))
                            .put("state", new Builder()
                                    .put("type", "string")
                                    .put("description", "Issue state (open/closed/all)")
                                    .put("required", false))));

            tools.put("github.prs", new Builder()
                    .put("name", "List Pull Requests")
                    .put("description", "List GitHub repository pull requests")
                    .put("parameters", new Builder()
                            .put("repository", new Builder()
                                    .put("type", "string")
                                    .put("description", "Repository in owner/repo format")
                                    .put("required", true))
                            .put("token", new Builder()
                                    .put("type", "string")
                                    .put("description", "GitHub API token")
                                    .put("required", true))
                            .put("state", new Builder()
                                    .put("type", "string")
                                    .put("description", "PR state (open/closed/all)")
                                    .put("required", false))));

            tools.put("github.actions", new Builder()
                    .put("name", "List Workflows")
                    .put("description", "List GitHub Actions workflows")
                    .put("parameters", new Builder()
                            .put("repository", new Builder()
                                    .put("type", "string")
                                    .put("description", "Repository in owner/repo format")
                                    .put("required", true))
                            .put("token", new Builder()
                                    .put("type", "string")
                                    .put("description", "GitHub API token")
                                    .put("required", true))));

            // Create resources list
            Builder resources = new Builder()
                    .put("repositories", new Builder()
                            .put("type", "array")
                            .put("description", "List of cloned repositories")
                            .put("items", new Builder()
                                    .put("type", "object")
                                    .put("properties", new Builder()
                                            .put("path", new Builder().put("type", "string"))
                                            .put("url", new Builder().put("type", "string"))
                                            .put("branch", new Builder().put("type", "string")))));

            // Create initialize response with tools and resources
            Builder result = new Builder();
            result.put("id", UUID.randomUUID().toString());
            result.put("features", GITHUB_FEATURES);
            result.put("tools", tools);
            result.put("resources", resources);

            response.setId(request.getId());
            response.setResult(result);
            initialized = true;
        } else {
            response.setError(new JsonRpcError(-32600, "Server already initialized"));
        }
    }

    private void handleGitClone(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received git.clone request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository")) {
            LOGGER.warning("Invalid params: repository URL required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository URL required"));
            return;
        }

        String repository = params.get("repository").toString();
        String branch = params.containsKey("branch") ? params.get("branch").toString() : "main";
        String targetPath = params.containsKey("target_path") ? params.get("target_path").toString() : null;

        LOGGER.info("Attempting to clone repository: " + repository + " (branch: " + branch + ")");

        try {
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
                    LOGGER.warning("Target directory exists and is not empty: " + finalTargetPath);
                    response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Target directory exists and is not empty: " + finalTargetPath));
                    return;
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

            response.setId(request.getId());
            response.setResult(responseResult);

            // Broadcast clone event to all connected clients
            Builder eventData = new Builder();
            eventData.put("type", "repository_cloned");
            eventData.put("repository", repository);
            eventData.put("branch", branch);
            eventData.put("path", finalTargetPath.toString());
            sseHandler.broadcastEvent(Events.NOTIFICATION, eventData.toString());

            LOGGER.info("Prepared JSON-RPC response: " + response.toString());

        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Failed to clone repository: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Git clone failed: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during clone: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleGitPull(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received git.pull request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository_path")) {
            LOGGER.warning("Invalid params: repository_path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository_path required"));
            return;
        }

        String repositoryPath = params.get("repository_path").toString();
        String branch = params.containsKey("branch") ? params.get("branch").toString() : null;

        LOGGER.info("Attempting to pull repository at: " + repositoryPath);

        try {
            File gitDir = new File(repositoryPath);
            if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
                LOGGER.warning("Not a valid git repository: " + repositoryPath);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Not a valid git repository: " + repositoryPath));
                return;
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

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Pull completed with status: " + result.isSuccessful());

        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Failed to pull repository: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Git pull failed: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during pull: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleGitPush(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received git.push request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository_path")) {
            LOGGER.warning("Invalid params: repository_path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository_path required"));
            return;
        }

        String repositoryPath = params.get("repository_path").toString();
        String remote = params.containsKey("remote") ? params.get("remote").toString() : "origin";
        String branch = params.containsKey("branch") ? params.get("branch").toString() : "main";

        LOGGER.info("Attempting to push repository at: " + repositoryPath + " to " + remote + "/" + branch);

        try {
            File gitDir = new File(repositoryPath);
            if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
                LOGGER.warning("Not a valid git repository: " + repositoryPath);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Not a valid git repository: " + repositoryPath));
                return;
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

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Push completed with status: " + success);

        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Failed to push repository: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Git push failed: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during push: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleGitStatus(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received git.status request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository_path")) {
            LOGGER.warning("Invalid params: repository_path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository_path required"));
            return;
        }

        String repositoryPath = params.get("repository_path").toString();

        LOGGER.info("Checking status of repository at: " + repositoryPath);

        try {
            File gitDir = new File(repositoryPath);
            if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
                LOGGER.warning("Not a valid git repository: " + repositoryPath);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Not a valid git repository: " + repositoryPath));
                return;
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

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Status check completed. Repository is " + (isClean ? "clean" : "dirty"));

        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Failed to get repository status: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Git status failed: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during status check: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleGitHubIssues(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received github.issues request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository") || !params.containsKey("token")) {
            LOGGER.warning("Invalid params: repository and token required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository and token required"));
            return;
        }

        String repository = params.get("repository").toString();
        String token = params.get("token").toString();
        String state = params.containsKey("state") ? params.get("state").toString() : "open";

        LOGGER.info("Fetching GitHub issues for repository: " + repository);

        try {
            // Format: owner/repo
            String[] repoParts = repository.split("/");
            if (repoParts.length != 2) {
                LOGGER.warning("Invalid repository format. Expected 'owner/repo'");
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid repository format. Expected 'owner/repo'"));
                return;
            }

            String owner = repoParts[0];
            String repo = repoParts[1];

            // Create GitHub API URL
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/issues?state=" + state;

            // Make HTTP request to GitHub API
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("Authorization", "token " + token);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                // Read response
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
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
                Builder responseResult = new Builder();
                responseResult.put("status", "success");
                responseResult.put("repository", repository);
                responseResult.put("total", issuesJson.size());
                responseResult.put("issues", issuesJson);

                response.setId(request.getId());
                response.setResult(responseResult);

                // Broadcast issues update event
                Builder eventData = new Builder();
                eventData.put("type", "issues_updated");
                eventData.put("repository", repository);
                eventData.put("count", issuesJson.size());
                sseHandler.broadcastEvent(Events.NOTIFICATION, eventData.toString());

                LOGGER.info("Successfully fetched " + issuesJson.size() + " issues for " + repository);
            } else {
                // Handle error
                LOGGER.warning("GitHub API returned error code: " + responseCode);
                response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "GitHub API error: " + responseCode));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch GitHub issues: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to fetch GitHub issues: " + e.getMessage()));
        }
    }

    private void handleGitHubPRs(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received github.prs request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository") || !params.containsKey("token")) {
            LOGGER.warning("Invalid params: repository and token required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository and token required"));
            return;
        }

        String repository = params.get("repository").toString();
        String token = params.get("token").toString();
        String state = params.containsKey("state") ? params.get("state").toString() : "open";

        LOGGER.info("Fetching GitHub pull requests for repository: " + repository);

        try {
            // Format: owner/repo
            String[] repoParts = repository.split("/");
            if (repoParts.length != 2) {
                LOGGER.warning("Invalid repository format. Expected 'owner/repo'");
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid repository format. Expected 'owner/repo'"));
                return;
            }

            String owner = repoParts[0];
            String repo = repoParts[1];

            // Create GitHub API URL for pull requests
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls?state=" + state;

            // Make HTTP request to GitHub API
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("Authorization", "token " + token);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                // Read response
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
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
                Builder responseResult = new Builder();
                responseResult.put("status", "success");
                responseResult.put("repository", repository);
                responseResult.put("total", prsJson.size());
                responseResult.put("pull_requests", prsJson);

                response.setId(request.getId());
                response.setResult(responseResult);

                LOGGER.info("Successfully fetched " + prsJson.size() + " pull requests for " + repository);
            } else {
                // Handle error
                LOGGER.warning("GitHub API returned error code: " + responseCode);
                response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "GitHub API error: " + responseCode));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch GitHub pull requests: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to fetch GitHub pull requests: " + e.getMessage()));
        }
    }

    private void handleGitHubActions(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received github.actions request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("repository") || !params.containsKey("token")) {
            LOGGER.warning("Invalid params: repository and token required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: repository and token required"));
            return;
        }

        String repository = params.get("repository").toString();
        String token = params.get("token").toString();

        LOGGER.info("Fetching GitHub Actions workflows for repository: " + repository);

        try {
            // Format: owner/repo
            String[] repoParts = repository.split("/");
            if (repoParts.length != 2) {
                LOGGER.warning("Invalid repository format. Expected 'owner/repo'");
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid repository format. Expected 'owner/repo'"));
                return;
            }

            String owner = repoParts[0];
            String repo = repoParts[1];

            // Create GitHub API URL for workflows
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/actions/workflows";

            // Make HTTP request to GitHub API
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("Authorization", "token " + token);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                // Read response
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
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
                Builder workflows;
                if (workflowsJson.containsKey("workflows")) {
                    workflows = (Builder) workflowsJson.get("workflows");
                } else {
                    workflows = workflowsJson;
                }

                // Create response
                Builder responseResult = new Builder();
                responseResult.put("status", "success");
                responseResult.put("repository", repository);
                responseResult.put("total", workflows.size());
                responseResult.put("workflows", workflows);

                response.setId(request.getId());
                response.setResult(responseResult);

                LOGGER.info("Successfully fetched GitHub Actions workflows for " + repository);
            } else {
                // Handle error
                LOGGER.warning("GitHub API returned error code: " + responseCode);
                response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "GitHub API error: " + responseCode));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch GitHub Actions workflows: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to fetch GitHub Actions workflows: " + e.getMessage()));
        }
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

        @Override
        public Builder getSchema() {
            Builder schema = new Builder();
            Builder properties = new Builder();

            // Operation parameter
            Builder operation = new Builder();
            operation.put("type", "string");
            operation.put("description", "The operation to perform (clone, pull, push, status, issues, prs, actions)");
            operation.put("enum", new String[]{"clone", "pull", "push", "status", "issues", "prs", "actions"});

            // Repository parameter
            Builder repository = new Builder();
            repository.put("type", "string");
            repository.put("description", "The repository URL or path");

            // Branch parameter
            Builder branch = new Builder();
            branch.put("type", "string");
            branch.put("description", "The branch name");

            // Token parameter for GitHub API operations
            Builder token = new Builder();
            token.put("type", "string");
            token.put("description", "GitHub API token for authentication");

            // State parameter for issues and PRs
            Builder state = new Builder();
            state.put("type", "string");
            state.put("description", "State of issues or PRs (open, closed, all)");
            state.put("enum", new String[]{"open", "closed", "all"});

            // Remote parameter for push operation
            Builder remote = new Builder();
            remote.put("type", "string");
            remote.put("description", "Remote name for push operation");

            // Target path parameter for clone operation
            Builder targetPath = new Builder();
            targetPath.put("type", "string");
            targetPath.put("description", "Target path for clone operation");

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
                        return cloneRepository(repository, branch, params.containsKey("target_path") ? params.get("target_path").toString() : null);
                    case "pull":
                        return pullRepository(repository, branch);
                    case "push":
                        return pushRepository(repository, params.containsKey("remote") ? params.get("remote").toString() : "origin", branch);
                    case "status":
                        return getRepositoryStatus(repository);
                    case "issues":
                        if (!params.containsKey("token")) {
                            throw new MCPException("Missing required parameter: token");
                        }
                        String issuesToken = params.get("token").toString();
                        String issuesState = params.containsKey("state") ? params.get("state").toString() : "open";
                        return getGitHubIssues(repository, issuesToken, issuesState);
                    case "prs":
                        if (!params.containsKey("token")) {
                            throw new MCPException("Missing required parameter: token");
                        }
                        String prsToken = params.get("token").toString();
                        String prsState = params.containsKey("state") ? params.get("state").toString() : "open";
                        return getGitHubPullRequests(repository, prsToken, prsState);
                    case "actions":
                        if (!params.containsKey("token")) {
                            throw new MCPException("Missing required parameter: token");
                        }
                        String actionsToken = params.get("token").toString();
                        return getGitHubActions(repository, actionsToken);
                    default:
                        throw new MCPException("Unsupported operation: " + operation);
                }
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

        private Builder getGitHubIssues(String repository, String token, String state) throws MCPException {
            try {
                // Format: owner/repo
                String[] repoParts = repository.split("/");
                if (repoParts.length != 2) {
                    throw new MCPException("Invalid repository format. Expected 'owner/repo'");
                }

                String owner = repoParts[0];
                String repo = repoParts[1];

                // Create GitHub API URL
                String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/issues?state=" + state;

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

        private Builder getGitHubPullRequests(String repository, String token, String state) throws MCPException {
            try {
                // Format: owner/repo
                String[] repoParts = repository.split("/");
                if (repoParts.length != 2) {
                    throw new MCPException("Invalid repository format. Expected 'owner/repo'");
                }

                String owner = repoParts[0];
                String repo = repoParts[1];

                // Create GitHub API URL for pull requests
                String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls?state=" + state;

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

        private Builder getGitHubActions(String repository, String token) throws MCPException {
            try {
                // Format: owner/repo
                String[] repoParts = repository.split("/");
                if (repoParts.length != 2) {
                    throw new MCPException("Invalid repository format. Expected 'owner/repo'");
                }

                String owner = repoParts[0];
                String repo = repoParts[1];

                // Create GitHub API URL for workflows
                String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/actions/workflows";

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
    }
}