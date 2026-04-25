This repository contains a simple IDE implementation using JavaFX and LSP4J.

# Architecture

- Keep things simple.
- For now, no backwards compatibility, this is a greenfield project.

# Testing

- Assertions use `AssertJ` for 
- Run tests with after changes to prevent regressions
```bash
./gradlew test
```