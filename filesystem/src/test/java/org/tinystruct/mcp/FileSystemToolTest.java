package org.tinystruct.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tinystruct.data.component.Builder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileSystemTool
 */
class FileSystemToolTest {

    private FileSystem.FileSystemTool fileSystemTool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileSystemTool = new FileSystem.FileSystemTool();
    }

    // ==================== File Info Tests ====================

    @Test
    void testGetFileInfo_ExistingFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // Act
        Builder result = fileSystemTool.getFileInfo(testFile.toString());

        // Assert
        assertNotNull(result);
        assertEquals(testFile.toString(), result.get("path"));
        assertEquals("test.txt", result.get("name"));
        assertEquals(true, result.get("is_file"));
        assertEquals(false, result.get("is_directory"));
        assertTrue((Long) result.get("size") > 0);
    }

    @Test
    void testGetFileInfo_Directory() throws Exception {
        // Arrange
        Path testDir = tempDir.resolve("testdir");
        Files.createDirectory(testDir);

        // Act
        Builder result = fileSystemTool.getFileInfo(testDir.toString());

        // Assert
        assertNotNull(result);
        assertEquals(true, result.get("is_directory"));
        assertEquals(false, result.get("is_file"));
    }

    @Test
    void testGetFileInfo_NonExistentFile() {
        // Arrange
        String nonExistentPath = tempDir.resolve("nonexistent.txt").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> fileSystemTool.getFileInfo(nonExistentPath));
    }

    // ==================== File Existence Tests ====================

    @Test
    void testCheckFileExists_ExistingFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("exists.txt");
        Files.writeString(testFile, "content");

        // Act
        Builder result = fileSystemTool.checkFileExists(testFile.toString());

        // Assert
        assertEquals(true, result.get("exists"));
        assertEquals(true, result.get("is_file"));
        assertEquals(false, result.get("is_directory"));
    }

    @Test
    void testCheckFileExists_NonExistentFile() {
        // Arrange
        String nonExistentPath = tempDir.resolve("notexists.txt").toString();

        // Act
        Builder result = fileSystemTool.checkFileExists(nonExistentPath);

        // Assert
        assertEquals(false, result.get("exists"));
    }

    // ==================== File Size Tests ====================

    @Test
    void testGetFileSize_RegularFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("sized.txt");
        String content = "This is test content";
        Files.writeString(testFile, content);

        // Act
        Builder result = fileSystemTool.getFileSize(testFile.toString());

        // Assert
        assertEquals(testFile.toString(), result.get("path"));
        assertTrue((Long) result.get("size") > 0);
        assertEquals(false, result.get("is_directory"));
    }

    @Test
    void testGetFileSize_EmptyFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("empty.txt");
        Files.createFile(testFile);

        // Act
        Builder result = fileSystemTool.getFileSize(testFile.toString());

        // Assert
        assertEquals(0L, result.get("size"));
    }

    @Test
    void testGetFileSize_NonExistentFile() {
        // Arrange
        String nonExistentPath = tempDir.resolve("nofile.txt").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> fileSystemTool.getFileSize(nonExistentPath));
    }

    // ==================== Directory Listing Tests ====================

    @Test
    void testListDirectory_NonEmptyDirectory() throws Exception {
        // Arrange
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("file1.txt"), "content1");
        Files.writeString(subDir.resolve("file2.txt"), "content2");

        // Act
        Builder result = fileSystemTool.listDirectory(subDir.toString());

        // Assert
        assertEquals(subDir.toString(), result.get("path"));
        assertEquals(2, result.get("total"));
        assertNotNull(result.get("entries"));
    }

    @Test
    void testListDirectory_EmptyDirectory() throws Exception {
        // Arrange
        Path emptyDir = tempDir.resolve("emptydir");
        Files.createDirectory(emptyDir);

        // Act
        Builder result = fileSystemTool.listDirectory(emptyDir.toString());

        // Assert
        assertEquals(0, result.get("total"));
    }

    @Test
    void testListDirectory_NonExistentDirectory() {
        // Arrange
        String nonExistentPath = tempDir.resolve("nodir").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> fileSystemTool.listDirectory(nonExistentPath));
    }

    @Test
    void testListDirectory_File() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("notadir.txt");
        Files.writeString(testFile, "content");

        // Act & Assert
        assertThrows(MCPException.class, () -> fileSystemTool.listDirectory(testFile.toString()));
    }

    // ==================== File Read Tests ====================

    @Test
    void testReadFile_UTF8Encoding() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("utf8.txt");
        String content = "Hello, World! 你好世界";
        Files.writeString(testFile, content);

        // Act
        Builder result = fileSystemTool.readFile(testFile.toString(), "utf8");

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(content, result.get("content"));
        assertEquals("utf8", result.get("encoding"));
    }

    @Test
    void testReadFile_Base64Encoding() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("base64.txt");
        String content = "Binary content test";
        Files.writeString(testFile, content);

        // Act
        Builder result = fileSystemTool.readFile(testFile.toString(), "base64");

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals("base64", result.get("encoding"));
        String encodedContent = (String) result.get("content");
        String decodedContent = new String(Base64.getDecoder().decode(encodedContent));
        assertEquals(content, decodedContent);
    }

    @Test
    void testReadFile_DefaultEncoding() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("default.txt");
        String content = "Default encoding test";
        Files.writeString(testFile, content);

        // Act
        Builder result = fileSystemTool.readFile(testFile.toString(), null);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(content, result.get("content"));
    }

    @Test
    void testReadFile_NonExistentFile() {
        // Arrange
        String nonExistentPath = tempDir.resolve("noread.txt").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> fileSystemTool.readFile(nonExistentPath, "utf8"));
    }

    @Test
    void testReadFile_Directory() throws Exception {
        // Arrange
        Path testDir = tempDir.resolve("dirread");
        Files.createDirectory(testDir);

        // Act & Assert
        assertThrows(MCPException.class, () -> fileSystemTool.readFile(testDir.toString(), "utf8"));
    }

    // ==================== File Write Tests ====================

    @Test
    void testWriteFile_UTF8Encoding() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("write_utf8.txt");
        String content = "Write test content";

        // Act
        Builder result = fileSystemTool.writeFile(testFile.toString(), content, "utf8", false);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(testFile.toString(), result.get("path"));
        assertEquals(false, result.get("append"));
        String fileContent = Files.readString(testFile);
        assertEquals(content, fileContent);
    }

    @Test
    void testWriteFile_Base64Encoding() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("write_base64.txt");
        String originalContent = "Base64 write test";
        String encodedContent = Base64.getEncoder().encodeToString(originalContent.getBytes());

        // Act
        Builder result = fileSystemTool.writeFile(testFile.toString(), encodedContent, "base64", false);

        // Assert
        assertEquals("success", result.get("status"));
        String fileContent = Files.readString(testFile);
        assertEquals(originalContent, fileContent);
    }

    @Test
    void testWriteFile_AppendMode() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("append.txt");
        Files.writeString(testFile, "First line\n");

        // Act
        Builder result = fileSystemTool.writeFile(testFile.toString(), "Second line", "utf8", true);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(true, result.get("append"));
        String fileContent = Files.readString(testFile);
        assertTrue(fileContent.contains("First line"));
        assertTrue(fileContent.contains("Second line"));
    }

    @Test
    void testWriteFile_OverwriteMode() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("overwrite.txt");
        Files.writeString(testFile, "Old content");

        // Act
        Builder result = fileSystemTool.writeFile(testFile.toString(), "New content", "utf8", false);

        // Assert
        assertEquals("success", result.get("status"));
        String fileContent = Files.readString(testFile);
        assertEquals("New content", fileContent);
        assertFalse(fileContent.contains("Old content"));
    }

    @Test
    void testWriteFile_CreatesParentDirectory() throws Exception {
        // Arrange
        Path nestedFile = tempDir.resolve("nested/dir/file.txt");

        // Act
        Builder result = fileSystemTool.writeFile(nestedFile.toString(), "content", "utf8", false);

        // Assert
        assertEquals("success", result.get("status"));
        assertTrue(Files.exists(nestedFile));
        assertTrue(Files.exists(nestedFile.getParent()));
    }

    // ==================== File Copy Tests ====================

    @Test
    void testCopyFile_Success() throws Exception {
        // Arrange
        Path sourceFile = tempDir.resolve("source.txt");
        Path destFile = tempDir.resolve("dest.txt");
        Files.writeString(sourceFile, "Copy content");

        // Act
        Builder result = fileSystemTool.copyFile(sourceFile.toString(), destFile.toString(), false);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(sourceFile.toString(), result.get("source"));
        assertEquals(destFile.toString(), result.get("destination"));
        assertTrue(Files.exists(destFile));
        assertEquals(Files.readString(sourceFile), Files.readString(destFile));
    }

    @Test
    void testCopyFile_WithOverwrite() throws Exception {
        // Arrange
        Path sourceFile = tempDir.resolve("source2.txt");
        Path destFile = tempDir.resolve("dest2.txt");
        Files.writeString(sourceFile, "New content");
        Files.writeString(destFile, "Old content");

        // Act
        Builder result = fileSystemTool.copyFile(sourceFile.toString(), destFile.toString(), true);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(true, result.get("overwrite"));
        assertEquals("New content", Files.readString(destFile));
    }

    @Test
    void testCopyFile_WithoutOverwriteToExisting() throws Exception {
        // Arrange
        Path sourceFile = tempDir.resolve("source3.txt");
        Path destFile = tempDir.resolve("dest3.txt");
        Files.writeString(sourceFile, "Source");
        Files.writeString(destFile, "Existing");

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            fileSystemTool.copyFile(sourceFile.toString(), destFile.toString(), false));
    }

    @Test
    void testCopyFile_NonExistentSource() {
        // Arrange
        String sourcePath = tempDir.resolve("nosource.txt").toString();
        String destPath = tempDir.resolve("dest.txt").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            fileSystemTool.copyFile(sourcePath, destPath, false));
    }

    // ==================== File Move Tests ====================

    @Test
    void testMoveFile_Success() throws Exception {
        // Arrange
        Path sourceFile = tempDir.resolve("movesource.txt");
        Path destFile = tempDir.resolve("movedest.txt");
        Files.writeString(sourceFile, "Move content");

        // Act
        Builder result = fileSystemTool.moveFile(sourceFile.toString(), destFile.toString(), false);

        // Assert
        assertEquals("success", result.get("status"));
        assertFalse(Files.exists(sourceFile));
        assertTrue(Files.exists(destFile));
        assertEquals("Move content", Files.readString(destFile));
    }

    @Test
    void testMoveFile_WithOverwrite() throws Exception {
        // Arrange
        Path sourceFile = tempDir.resolve("movesource2.txt");
        Path destFile = tempDir.resolve("movedest2.txt");
        Files.writeString(sourceFile, "New content");
        Files.writeString(destFile, "Old content");

        // Act
        Builder result = fileSystemTool.moveFile(sourceFile.toString(), destFile.toString(), true);

        // Assert
        assertEquals("success", result.get("status"));
        assertFalse(Files.exists(sourceFile));
        assertEquals("New content", Files.readString(destFile));
    }

    @Test
    void testMoveFile_NonExistentSource() {
        // Arrange
        String sourcePath = tempDir.resolve("nomovesource.txt").toString();
        String destPath = tempDir.resolve("movedest.txt").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            fileSystemTool.moveFile(sourcePath, destPath, false));
    }

    // ==================== File Delete Tests ====================

    @Test
    void testDeleteFile_RegularFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("delete.txt");
        Files.writeString(testFile, "Delete me");

        // Act
        Builder result = fileSystemTool.deleteFile(testFile.toString(), false);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(true, result.get("deleted"));
        assertFalse(Files.exists(testFile));
    }

    @Test
    void testDeleteFile_EmptyDirectory() throws Exception {
        // Arrange
        Path emptyDir = tempDir.resolve("emptydelete");
        Files.createDirectory(emptyDir);

        // Act
        Builder result = fileSystemTool.deleteFile(emptyDir.toString(), false);

        // Assert
        assertEquals("success", result.get("status"));
        assertFalse(Files.exists(emptyDir));
    }

    @Test
    void testDeleteFile_NonEmptyDirectoryWithRecursive() throws Exception {
        // Arrange
        Path dirToDelete = tempDir.resolve("recursivedelete");
        Files.createDirectory(dirToDelete);
        Files.writeString(dirToDelete.resolve("file1.txt"), "content1");
        Files.createDirectory(dirToDelete.resolve("subdir"));
        Files.writeString(dirToDelete.resolve("subdir/file2.txt"), "content2");

        // Act
        Builder result = fileSystemTool.deleteFile(dirToDelete.toString(), true);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(true, result.get("recursive"));
        assertFalse(Files.exists(dirToDelete));
    }

    @Test
    void testDeleteFile_NonExistentPath() {
        // Arrange
        String nonExistentPath = tempDir.resolve("nodelete.txt").toString();

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            fileSystemTool.deleteFile(nonExistentPath, false));
    }

    // ==================== Directory Creation Tests ====================

    @Test
    void testCreateDirectory_NewDirectory() throws Exception {
        // Arrange
        Path newDir = tempDir.resolve("newdir");

        // Act
        Builder result = fileSystemTool.createDirectory(newDir.toString());

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(true, result.get("created"));
        assertEquals(newDir.toString(), result.get("path"));
        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    void testCreateDirectory_NestedDirectories() throws Exception {
        // Arrange
        Path nestedDir = tempDir.resolve("level1/level2/level3");

        // Act
        Builder result = fileSystemTool.createDirectory(nestedDir.toString());

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(true, result.get("created"));
        assertTrue(Files.exists(nestedDir));
    }

    @Test
    void testCreateDirectory_ExistingDirectory() throws Exception {
        // Arrange
        Path existingDir = tempDir.resolve("existing");
        Files.createDirectory(existingDir);

        // Act
        Builder result = fileSystemTool.createDirectory(existingDir.toString());

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(false, result.get("created"));
        assertEquals(true, result.get("already_exists"));
    }

    @Test
    void testCreateDirectory_WhereFileExists() throws Exception {
        // Arrange
        Path existingFile = tempDir.resolve("file.txt");
        Files.writeString(existingFile, "content");

        // Act & Assert
        assertThrows(MCPException.class, () -> 
            fileSystemTool.createDirectory(existingFile.toString()));
    }
}
