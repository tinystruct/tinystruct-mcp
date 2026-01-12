package org.tinystruct.mcp;

import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

// Reference: see GitHub.java and SampleMCPServerApplication.java for the modern MCPServerApplication pattern
public class FileSystem extends MCPServer {

    // FileSystem MCP specific constants
    private static final String FILESYSTEM_PROTOCOL_VERSION = "1.0.0";

    @Override
    public void init() {
        super.init();

        // Register FileSystem tool methods as individual tools
        FileSystemTool fsTool = new FileSystemTool();
        this.registerTool(fsTool);

        // Register a sample prompt (can be customized for FileSystem context)
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
            "Hello, {{name}}! Welcome to the FileSystem MCP server.",
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
        return FILESYSTEM_PROTOCOL_VERSION;
    }

    /**
     * Inner class FileSystemTool for performing file system operations.
     */
    public static class FileSystemTool extends MCPTool {
        private static final Logger LOGGER = Logger.getLogger(FileSystemTool.class.getName());

        /**
         * Constructs a new FileSystemTool with local execution support.
         */
        public FileSystemTool() {
            // Note the true parameter at the end to enable local execution
            super("filesystem", "Tool for performing file system operations");
        }

        /**
         * Constructs a new FileSystemTool with a client.
         *
         * @param client The MCP client
         */
        public FileSystemTool(MCPClient client) {
            // Note the true parameter at the end to enable local execution
            super("filesystem", "Tool for performing file system operations", null, client, true);
        }

        /**
         * Gets information about a file or directory.
         * @param path The file or directory path
         * @return File information
         * @throws MCPException If getting info fails
         */
        @Action(value = "filesystem/info", description = "Get information about a file or directory", arguments = {
                @Argument(key = "path", description = "The file or directory path", type = "string")
        })
        public Builder getFileInfo(String path) throws MCPException {
            try {
                return getFileInfoInternal(path);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error getting file info: " + e.getMessage(), e);
                throw new MCPException("Error getting file info: " + e.getMessage());
            }
        }

        /**
         * Checks if a file or directory exists.
         * @param path The file or directory path
         * @return Existence status
         */
        @Action(value = "filesystem/exists", description = "Check if a file or directory exists", arguments = {
                @Argument(key = "path", description = "The file or directory path", type = "string")
        })
        public Builder checkFileExists(String path) {
            return checkFileExistsInternal(path);
        }

        /**
         * Gets the size of a file.
         * @param path The file path
         * @return File size information
         * @throws MCPException If getting size fails
         */
        @Action(value = "filesystem/size", description = "Get the size of a file", arguments = {
                @Argument(key = "path", description = "The file path", type = "string")
        })
        public Builder getFileSize(String path) throws MCPException {
            try {
                return getFileSizeInternal(path);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error getting file size: " + e.getMessage(), e);
                throw new MCPException("Error getting file size: " + e.getMessage());
            }
        }

        /**
         * Lists the contents of a directory.
         * @param path The directory path
         * @return Directory contents
         * @throws MCPException If listing fails
         */
        @Action(value = "filesystem/list", description = "List the contents of a directory", arguments = {
                @Argument(key = "path", description = "The directory path", type = "string")
        })
        public Builder listDirectory(String path) throws MCPException {
            try {
                return listDirectoryInternal(path);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error listing directory: " + e.getMessage(), e);
                throw new MCPException("Error listing directory: " + e.getMessage());
            }
        }

        /**
         * Reads the contents of a file.
         * @param path The file path
         * @param encoding The encoding to use (utf8 or base64)
         * @return File contents
         * @throws MCPException If reading fails
         */
        @Action(value = "filesystem/read", description = "Read the contents of a file", arguments = {
                @Argument(key = "path", description = "The file path", type = "string"),
                @Argument(key = "encoding", description = "The encoding to use", type = "string")
        })
        public Builder readFile(String path, String encoding) throws MCPException {
            try {
                return readFileInternal(path, encoding != null ? encoding : "utf8");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error reading file: " + e.getMessage(), e);
                throw new MCPException("Error reading file: " + e.getMessage());
            }
        }

