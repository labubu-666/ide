This repository contains a simple IDE implementation using JavaFX and LSP4J.

# Architecture

- Keep things simple.
- For now, no backwards compatibility, this is a greenfield project.

# Testing

- Assertions use `AssertJ`.
- Run tests after changes to prevent regressions.
- Use `Arrange-Act-Assert` pattern for test structure.
```bash
./gradlew test
```
- Don't run the application yourself, ill handle it myself.