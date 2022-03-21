[![Build and Test](https://github.com/camel-tooling/camel-debug-adapter/actions/workflows/ci.yaml/badge.svg)](https://github.com/camel-tooling/camel-debug-adapter/actions/workflows/ci.yaml)
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
- Camel DSLs:
  - Java DSL.
  - Yaml DSL. Note that the breakpoint must be set on the from/to line, not on the Camel URI line.
  - XML DSL in Camel Main mode. It implies that it is not working with Camel context specified in Camel XML file.
- Single context
- Add and remove breakpoint
- Inspect some variables when breakpoint is hit
- Stop on hit breakpoint
- Resume a single route instance and resume all
- Stepping when the route definition is in the same file
- Specific client provided:
  - on [Eclipse Desktop](https://github.com/camel-tooling/camel-dap-client-eclipse), install from [this update site](https://camel-tooling.github.io/camel-dap-client-eclipse/)
  - on [VS Code](https://github.com/camel-tooling/camel-dap-client-vscode), snapshot binary available [here](https://download.jboss.org/jbosstools/vscode/snapshots/vscode-debug-adapter-apache-camel/)
- Update value of common variables which are grouped in `Debugger` scope

## How to use it

- `java -jar camel-dap-server-xxx.jar`
- json parameter to provide on attach:

```json
{
"attach_pid": "xxxxx",
"request": "attach"
}
```

Note that the request parameter is not part of the protocol but required by some clients (at least VS Code and Eclipse desktop).