        /**
         * Writes content to a file.
         * @param path The file path
         * @param content The content to write
         * @param encoding The encoding to use (utf8 or base64)
         * @param append Whether to append to the file or overwrite it
         * @return Write operation result
         * @throws MCPException If writing fails
         */
        @Action(value = "filesystem/write", description = "Write content to a file", arguments = {
                @Argument(key = "path", description = "The file path", type = "string"),
                @Argument(key = "content", description = "The content to write", type = "string"),
                @Argument(key = "encoding", description = "The encoding to use", type = "string"),
                @Argument(key = "append", description = "Whether to append to the file", type = "boolean")
        })
        public Builder writeFile(String path, String content, String encoding, boolean append) throws MCPException {
            try {
                return writeFileInternal(path, content, encoding != null ? encoding : "utf8", append);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error writing file: " + e.getMessage(), e);
                throw new MCPException("Error writing file: " + e.getMessage());
            }
        }

        /**
         * Copies a file or directory.
         * @param source The source path
         * @param destination The destination path
         * @param overwrite Whether to overwrite existing files
         * @return Copy operation result
         * @throws MCPException If copying fails
         */
        @Action(value = "filesystem/copy", description = "Copy a file or directory", arguments = {
                @Argument(key = "source", description = "The source path", type = "string"),
                @Argument(key = "destination", description = "The destination path", type = "string"),
                @Argument(key = "overwrite", description = "Whether to overwrite existing files", type = "boolean")
        })
        public Builder copyFile(String source, String destination, boolean overwrite) throws MCPException {
            try {
                return copyFileInternal(source, destination, overwrite);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error copying file: " + e.getMessage(), e);
                throw new MCPException("Error copying file: " + e.getMessage());
            }
        }

        /**
         * Moves a file or directory.
         * @param source The source path
         * @param destination The destination path
         * @param overwrite Whether to overwrite existing files
         * @return Move operation result
         * @throws MCPException If moving fails
         */
        @Action(value = "filesystem/move", description = "Move a file or directory", arguments = {
                @Argument(key = "source", description = "The source path", type = "string"),
                @Argument(key = "destination", description = "The destination path", type = "string"),
                @Argument(key = "overwrite", description = "Whether to overwrite existing files", type = "boolean")
        })
        public Builder moveFile(String source, String destination, boolean overwrite) throws MCPException {
            try {
                return moveFileInternal(source, destination, overwrite);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error moving file: " + e.getMessage(), e);
                throw new MCPException("Error moving file: " + e.getMessage());
            }
        }

        /**
         * Deletes a file or directory.
         * @param path The file or directory path
         * @param recursive Whether to delete directories recursively
         * @return Delete operation result
         * @throws MCPException If deletion fails
         */
        @Action(value = "filesystem/delete", description = "Delete a file or directory", arguments = {
                @Argument(key = "path", description = "The file or directory path", type = "string"),
                @Argument(key = "recursive", description = "Whether to delete directories recursively", type = "boolean")
        })
        public Builder deleteFile(String path, boolean recursive) throws MCPException {
            try {
                return deleteFileInternal(path, recursive);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error deleting file: " + e.getMessage(), e);
                throw new MCPException("Error deleting file: " + e.getMessage());
            }
        }

        /**
         * Creates a directory.
         * @param path The directory path
         * @return Directory creation result
         * @throws MCPException If creation fails
         */
        @Action(value = "filesystem/mkdir", description = "Create a directory", arguments = {
                @Argument(key = "path", description = "The directory path", type = "string")
        })
        public Builder createDirectory(String path) throws MCPException {
            try {
                return createDirectoryInternal(path);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error creating directory: " + e.getMessage(), e);
                throw new MCPException("Error creating directory: " + e.getMessage());
            }
        }

