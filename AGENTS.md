# Repository Guidelines

## Project Structure & Module Organization

This is a Maven-based Java 21 CLI project. Main code lives in `src/main/java/com/strategicgains/jam4j`, with the picocli entry point in `Jam.java`. Commands are under `command`, installation in `install`, manifest parsing in `model`, artifact resolution in `resolver`, and script handling in `script`.

Tests mirror the package layout in `src/test/java/com/strategicgains/jam4j`. Build outputs go to `target/` and should not be committed. `design_overview.md` gives product context for larger changes.

## Build, Test, and Development Commands

- `mvn test` runs the JUnit 5 test suite through Surefire. Integration-tagged tests are excluded by default.
- `mvn test -Dgroups=integration` runs tests tagged with the `integration` group.
- `mvn package` compiles, tests, and builds the shaded standalone JAR in `target/`.
- `java -jar target/jam-1.0.0-SNAPSHOT.jar --help` checks the packaged CLI after `mvn package`.
- `mvn clean` removes generated build artifacts.

Use Java 21 locally; compiler source and target are set from `java.version` in `pom.xml`.

## Coding Style & Naming Conventions

Follow the existing Java style: 4-space indentation, same-line braces, package-private test classes, and clear method names. Production classes use `PascalCase`; methods, fields, and locals use `camelCase`; constants use `UPPER_SNAKE_CASE`.

Keep command implementations focused in `command/*Command.java` and prefer shared CLI options through `CommonOptions`. Avoid Maven Resolver 2.x without a deliberate migration; the POM intentionally uses the 1.9.x API.

## Testing Guidelines

Use JUnit Jupiter and AssertJ, as shown in `VariableSubstitutorTest`. Name test files `*Test.java` and keep them in the same package as the code under test. Prefer behavior-focused names such as `replacesDeps` or `tokenizeWithDoubleQuotes`.

Add tests for parser, resolver, or command behavior changes. For filesystem or network-sensitive tests, isolate temporary directories and consider tagging slower external tests as `integration`.

## Commit & Pull Request Guidelines

Recent history is light, but includes conventional-style messages such as `fix: added .claude/ to .gitignore`. Prefer short imperative commits with an optional prefix, for example `fix: handle missing project.json`.

Pull requests should include a concise description, the motivation or linked issue, and the commands run for verification. For CLI behavior changes, include before/after command examples or relevant help output.

## Security & Configuration Tips

Do not commit local caches, credentials, or generated `target/` artifacts. Repository credentials should be supplied through environment variables such as `JAM_REPO_<name>_USER` and `JAM_REPO_<name>_PASSWORD`, not hard-coded in source or examples.
