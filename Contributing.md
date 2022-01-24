# Build project

In Eclipse IDE, launch `camel-debug-adapter` launch configuration.

From command-line, launch `mvn clean verify`.

# How to test Camel Debug Adapter Server in IDEs

For Eclipse, see https://github.com/camel-tooling/camel-dap-client-eclipse#how-to-use-the-debug-adapter-for-apache-camel

For VS Code, replace the jar in [jars](https://github.com/camel-tooling/camel-dap-client-vscode/tree/main/jars) folder and read [how to configure a launch configuration](https://github.com/camel-tooling/camel-dap-client-vscode#how-to-use-it).

# How to debug Camel Debug Adapter Server in Eclipse Desktop IDE

You can use the `Test camel-dap-server` launch configuration in Debug mode.

To test with the Client integration of Eclipse IDE:

- Install [Eclipse LSP4E](https://projects.eclipse.org/projects/technology.lsp4e) in your Eclipse Desktop instance
- Build the Camel Debug Adapter Jar
- Launch the Camel application that you want to debug
- Grab the pid of the Camel Application
- Create a `Debug Adapter Launcher` configuration
  - Select `launch a Debug Server using the following arguments`
  - In `command` field, set `java`
  - In `Arguments` field, set `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=3000 -jar <pathTo>/camel-dap-server-xxx.jar`
  - Select `Monitor Debug Adapter launch process`
  - In `Launch Parameters (Json)` field, set
  
  ```json
  {
   "request": "attach",
   "attach_pid": "<thePidOfTheCamelApplication>"
  }
  ```
  - Click `Debug`
- You can now set breakpoints in textual Camel route definition and Camel Debug Adapter project