        private Builder getFileInfoInternal(String path) throws Exception {
            File file = new File(path);
            if (!file.exists()) {
                throw new MCPException("File does not exist: " + path);
            }

            Path filePath = Paths.get(path);
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

            Builder info = new Builder();
            info.put("path", path);
            info.put("name", file.getName());
            info.put("is_directory", file.isDirectory());
            info.put("is_file", file.isFile());
            info.put("size", file.length());
            info.put("last_modified", attrs.lastModifiedTime().toMillis());
            info.put("creation_time", attrs.creationTime().toMillis());
            info.put("last_access_time", attrs.lastAccessTime().toMillis());
            info.put("is_symbolic_link", attrs.isSymbolicLink());
            info.put("is_hidden", file.isHidden());
            info.put("can_read", file.canRead());
            info.put("can_write", file.canWrite());
            info.put("can_execute", file.canExecute());

            return info;
        }

        private Builder checkFileExistsInternal(String path) {
            File file = new File(path);
            Builder result = new Builder();
            result.put("path", path);
            result.put("exists", file.exists());
            if (file.exists()) {
                result.put("is_directory", file.isDirectory());
                result.put("is_file", file.isFile());
            }
            return result;
        }

        private Builder getFileSizeInternal(String path) throws MCPException {
            File file = new File(path);
            if (!file.exists()) {
                throw new MCPException("File does not exist: " + path);
            }

            Builder result = new Builder();
            result.put("path", path);
            result.put("size", file.length());
            result.put("is_directory", file.isDirectory());

            return result;
        }

        private Builder listDirectoryInternal(String path) throws MCPException {
            File directory = new File(path);
            if (!directory.exists()) {
                throw new MCPException("Directory does not exist: " + path);
            }

            if (!directory.isDirectory()) {
                throw new MCPException("Path is not a directory: " + path);
            }

            File[] files = directory.listFiles();
            Builder result = new Builder();
            result.put("path", path);

            Builder entries = new Builder();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    Builder entry = new Builder();
                    entry.put("name", file.getName());
                    entry.put("path", file.getAbsolutePath());
                    entry.put("is_directory", file.isDirectory());
                    entry.put("size", file.length());
                    entry.put("last_modified", file.lastModified());

                    entries.put(String.valueOf(i), entry);
                }
            }

            result.put("entries", entries);
            result.put("total", entries.size());

