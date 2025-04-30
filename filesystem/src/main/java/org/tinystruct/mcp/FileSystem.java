package org.tinystruct.mcp;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.http.Request;
import org.tinystruct.http.Response;
import org.tinystruct.http.ResponseStatus;
import org.tinystruct.system.annotation.Action;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.tinystruct.mcp.MCPSpecification.ErrorCodes;
import static org.tinystruct.mcp.MCPSpecification.Http;

/**
 * FileSystem MCP Application that provides file system operations
 * Implements file operations like list, read, write, copy, move, delete
 * Supports Server-Sent Events (SSE) for real-time file system notifications
 */
public class FileSystem extends MCPApplication {
    private static final Logger LOGGER = Logger.getLogger(FileSystem.class.getName());

    // FileSystem MCP specific constants
    private static final String FILESYSTEM_PROTOCOL_VERSION = "1.0.0";
    private static final String[] FILESYSTEM_FEATURES = {
            "base", "lifecycle", "resources", "filesystem",
            "fs.list", "fs.read", "fs.write", "fs.copy",
            "fs.move", "fs.delete", "fs.info", "fs.mkdir"
    };

    // SSE Event Types
    private static final String EVENT_FILE_CREATED = "file_created";
    private static final String EVENT_FILE_DELETED = "file_deleted";
    private static final String EVENT_FILE_MODIFIED = "file_modified";
    private static final String EVENT_DIRECTORY_CREATED = "directory_created";
    private static final String EVENT_DIRECTORY_DELETED = "directory_deleted";

    @Override
    public void init() {
        super.init();
        LOGGER.info("FileSystem MCP Application initialized");
    }

    @Override
    public String version() {
        return FILESYSTEM_PROTOCOL_VERSION;
    }

