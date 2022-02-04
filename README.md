[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)]()
[![Zulip chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://camel.zulipchat.com/#narrow/stream/258729-camel-tooling)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=camel-tooling_camel-debug-adapter&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=camel-tooling_camel-debug-adapter)

# Debug Adapter for Apache Camel

Debug Adapter for [Apache Camel](https://camel.apache.org/) based on the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/) and [Eclipse LSP4J](https://github.com/eclipse/lsp4j) as SDK.

The Debug Adapter allows to set breakpoints and debug Camel textual DSLs.

## Requirements

The Camel instance to debug must follow these requirements:

- Camel 3.15+
- Have `camel-debug` on the classpath
- Have JMX enabled

The Camel Debug Server Adapter must use Java Runtime Environment 11+ with `com.sun.tools.attach.VirtualMachine` (available in most JVMs such as Hotspot and OpenJDK).

## Supported scope

- Local only
- Attach only
- Java DSL (not tested with xml and Yaml even if it should work)
- Single context
- Single breakpoint
- Add and remove breakpoint
- Inspect some variables when breakpoint is hit
- Stop on hit breakpoint
- Resume all
- Stepping when the route definition is in the same file
- Tested manually with [Eclipse LSP4E](https://github.com/eclipse/lsp4e), see [how-to](https://github.com/camel-tooling/camel-dap-client-eclipse#how-to-use-the-debug-adapter-for-apache-camel)
- Tested manually with locally built [VS Code Debug Adapter for Apache Camel](https://github.com/camel-tooling/camel-dap-client-vscode), see [how-to](https://github.com/camel-tooling/camel-dap-client-vscode#how-to-use-it)

## How to use it

- `java -jar camel-dap-server-xxx.jar`
- json parameter to provide on attach:

```json
{
"attach_pid": "xxxxx"
}
```