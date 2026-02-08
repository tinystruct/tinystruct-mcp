# Developing MCP Tools with tinystruct

This guide providing step-by-step instructions on how to extend the **tinystruct-mcp** server by developing your own custom MCP tools using the annotation-driven pattern.

---

## 🏗 Understanding the Architecture

A tinystruct MCP implementation typically consists of two main components:
1.  **`MCPTool`**: A class (often an inner class) that defines the actual operations (actions) using `@Action` and `@Argument` annotations.
2.  **`MCPServer`**: The main application class that handles initialization and registration of tools and prompts.

---

## 🛠 1. Create a Custom Tool

Extend the `MCPTool` class and define your operations. Each method annotated with `@Action` becomes a tool available to the AI.

```java
import org.tinystruct.mcp.MCPTool;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;
import java.util.logging.Logger;

public class WeatherTool extends MCPTool {
    private static final Logger LOGGER = Logger.getLogger(WeatherTool.class.getName());

    public WeatherTool() {
        // Initialize with tool name and description
        super("weather", "A tool for retrieving weather information");
    }

    @Action(value = "weather/get", description = "Get current weather for a city", arguments = {
            @Argument(key = "city", description = "The name of the city", type = "string"),
            @Argument(key = "unit", description = "Temperature unit (celsius/fahrenheit)", type = "string")
    })
    public String getWeather(String city, String unit) {
        LOGGER.info("Fetching weather for " + city + " in " + unit);
        // Implement your logic here
        return "The weather in " + city + " is sunny, 25° " + (unit.equals("fahrenheit") ? "F" : "C");
    }
}
```

---

## 🔌 2. Register the Tool in the Server

To make your tool active, you must register it within an `MCPServer` implementation's `init()` method.

```java
import org.tinystruct.mcp.MCPServer;

public class MyMCPServer extends MCPServer {
    @Override
    public void init() {
        super.init();

        // Register your custom tool
        this.registerTool(new WeatherTool());
        
        // Optionally register other tools or prompts
    }
}
```

---

## 🚀 3. Start Your Server

Once your tool is registered, you can start your server using the dispatcher.

### Using the Dispatcher (Recommended)

After compiling your project, use the `bin/dispatcher` script to load your server class:

```sh
# On Linux/macOS
bin/dispatcher start --import org.tinystruct.system.HttpServer --import com.yourpackage.MyMCPServer --server-port 777

# On Windows
bin\dispatcher.cmd start --import org.tinystruct.system.HttpServer --import com.yourpackage.MyMCPServer --server-port 777
```

---

## 📝 4. Defining Arguments and Schemas

The `@Argument` annotation is crucial as it automatically generates the JSON schema required by the Model Context Protocol.

### Supported Types:
*   **`string`**: For text values.
*   **`number`**: For integers, doubles, and floats.
*   **`boolean`**: For true/false values.
*   **`object`**: For complex data structures (use `org.tinystruct.data.component.Builder`).

### Example with Complex Objects:
```java
@Action(value = "data/process", description = "Process complex data", arguments = {
        @Argument(key = "payload", description = "The JSON payload", type = "object")
})
public Builder processData(Builder payload) {
    // Logic to process the Builder object
    return payload;
}
```

---

## 💡 Best Practices

1.  **Namespacing**: Use the `toolname/action` format for `value` in `@Action` (e.g., `github/clone`).
2.  **Clear Descriptions**: Provide detailed descriptions for both tools and arguments; these are used by the LLM to understand when and how to call your tool.
3.  **Local Execution**: The tinystruct MCP implementation supports local execution by default. Ensure your tool methods are thread-safe.
4.  **Error Handling**: Wrap your logic in try-catch blocks and throw `MCPException` for errors that should be reported back to the LLM.
5.  **Logging**: Use `java.util.logging` to provide visibility into tool execution on the server side.

---

## 🎯 Complete Example: Calculator

```java
public class Calculator extends MCPServer {
    @Override
    public void init() {
        super.init();
        this.registerTool(new CalcTool());
    }

    public static class CalcTool extends MCPTool {
        public CalcTool() {
            super("calc", "Basic arithmetic operations");
        }

        @Action(value = "calc/add", description = "Add two numbers", arguments = {
                @Argument(key = "a", description = "First number", type = "number"),
                @Argument(key = "b", description = "Second number", type = "number")
        })
        public double add(double a, double b) {
            return a + b;
        }
    }
}
```

---

**Note**: *MCP stands for Model Context Protocol.*
