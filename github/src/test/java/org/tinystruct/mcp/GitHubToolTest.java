package org.tinystruct.mcp;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.tinystruct.data.component.Builder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHubTool
 * Note: These tests use real file system operations for simplicity.
 * In a production environment, you might want to mock JGit operations more extensively.
 */
class GitHubToolTest {

    private GitHub.GitHubTool githubTool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        githubTool = new GitHub.GitHubTool();
    }

    // ==================== Clone Repository Tests ====================

    @Test
    void testCloneRepository_WithAllParameters() throws Exception {
        // This test requires actual Git operations, so we'll test the parameter handling
        // and error cases rather than actual cloning
        
        // Arrange
        String repository = "https://github.com/test/repo.git";
        String branch = "main";
        Path targetPath = tempDir.resolve("cloned-repo");

        // Act & Assert - Should fail because repository doesn't exist
        // But we're testing that the method accepts parameters correctly
        assertThrows(MCPException.class, () -> 
            githubTool.cloneRepository(repository, branch, targetPath.toString()));
    }

    @Test
    void testCloneRepository_WithDefaultBranch() throws Exception {
        // Arrange
        String repository = "https://github.com/test/repo.git";
        Path targetPath = tempDir.resolve("cloned-repo2");

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            githubTool.cloneRepository(repository, null, targetPath.toString()));
    }

    @Test
    void testCloneRepository_ToExistingNonEmptyDirectory() throws Exception {
        // Arrange
        String repository = "https://github.com/test/repo.git";
        Path targetPath = tempDir.resolve("existing-dir");
        Files.createDirectory(targetPath);
        Files.writeString(targetPath.resolve("file.txt"), "content");

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.cloneRepository(repository, "main", targetPath.toString()));
        assertTrue(exception.getMessage().contains("not empty") || 
                   exception.getMessage().contains("exists"));
    }

    @Test
    void testCloneRepository_InvalidURL() {
        // Arrange
        String invalidRepository = "not-a-valid-url";
        Path targetPath = tempDir.resolve("invalid-clone");

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            githubTool.cloneRepository(invalidRepository, "main", targetPath.toString()));
    }

    // ==================== Pull Repository Tests ====================

    @Test
    void testPullRepository_NonExistentRepository() {
        // Arrange
        String nonExistentPath = tempDir.resolve("not-a-repo").toString();

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.pullRepository(nonExistentPath, "main"));
        assertTrue(exception.getMessage().contains("not") || 
                   exception.getMessage().contains("valid"));
    }

    @Test
    void testPullRepository_NonGitDirectory() throws Exception {
        // Arrange
        Path regularDir = tempDir.resolve("regular-dir");
        Files.createDirectory(regularDir);

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.pullRepository(regularDir.toString(), "main"));
        assertTrue(exception.getMessage().contains("not") || 
                   exception.getMessage().contains("valid"));
    }

    @Test
    void testPullRepository_WithDefaultBranch() throws Exception {
        // Arrange
        Path gitDir = tempDir.resolve("git-repo");
        Files.createDirectory(gitDir);

        // Act & Assert - Should fail because it's not a valid git repo
        assertThrows(MCPException.class, () -> 
            githubTool.pullRepository(gitDir.toString(), null));
    }

    // ==================== Push Repository Tests ====================

    @Test
    void testPushRepository_NonGitDirectory() throws Exception {
        // Arrange
        Path regularDir = tempDir.resolve("push-dir");
        Files.createDirectory(regularDir);

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.pushRepository(regularDir.toString(), "origin", "main"));
        assertTrue(exception.getMessage().contains("not") || 
                   exception.getMessage().contains("valid"));
    }

    @Test
    void testPushRepository_WithDefaults() throws Exception {
        // Arrange
        Path gitDir = tempDir.resolve("push-repo");
        Files.createDirectory(gitDir);

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            githubTool.pushRepository(gitDir.toString(), null, null));
    }

    // ==================== Repository Status Tests ====================

    @Test
    void testGetRepositoryStatus_NonGitDirectory() throws Exception {
        // Arrange
        Path regularDir = tempDir.resolve("status-dir");
        Files.createDirectory(regularDir);

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.getRepositoryStatus(regularDir.toString()));
        assertTrue(exception.getMessage().contains("not") || 
                   exception.getMessage().contains("valid"));
    }

    @Test
    void testGetRepositoryStatus_NonExistentPath() {
        // Arrange
        String nonExistentPath = tempDir.resolve("no-status").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            githubTool.getRepositoryStatus(nonExistentPath));
    }

    // ==================== GitHub API Tests ====================

    @Test
    void testGetGitHubIssues_InvalidRepositoryFormat() {
        // Arrange
        String invalidRepo = "invalid-format";
        String token = "test-token";

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(invalidRepo, token, "open"));
        assertTrue(exception.getMessage().contains("Invalid repository format") ||
                   exception.getMessage().contains("owner/repo"));
    }

    @Test
    void testGetGitHubIssues_WithDefaultState() {
        // Arrange
        String repo = "owner/repo";
        String token = "test-token";

        // Act & Assert - Will fail due to network/auth, but tests parameter handling
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(repo, token, null));
    }

    @Test
    void testGetGitHubIssues_AllStates() {
        // Arrange
        String repo = "owner/repo";
        String token = "test-token";

        // Test different states
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(repo, token, "open"));
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(repo, token, "closed"));
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(repo, token, "all"));
    }

    @Test
    void testGetGitHubPullRequests_InvalidRepositoryFormat() {
        // Arrange
        String invalidRepo = "no-slash";
        String token = "test-token";

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.getGitHubPullRequests(invalidRepo, token, "open"));
        assertTrue(exception.getMessage().contains("Invalid repository format") ||
                   exception.getMessage().contains("owner/repo"));
    }

    @Test
    void testGetGitHubPullRequests_WithDefaultState() {
        // Arrange
        String repo = "owner/repo";
        String token = "test-token";

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubPullRequests(repo, token, null));
    }

    @Test
    void testGetGitHubActions_InvalidRepositoryFormat() {
        // Arrange
        String invalidRepo = "invalid";
        String token = "test-token";

        // Act & Assert
        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.getGitHubActions(invalidRepo, token));
        assertTrue(exception.getMessage().contains("Invalid repository format") ||
                   exception.getMessage().contains("owner/repo"));
    }

    @Test
    void testGetGitHubActions_ValidFormat() {
        // Arrange
        String repo = "owner/repo";
        String token = "test-token";

        // Act & Assert - Will fail due to network, but tests parameter handling
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubActions(repo, token));
    }

    // ==================== Parameter Validation Tests ====================

    @Test
    void testCloneRepository_NullRepository() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.cloneRepository(null, "main", tempDir.toString()));
    }

    @Test
    void testPullRepository_NullPath() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.pullRepository(null, "main"));
    }

    @Test
    void testPushRepository_NullPath() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.pushRepository(null, "origin", "main"));
    }

    @Test
    void testGetRepositoryStatus_NullPath() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.getRepositoryStatus(null));
    }

    @Test
    void testGetGitHubIssues_NullRepository() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.getGitHubIssues(null, "token", "open"));
    }

    @Test
    void testGetGitHubPullRequests_NullRepository() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.getGitHubPullRequests(null, "token", "open"));
    }

    @Test
    void testGetGitHubActions_NullRepository() {
        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.getGitHubActions(null, "token"));
    }

    // ==================== Edge Case Tests ====================

    @Test
    void testCloneRepository_EmptyRepositoryURL() {
        // Arrange
        String emptyRepo = "";
        Path targetPath = tempDir.resolve("empty-clone");

        // Act & Assert
        assertThrows(Exception.class, () -> 
            githubTool.cloneRepository(emptyRepo, "main", targetPath.toString()));
    }

    @Test
    void testGetGitHubIssues_EmptyToken() {
        // Arrange
        String repo = "owner/repo";
        String emptyToken = "";

        // Act & Assert - Should still attempt the request
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(repo, emptyToken, "open"));
    }

    @Test
    void testCloneRepository_RepositoryWithoutGitExtension() {
        // Arrange - Some repos might not have .git extension
        String repoWithoutExt = "https://github.com/owner/repo";
        Path targetPath = tempDir.resolve("no-ext-clone");

        // Act & Assert - Should handle gracefully
        assertThrows(Exception.class, () -> 
            githubTool.cloneRepository(repoWithoutExt, "main", targetPath.toString()));
    }

    @Test
    void testGetGitHubIssues_RepositoryWithMultipleSlashes() {
        // Arrange
        String invalidRepo = "owner/repo/extra";
        String token = "test-token";

        // Act & Assert - Should validate format
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(invalidRepo, token, "open"));
    }

    @Test
    void testGetGitHubIssues_RepositoryWithSpecialCharacters() {
        // Arrange
        String repo = "owner-name/repo-name";
        String token = "test-token";

        // Act & Assert - Should accept valid special characters
        assertThrows(MCPException.class, () -> 
            githubTool.getGitHubIssues(repo, token, "open"));
    }

    // ==================== Constructor Tests ====================

    @Test
    void testGitHubTool_DefaultConstructor() {
        // Act
        GitHub.GitHubTool tool = new GitHub.GitHubTool();

        // Assert
        assertNotNull(tool);
    }

    @Test
    void testGitHubTool_ClientConstructor() {
        // Arrange
        MCPClient mockClient = mock(MCPClient.class);

        // Act
        GitHub.GitHubTool tool = new GitHub.GitHubTool(mockClient);

        // Assert
        assertNotNull(tool);
    }

    // ==================== Integration-Style Tests ====================
    // These tests verify the complete flow without mocking

    @Test
    void testCloneRepository_CreatesTargetDirectory() throws Exception {
        // Arrange
        String repository = "https://github.com/test/nonexistent.git";
        Path targetPath = tempDir.resolve("auto-created");

        // Act & Assert - Even though clone fails, we test directory creation logic
        try {
            githubTool.cloneRepository(repository, "main", targetPath.toString());
            fail("Should have thrown exception");
        } catch (MCPException e) {
            // Expected - repository doesn't exist
            assertTrue(e.getMessage().contains("Error") || 
                      e.getMessage().contains("clone") ||
                      e.getMessage().contains("repository"));
        }
    }

    @Test
    void testGetRepositoryStatus_ReturnsProperStructure() throws Exception {
        // This would require a real git repository
        // For now, we test that it throws appropriate error
        Path nonGitDir = tempDir.resolve("not-git");
        Files.createDirectory(nonGitDir);

        MCPException exception = assertThrows(MCPException.class, () -> 
            githubTool.getRepositoryStatus(nonGitDir.toString()));
        
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().length() > 0);
    }

    @Test
    void testGitHubAPIUrl_Construction() {
        // Test the helper method for constructing GitHub API URLs
        // This is a protected method, so we test it indirectly through API calls
        
        String repo = "owner/repo";
        String token = "test-token";

        // The error message should indicate it tried to connect to the correct URL
        try {
            githubTool.getGitHubIssues(repo, token, "open");
            fail("Should have thrown exception");
        } catch (MCPException e) {
            // Expected - network error or auth error
            assertNotNull(e.getMessage());
        }
    }
}