    /**
     * Handles JSON-RPC requests for file system operations
     */
    @Action("filesystem/rpc")
    public String handleFileSystemRpcRequest(Request request, Response response) throws ApplicationException {
        try {
            // Validate authentication using parent class's auth handler
            authHandler.validateAuthHeader(request);

            // Parse the JSON-RPC request
            String jsonStr = request.body();
            LOGGER.fine("Received JSON: " + jsonStr);

            // Add batch request support
            if (jsonStr.trim().startsWith("[")) {
                return jsonRpcHandler.handleBatchRequest(jsonStr, response, this::handleFileSystemMethod);
            }

            if (!jsonRpcHandler.validateJsonRpcRequest(jsonStr)) {
                response.setStatus(ResponseStatus.BAD_REQUEST);
                return jsonRpcHandler.createErrorResponse("Invalid JSON-RPC request", ErrorCodes.INVALID_REQUEST);
            }

            JsonRpcRequest rpcRequest = new JsonRpcRequest();
            rpcRequest.parse(jsonStr);

            JsonRpcResponse jsonResponse = new JsonRpcResponse();
            handleFileSystemMethod(rpcRequest, jsonResponse);

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

    private void handleFileSystemMethod(JsonRpcRequest request, JsonRpcResponse response) {
        String method = request.getMethod();
        switch (method) {
            case "fs.list":
                handleFsList(request, response);
                break;
            case "fs.read":
                handleFsRead(request, response);
                break;
            case "fs.write":
                handleFsWrite(request, response);
                break;
            case "fs.copy":
                handleFsCopy(request, response);
                break;
            case "fs.move":
                handleFsMove(request, response);
                break;
            case "fs.delete":
                handleFsDelete(request, response);
                break;
            case "fs.info":
                handleFsInfo(request, response);
                break;
            case "fs.mkdir":
                handleFsMkdir(request, response);
                break;
            default:
                response.setError(new JsonRpcError(ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + method));
        }
    }

    private void handleFsList(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.list request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("path")) {
            LOGGER.warning("Invalid params: path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: path required"));
            return;
        }

        String path = params.get("path").toString();

        LOGGER.info("Listing directory: " + path);

        try {
            File directory = new File(path);
            if (!directory.exists()) {
                LOGGER.warning("Directory does not exist: " + path);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Directory does not exist: " + path));
                return;
            }

            if (!directory.isDirectory()) {
                LOGGER.warning("Path is not a directory: " + path);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Path is not a directory: " + path));
                return;
            }

            File[] files = directory.listFiles();

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("path", path);

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

            responseResult.put("entries", entries);
            responseResult.put("total", entries.size());

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Successfully listed directory with " + entries.size() + " entries");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list directory: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to list directory: " + e.getMessage()));
        }
    }

    private void handleFsRead(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.read request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("path")) {
            LOGGER.warning("Invalid params: path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: path required"));
            return;
        }

        String path = params.get("path").toString();
        String encoding = params.containsKey("encoding") ? params.get("encoding").toString() : "utf8";

        LOGGER.info("Reading file: " + path + " with encoding: " + encoding);

        try {
            File file = new File(path);
            if (!file.exists()) {
                LOGGER.warning("File does not exist: " + path);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "File does not exist: " + path));
                return;
            }

            if (file.isDirectory()) {
                LOGGER.warning("Path is a directory, not a file: " + path);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Path is a directory, not a file: " + path));
                return;
            }

            byte[] fileContent = Files.readAllBytes(file.toPath());
            String content;

            if ("base64".equalsIgnoreCase(encoding)) {
                content = Base64.getEncoder().encodeToString(fileContent);
            } else {
                // Default to UTF-8
                content = new String(fileContent, "UTF-8");
            }

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("path", path);
            responseResult.put("encoding", encoding);
            responseResult.put("size", fileContent.length);
            responseResult.put("content", content);

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Successfully read file: " + path);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to read file: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to read file: " + e.getMessage()));
        }
    }

    private void handleFsWrite(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.write request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("path") || !params.containsKey("content")) {
            LOGGER.warning("Invalid params: path and content required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: path and content required"));
            return;
        }

        String path = params.get("path").toString();
        String content = params.get("content").toString();
        String encoding = params.containsKey("encoding") ? params.get("encoding").toString() : "utf8";
        boolean append = params.containsKey("append") && Boolean.parseBoolean(params.get("append").toString());

        LOGGER.info("Writing to file: " + path + " with encoding: " + encoding + ", append: " + append);

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

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("path", path);
            responseResult.put("size", data.length);
            responseResult.put("append", append);

            response.setId(request.getId());
            response.setResult(responseResult);

            // Broadcast file modification event
            String eventData = new Builder()
                    .put("path", path)
                    .put("size", data.length)
                    .put("append", append)
                    .toString();
            sseHandler.broadcastEvent(EVENT_FILE_MODIFIED, eventData);

            LOGGER.info("Successfully wrote to file: " + path);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to write file: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to write file: " + e.getMessage()));
        }
    }

    private void handleFsCopy(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.copy request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("source") || !params.containsKey("destination")) {
            LOGGER.warning("Invalid params: source and destination required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: source and destination required"));
            return;
        }

        String source = params.get("source").toString();
        String destination = params.get("destination").toString();
        boolean overwrite = params.containsKey("overwrite") && Boolean.parseBoolean(params.get("overwrite").toString());

        LOGGER.info("Copying from " + source + " to " + destination + ", overwrite: " + overwrite);

        try {
            Path sourcePath = Paths.get(source);
            Path destPath = Paths.get(destination);

            if (!Files.exists(sourcePath)) {
                LOGGER.warning("Source does not exist: " + source);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Source does not exist: " + source));
                return;
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

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("source", source);
            responseResult.put("destination", destination);
            responseResult.put("overwrite", overwrite);

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Successfully copied from " + source + " to " + destination);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to copy: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during copy: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleFsMove(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.move request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("source") || !params.containsKey("destination")) {
            LOGGER.warning("Invalid params: source and destination required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: source and destination required"));
            return;
        }

        String source = params.get("source").toString();
        String destination = params.get("destination").toString();
        boolean overwrite = params.containsKey("overwrite") && Boolean.parseBoolean(params.get("overwrite").toString());

        LOGGER.info("Moving from " + source + " to " + destination + ", overwrite: " + overwrite);

        try {
            Path sourcePath = Paths.get(source);
            Path destPath = Paths.get(destination);

            if (!Files.exists(sourcePath)) {
                LOGGER.warning("Source does not exist: " + source);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Source does not exist: " + source));
                return;
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

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("source", source);
            responseResult.put("destination", destination);
            responseResult.put("overwrite", overwrite);

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Successfully moved from " + source + " to " + destination);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to move: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to move: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during move: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleFsDelete(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.delete request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("path")) {
            LOGGER.warning("Invalid params: path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: path required"));
            return;
        }

        String path = params.get("path").toString();
        boolean recursive = params.containsKey("recursive") && Boolean.parseBoolean(params.get("recursive").toString());

        LOGGER.info("Deleting: " + path + ", recursive: " + recursive);

        try {
            Path filePath = Paths.get(path);

            if (!Files.exists(filePath)) {
                LOGGER.warning("Path does not exist: " + path);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Path does not exist: " + path));
                return;
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

            // Prepare response
            Builder responseResult = new Builder();
            responseResult.put("status", success ? "success" : "error");
            responseResult.put("path", path);
            responseResult.put("recursive", recursive);
            responseResult.put("deleted", success);

            response.setId(request.getId());
            response.setResult(responseResult);

            // Broadcast deletion event
            if (success) {
                String eventData = new Builder()
                        .put("path", path)
                        .put("is_directory", isDirectory)
                        .put("recursive", recursive)
                        .toString();
                sseHandler.broadcastEvent(isDirectory ? EVENT_DIRECTORY_DELETED : EVENT_FILE_DELETED, eventData);
            }

            LOGGER.info("Delete operation completed with status: " + success);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to delete: " + e.getMessage()));
        }
    }

    private void handleFsInfo(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.info request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("path")) {
            LOGGER.warning("Invalid params: path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: path required"));
            return;
        }

        String path = params.get("path").toString();

        LOGGER.info("Getting info for: " + path);

        try {
            Path filePath = Paths.get(path);

            if (!Files.exists(filePath)) {
                LOGGER.warning("Path does not exist: " + path);
                response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Path does not exist: " + path));
                return;
            }

            File file = filePath.toFile();
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

            // Prepare detailed file information
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("path", path);
            responseResult.put("name", file.getName());
            responseResult.put("is_directory", file.isDirectory());
            responseResult.put("is_file", file.isFile());
            responseResult.put("size", file.length());
            responseResult.put("last_modified", attrs.lastModifiedTime().toMillis());
            responseResult.put("creation_time", attrs.creationTime().toMillis());
            responseResult.put("last_access_time", attrs.lastAccessTime().toMillis());
            responseResult.put("is_symbolic_link", attrs.isSymbolicLink());
            responseResult.put("is_hidden", file.isHidden());
            responseResult.put("can_read", file.canRead());
            responseResult.put("can_write", file.canWrite());
            responseResult.put("can_execute", file.canExecute());

            response.setId(request.getId());
            response.setResult(responseResult);

            LOGGER.info("Successfully retrieved info for: " + path);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to get file info: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to get file info: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error getting file info: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Unexpected error: " + e.getMessage()));
        }
    }

    private void handleFsMkdir(JsonRpcRequest request, JsonRpcResponse response) {
        LOGGER.info("Received fs.mkdir request with ID: " + request.getId());
        Builder params = request.getParams();
        if (params == null || !params.containsKey("path")) {
            LOGGER.warning("Invalid params: path required");
            response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Invalid params: path required"));
            return;
        }

        String path = params.get("path").toString();

        LOGGER.info("Creating directory: " + path);

        try {
            Path dirPath = Paths.get(path);

            if (Files.exists(dirPath)) {
                if (Files.isDirectory(dirPath)) {
                    LOGGER.warning("Directory already exists: " + path);

                    // Return success but indicate directory already existed
                    Builder responseResult = new Builder();
                    responseResult.put("status", "success");
                    responseResult.put("path", path);
                    responseResult.put("created", false);
                    responseResult.put("already_exists", true);

                    response.setId(request.getId());
                    response.setResult(responseResult);
                    return;
                } else {
                    LOGGER.warning("Path exists but is not a directory: " + path);
                    response.setError(new JsonRpcError(ErrorCodes.INVALID_PARAMS, "Path exists but is not a directory: " + path));
                    return;
                }
            }

            // Create directory and any necessary parent directories
            Files.createDirectories(dirPath);

            // Prepare success response
            Builder responseResult = new Builder();
            responseResult.put("status", "success");
            responseResult.put("path", path);
            responseResult.put("created", true);

            response.setId(request.getId());
            response.setResult(responseResult);

            // Broadcast directory creation event
            String eventData = new Builder()
                    .put("path", path)
                    .toString();
            sseHandler.broadcastEvent(EVENT_DIRECTORY_CREATED, eventData);

            LOGGER.info("Successfully created directory: " + path);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create directory: " + e.getMessage(), e);
            response.setError(new JsonRpcError(ErrorCodes.INTERNAL_ERROR, "Failed to create directory: " + e.getMessage()));
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

    /**
     * Inner class FileSystemTool for performing file system operations.
     */
    public static class FileSystemTool extends MCPTool {
        private static final Logger LOGGER = Logger.getLogger(FileSystemTool.class.getName());

        public FileSystemTool(String name, String description, Builder schema, MCPClient client) {
            super(name, description, schema, client);
        }

        public FileSystemTool(String name, String description, Builder schema, MCPClient client, boolean supportsLocalExecution) {
            super(name, description, schema, client, supportsLocalExecution);
        }

        @Override
        public String getName() {
            return "filesystem";
        }

        @Override
        public String getDescription() {
            return "Tool for performing file system operations";
        }

        @Override
        public Builder getSchema() {
            Builder schema = new Builder();
            Builder properties = new Builder();

            // Operation parameter
            Builder operation = new Builder();
            operation.put("type", "string");
            operation.put("description", "The operation to perform (info, exists, size, list, read, write, copy, move, delete, mkdir)");
            operation.put("enum", new String[]{"info", "exists", "size", "list", "read", "write", "copy", "move", "delete", "mkdir"});

            // Path parameter
            Builder path = new Builder();
            path.put("type", "string");
            path.put("description", "The file or directory path");

            // Content parameter for write operation
            Builder content = new Builder();
            content.put("type", "string");
            content.put("description", "The content to write to the file");

            // Encoding parameter
            Builder encoding = new Builder();
            encoding.put("type", "string");
            encoding.put("description", "The encoding to use (utf8 or base64)");
            encoding.put("enum", new String[]{"utf8", "base64"});

            // Append parameter for write operation
            Builder append = new Builder();
            append.put("type", "boolean");
            append.put("description", "Whether to append to the file or overwrite it");

            // Source parameter for copy/move operations
            Builder source = new Builder();
            source.put("type", "string");
            source.put("description", "The source file or directory path");

            // Destination parameter for copy/move operations
            Builder destination = new Builder();
            destination.put("type", "string");
            destination.put("description", "The destination file or directory path");

            // Overwrite parameter for copy/move operations
            Builder overwrite = new Builder();
            overwrite.put("type", "boolean");
            overwrite.put("description", "Whether to overwrite existing files");

            // Recursive parameter for delete operation
            Builder recursive = new Builder();
            recursive.put("type", "boolean");
            recursive.put("description", "Whether to delete directories recursively");

            properties.put("operation", operation);
            properties.put("path", path);
            properties.put("content", content);
            properties.put("encoding", encoding);
            properties.put("append", append);
            properties.put("source", source);
            properties.put("destination", destination);
            properties.put("overwrite", overwrite);
            properties.put("recursive", recursive);

            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", new String[]{"operation", "path"});

            return schema;
        }

        @Override
        public Object execute(Builder params) throws MCPException {
            if (params == null || !params.containsKey("operation") || !params.containsKey("path")) {
                throw new MCPException("Missing required parameters: operation and path");
            }

            String operation = params.get("operation").toString();
            String path = params.get("path").toString();

            LOGGER.info("Executing filesystem tool with operation: " + operation + " on path: " + path);

            try {
                switch (operation) {
                    case "info":
                        return getFileInfo(path);
                    case "exists":
                        return checkFileExists(path);
                    case "size":
                        return getFileSize(path);
                    case "list":
                        return listDirectory(path);
                    case "read":
                        String readEncoding = params.containsKey("encoding") ? params.get("encoding").toString() : "utf8";
                        return readFile(path, readEncoding);
                    case "write":
                        if (!params.containsKey("content")) {
                            throw new MCPException("Missing required parameter: content");
                        }
                        String content = params.get("content").toString();
                        String writeEncoding = params.containsKey("encoding") ? params.get("encoding").toString() : "utf8";
                        boolean append = params.containsKey("append") && Boolean.parseBoolean(params.get("append").toString());
                        return writeFile(path, content, writeEncoding, append);
                    case "copy":
                        if (!params.containsKey("source") || !params.containsKey("destination")) {
                            throw new MCPException("Missing required parameters: source and destination");
                        }
                        String source = params.get("source").toString();
                        String destination = params.get("destination").toString();
                        boolean copyOverwrite = params.containsKey("overwrite") && Boolean.parseBoolean(params.get("overwrite").toString());
                        return copyFile(source, destination, copyOverwrite);
                    case "move":
                        if (!params.containsKey("source") || !params.containsKey("destination")) {
                            throw new MCPException("Missing required parameters: source and destination");
                        }
                        String moveSource = params.get("source").toString();
                        String moveDestination = params.get("destination").toString();
                        boolean moveOverwrite = params.containsKey("overwrite") && Boolean.parseBoolean(params.get("overwrite").toString());
                        return moveFile(moveSource, moveDestination, moveOverwrite);
                    case "delete":
                        boolean recursive = params.containsKey("recursive") && Boolean.parseBoolean(params.get("recursive").toString());
                        return deleteFile(path, recursive);
                    case "mkdir":
                        return createDirectory(path);
                    default:
                        throw new MCPException("Unsupported operation: " + operation);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing filesystem tool: " + e.getMessage(), e);
                throw new MCPException("Error executing filesystem tool: " + e.getMessage());
            }
        }

        private Builder getFileInfo(String path) throws Exception {
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

        private Builder checkFileExists(String path) {
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

        private Builder getFileSize(String path) throws MCPException {
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

        private Builder listDirectory(String path) throws MCPException {
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

        private Builder readFile(String path, String encoding) throws MCPException {
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

        private Builder writeFile(String path, String content, String encoding, boolean append) throws MCPException {
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

        private Builder copyFile(String source, String destination, boolean overwrite) throws MCPException {
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

        private Builder moveFile(String source, String destination, boolean overwrite) throws MCPException {
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

        private Builder deleteFile(String path, boolean recursive) throws MCPException {
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

        private Builder createDirectory(String path) throws MCPException {
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