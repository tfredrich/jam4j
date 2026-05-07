# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

`jam4j` is a Java CLI tool (`jam`) that brings an npm-like workflow to Java projects. It reads a `project.json` manifest to declare dependencies, resolves artifacts from Maven repositories using Apache Maven Resolver, installs JARs into a local `lib/` directory, and executes named scripts.

## Build and Test Commands

```bash
mvn test                                        # run unit tests (integration tests excluded by default)
mvn test -Dgroups=integration                   # run integration-tagged tests
mvn package                                     # compile + test + build shaded standalone JAR
mvn clean                                       # remove target/
java -jar target/jam4j-1.0.0-SNAPSHOT.jar --help   # smoke-test the CLI after packaging
```

To run a single test class:
```bash
mvn test -Dtest=VariableSubstitutorTest
```

## Architecture

The entry point is `Jam.java`, which wires up the picocli command tree. All commands share options via `CommonOptions` (cache dir, install dir, repo URLs, quiet/verbose flags).

| Package | Role |
|---|---|
| `command/` | One `*Command.java` per subcommand (`search`, `install`, `path`, `run`, `build`, `test`, `clean`, `package`, `init`). Convenience commands (`build`, `test`, `clean`, `package`) delegate to `RunCommand`. |
| `resolver/` | `ResolverFactory` builds a Maven Resolver session; `ArtifactResolver` drives artifact/transitive resolution and search against Maven Central and configured repos. |
| `install/` | `Installer` copies or symlinks resolved JARs into the project `lib/` directory. |
| `model/` | `ProjectJson` — Jackson-bound POJO for `project.json` (name, version, main, dependencies, devDependencies, scripts). |
| `script/` | `VariableSubstitutor` expands `{{deps}}`, `{/}`, `{:}`, `{~}`, and `{./path}` tokens in script strings. `ScriptRunner` forks the substituted command. |

The shaded JAR (built by maven-shade-plugin) bundles all dependencies for standalone execution. The `ServicesResourceTransformer` is required so Maven Resolver's SPI wires up correctly inside the fat JAR.

## Key Constraints

- **Maven Resolver version**: stay on `1.9.x`. The `2.x` API differs significantly; do not upgrade without a deliberate migration.
- **Java 21** is required locally; compiler source/target are set via `java.version` in `pom.xml`.
- Integration tests (filesystem or network) should be tagged `@Tag("integration")` — Surefire excludes them by default.

## Testing Conventions

- Framework: JUnit Jupiter + AssertJ.
- Test files: `*Test.java` in the same package as the class under test.
- Method names describe behavior: `replacesDeps`, `tokenizeWithDoubleQuotes`.
- Use temporary directories for filesystem-sensitive tests; tag network tests as `integration`.

## Commit Style

Conventional-style short imperative messages with an optional prefix:
```
fix: handle missing project.json
feat: add --snapshots flag to search
```

## Release Flow

1. Update `pom.xml` version from `*-SNAPSHOT` to the release version.
2. `mvn clean package` and verify `--version` output.
3. `git commit -m "release: X.Y.Z"` → `git tag vX.Y.Z` → `git push origin main --tags`.
4. The GitHub Actions release workflow triggers on `v*` tags, verifies the tag matches `pom.xml`, and publishes a GitHub Release with the JAR and SHA-256.
5. After release, bump `pom.xml` to the next `-SNAPSHOT` and push.
