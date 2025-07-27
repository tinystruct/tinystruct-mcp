# Developing MCP Tools for tinystruct-mcp

This guide explains how to extend the tinystruct-mcp server by developing your own MCP tools using the modern `@Action` annotation pattern.

---

## 1. Create a Custom Tool

To add new functionality, create a class that extends `MCPTool` and use `@Action` annotations for each operation:

```java
import org.tinystruct.mcp.MCPTool;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

public class EchoTool extends MCPTool {
    
    /**
     * Constructs a new EchoTool with local execution support.
     */
    public EchoTool() {
        // Note the true parameter at the end to enable local execution
        super("echo", "A tool that echoes back input");
    }

    /**
     * Constructs a new EchoTool with a client.
     *
     * @param client The MCP client
     */
    public EchoTool(MCPClient client) {
        // Note the true parameter at the end to enable local execution
        super("echo", "A tool that echoes back input", null, client, true);
    }

    /**
     * Echoes back the input message.
     * @param message The message to echo
     * @return The echoed message
     */
    @Action(value = "echo/message", description = "Echo back the input message", arguments = {
            @Argument(key = "message", description = "The message to echo", type = "string")
    })
    public String echoMessage(String message) {
        return message;
    }

    /**
     * Echoes back the input with a prefix.
     * @param message The message to echo
     * @param prefix The prefix to add
     * @return The prefixed message
     */
    @Action(value = "echo/with-prefix", description = "Echo back the input with a prefix", arguments = {
            @Argument(key = "message", description = "The message to echo", type = "string"),
            @Argument(key = "prefix", description = "The prefix to add", type = "string")
    })
    public String echoWithPrefix(String message, String prefix) {
        return prefix + ": " + message;
    }
}
```

---

## 2. Register Your Tool in the Server

In your server class (extending `MCPServerApplication`), register your tool in the `init()` method:

```java
public class MyMCPServer extends MCPServerApplication {
    @Override
    public void init() {
        super.init();
        this.registerToolMethods(new EchoTool());
        // Register other tools or prompts as needed
    }
}
```

---

## 3. Key Features of Modern MCP Tools

### Constructor Pattern
- **Default constructor**: `super("tool-name", "Tool description")` enables local execution
- **Client constructor**: `super("tool-name", "Tool description", null, client, true)` for client-based execution

### @Action Annotations
- **Automatic schema generation**: No need to manually build schemas
- **Method-based operations**: Each `@Action` method becomes a separate tool operation
- **Parameter validation**: `@Argument` annotations define parameter types and descriptions

### Automatic Features
- **Name and description**: Set in constructor, no need to override `getName()` or `getDescription()`
- **Schema generation**: Built automatically from `@Action` and `@Argument` annotations
- **Local execution**: Enabled by default for better performance

---

## 4. Add Custom Prompts (Optional)

You can also register prompts for user interaction or automation:

```java
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
    "A greeting prompt",
    "Hello, {{name}}!",
    promptSchema,
    null
) {
    @Override
    protected boolean supportsLocalExecution() { return true; }
};
this.registerPrompt(greetingPrompt);
```

---

## 5. Best Practices

### Tool Design
- **Single responsibility**: Each tool should focus on one domain (e.g., file operations, Git operations)
- **Consistent naming**: Use `tool-name/operation` format for action values
- **Clear descriptions**: Provide helpful descriptions for tools and arguments
- **Error handling**: Wrap internal operations in try-catch blocks

### Method Structure
```java
@Action(value = "tool/operation", description = "What this operation does", arguments = {
        @Argument(key = "param1", description = "Description of param1", type = "string"),
        @Argument(key = "param2", description = "Description of param2", type = "number")
})
public ReturnType operationName(String param1, int param2) throws MCPException {
    try {
        // Implementation logic here
        return result;
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Error in operation: " + e.getMessage(), e);
        throw new MCPException("Error in operation: " + e.getMessage());
    }
}
```

### Parameter Types
- **string**: Text values
- **number**: Numeric values (int, double, etc.)
- **boolean**: True/false values
- **object**: Complex objects (use Builder)

---

## 6. Recommended Workflow

1. **Extend `MCPServerApplication`** and implement the `init()` method
2. **Create tool class** extending `MCPTool` with proper constructors
3. **Add `@Action` methods** for each operation with `@Argument` annotations
4. **Register tools** using `registerToolMethods()` in `init()`
5. **Optionally register prompts** for user interaction
6. **Start the server** via Java or CLI
7. **Configure** via properties or `Settings`

---

## 7. Example: Complete Calculator Tool

```java
public class CalculatorTool extends MCPTool {
    
    public CalculatorTool() {
        super("calculator", "A calculator that performs arithmetic operations");
    }

    public CalculatorTool(MCPClient client) {
        super("calculator", "A calculator that performs arithmetic operations", null, client, true);
    }

    @Action(value = "calculator/add", description = "Add two numbers", arguments = {
            @Argument(key = "a", description = "The first operand", type = "number"),
            @Argument(key = "b", description = "The second operand", type = "number")
    })
    public double add(double a, double b) {
        return a + b;
    }

    @Action(value = "calculator/multiply", description = "Multiply two numbers", arguments = {
            @Argument(key = "a", description = "The first operand", type = "number"),
            @Argument(key = "b", description = "The second operand", type = "number")
    })
    public double multiply(double a, double b) {
        return a * b;
    }
}
```

**MCP stands for Model Context Protocol.** 