            return result;
        }

        private Builder readFileInternal(String path, String encoding) throws MCPException {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    throw new MCPException("File does not exist: " + path);
                }

                if (file.isDirectory()) {
                    throw new MCPException("Path is a directory, not a file: " + path);
                }

                byte[] fileContent = Files.readAllBytes(file.toPath());
                String content;

                if ("base64".equalsIgnoreCase(encoding)) {
                    content = Base64.getEncoder().encodeToString(fileContent);
                } else {
                    // Default to UTF-8
                    content = new String(fileContent, "UTF-8");
                }

                Builder result = new Builder();
                result.put("status", "success");
                result.put("path", path);
                result.put("encoding", encoding);
                result.put("size", fileContent.length);
                result.put("content", content);

                LOGGER.info("Successfully read file: " + path);
                return result;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to read file: " + e.getMessage(), e);
                throw new MCPException("Failed to read file: " + e.getMessage());
            }
        }

        private Builder writeFileInternal(String path, String content, String encoding, boolean append) throws MCPException {
            try {
                File file = new File(path);

                // Create parent directories if they don't exist
                if (!file.exists() && file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }

                byte[] data;
                if ("base64".equalsIgnoreCase(encoding)) {
                    data = Base64.getDecoder().decode(content);
                } else {
                    // Default to UTF-8
                    data = content.getBytes("UTF-8");
                }

                // Write the file
                if (append && file.exists()) {
                    Files.write(file.toPath(), data, StandardOpenOption.APPEND);
                } else {
                    Files.write(file.toPath(), data);
                }

                Builder result = new Builder();
                result.put("status", "success");
                result.put("path", path);
                result.put("size", data.length);
                result.put("append", append);

                LOGGER.info("Successfully wrote to file: " + path);
                return result;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to write file: " + e.getMessage(), e);
                throw new MCPException("Failed to write file: " + e.getMessage());
            }
        }

        private Builder copyFileInternal(String source, String destination, boolean overwrite) throws MCPException {
            try {
                Path sourcePath = Paths.get(source);
                Path destPath = Paths.get(destination);

                if (!Files.exists(sourcePath)) {
                    throw new MCPException("Source does not exist: " + source);
                }

                // Create parent directories if they don't exist
                if (destPath.getParent() != null) {
                    Files.createDirectories(destPath.getParent());
                }

                // Copy options
                if (overwrite) {
                    Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(sourcePath, destPath);
                }

                Builder result = new Builder();
                result.put("status", "success");
                result.put("source", source);
                result.put("destination", destination);
                result.put("overwrite", overwrite);

                LOGGER.info("Successfully copied from " + source + " to " + destination);
                return result;

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to copy: " + e.getMessage(), e);
                throw new MCPException("Failed to copy: " + e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error during copy: " + e.getMessage(), e);
                throw new MCPException("Unexpected error: " + e.getMessage());
            }
        }

        private Builder moveFileInternal(String source, String destination, boolean overwrite) throws MCPException {
            try {
                Path sourcePath = Paths.get(source);
                Path destPath = Paths.get(destination);

                if (!Files.exists(sourcePath)) {
                    throw new MCPException("Source does not exist: " + source);
                }

                // Create parent directories if they don't exist
                if (destPath.getParent() != null) {
                    Files.createDirectories(destPath.getParent());
                }

                // Move options
                if (overwrite) {
                    Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(sourcePath, destPath);
                }

                Builder result = new Builder();
                result.put("status", "success");
                result.put("source", source);
                result.put("destination", destination);
                result.put("overwrite", overwrite);

                LOGGER.info("Successfully moved from " + source + " to " + destination);
                return result;

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to move: " + e.getMessage(), e);
                throw new MCPException("Failed to move: " + e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error during move: " + e.getMessage(), e);
                throw new MCPException("Unexpected error: " + e.getMessage());
            }
        }

        private Builder deleteFileInternal(String path, boolean recursive) throws MCPException {
            try {
                Path filePath = Paths.get(path);

                if (!Files.exists(filePath)) {
                    throw new MCPException("Path does not exist: " + path);
                }

                boolean isDirectory = Files.isDirectory(filePath);
                boolean success;
                if (recursive && isDirectory) {
                    // Delete directory and its contents recursively
                    success = deleteRecursively(filePath.toFile());
                } else {
                    // Delete single file or empty directory
                    success = Files.deleteIfExists(filePath);
                }

                Builder result = new Builder();
                result.put("status", success ? "success" : "error");
                result.put("path", path);
                result.put("recursive", recursive);
                result.put("deleted", success);

                LOGGER.info("Delete operation completed with status: " + success);
                return result;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to delete: " + e.getMessage(), e);
                throw new MCPException("Failed to delete: " + e.getMessage());
            }
        }

        private Builder createDirectoryInternal(String path) throws MCPException {
            try {
                Path dirPath = Paths.get(path);

                if (Files.exists(dirPath)) {
                    if (Files.isDirectory(dirPath)) {
                        LOGGER.warning("Directory already exists: " + path);

                        // Return success but indicate directory already existed
                        Builder result = new Builder();
                        result.put("status", "success");
                        result.put("path", path);
                        result.put("created", false);
                        result.put("already_exists", true);
                        return result;
                    } else {
                        throw new MCPException("Path exists but is not a directory: " + path);
                    }
                }

                // Create directory and any necessary parent directories
                Files.createDirectories(dirPath);

                Builder result = new Builder();
                result.put("status", "success");
                result.put("path", path);
                result.put("created", true);

                LOGGER.info("Successfully created directory: " + path);
                return result;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to create directory: " + e.getMessage(), e);
                throw new MCPException("Failed to create directory: " + e.getMessage());
            }
        }

        private boolean deleteRecursively(File file) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File child : files) {
                        deleteRecursively(child);
                    }
                }
            }
            return file.delete();
        }
    }
}