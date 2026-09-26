# Development Log

## Feature: CLI Config File Support

**Branch:** `feature/cli-config-file`
**Feature:** Add `.pragent.properties` support

### Overview

Added support for an optional `.pragent.properties` configuration file at the repository root.

The CLI can now read shared per-repository configuration instead of requiring all values to be provided through CLI arguments.

### Configuration File

The repository can contain a `.pragent.properties` file:

```properties
backend.url=http://localhost:8080
default.base=main
max.chunk.chars=8000
```

The file is **not a secret** and can be committed to the repository as shared project configuration.

### Supported Properties

| Property          | Purpose                                             |
| ----------------- | --------------------------------------------------- |
| `backend.url`     | URL of the PR-Agent backend                         |
| `default.base`    | Default base branch to use                          |
| `max.chunk.chars` | Maximum number of characters allowed per diff chunk |

### Configuration Precedence

The CLI uses the following precedence:

```text
Explicit CLI flag
        ↓
.pragent.properties
        ↓
Hardcoded fallback
```

In other words:

1. If the user explicitly provides a CLI option, that value is used.
2. Otherwise, if the corresponding value exists in `.pragent.properties`, it is used.
3. Otherwise, the CLI falls back to its hardcoded fallback where one exists.

### Base Branch Behavior

`--base` does **not** silently assume a built-in default.

A repository can explicitly opt into a default base branch by adding:

```properties
default.base=main
```

This means the tool does not assume that every repository uses `main`.

For example:

```text
CLI:
--base develop
```

takes precedence over:

```properties
default.base=main
```

Result:

```text
develop
```

If neither `--base` nor `default.base` is provided, there is no silent built-in base branch assumption.

### Why This Change Was Made

The goal is to make the CLI easier to configure on a per-repository basis while keeping user-provided CLI options more explicit and powerful.

This also allows different repositories to use different configuration without requiring users to repeatedly specify the same values.

### Example

A repository may contain:

```properties
backend.url=http://localhost:8080
default.base=main
max.chunk.chars=8000
```

The CLI can then use these values automatically.

If the user runs the CLI with an explicit option such as:

```text
--base develop
```

the CLI uses `develop` instead of the configured:

```properties
default.base=main
```

### Git

The configuration file is intended to be committed:

```text
.pragent.properties
```

It should **not** be added to `.gitignore` because it contains shared repository configuration rather than secrets.

### Result

The CLI now supports repository-level configuration through `.pragent.properties` while preserving explicit CLI arguments as the highest-priority configuration source.
