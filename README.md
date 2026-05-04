# jam4j

`jam4j` is an experimental Java Artifact Manager: a small CLI that brings an npm-like workflow to Java projects. It uses a `project.json` manifest to declare dependencies and scripts, resolves artifacts from Maven repositories with Apache Maven Resolver, and installs resolved JARs into a local `lib/` directory.

## Goals

- Manage Java dependencies from a JSON manifest instead of a Maven or Gradle build file.
- Resolve Maven Central artifacts and transitive compile-scope dependencies.
- Provide familiar commands such as `install`, `run`, `build`, `test`, `clean`, and `package`.
- Support portable script definitions through variable substitution.

## Project Status

This repository is an early implementation. The CLI, manifest model, Maven Resolver integration, local installation, script execution, and basic tests are present. Native image packaging and broader project-generation workflows are design goals, not complete product features.

## Requirements

- Java 21
- Maven 3.9 or newer

## Build and Test

```bash
mvn test
mvn package
java -jar target/jam-1.0.0-SNAPSHOT.jar --help
```

`mvn package` builds a shaded standalone JAR with `com.strategicgains.jam4j.Jam` as the entry point.

## `project.json`

A project manifest can declare dependencies, dev dependencies, a main class, and named scripts:

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "main": "com.example.Main",
  "dependencies": {
    "com.fasterxml.jackson.core:jackson-databind": "2.17.1"
  },
  "devDependencies": {
    "org.junit.jupiter:junit-jupiter": "5.10.2"
  },
  "scripts": {
    "build": "javac -cp {{deps}} -d {./target/classes} src{/}*.java",
    "run": "java -cp {{deps}}{:}{./target/classes} com.example.Main",
    "test": "java -cp {{deps}} org.junit.platform.console.ConsoleLauncher --scan-classpath",
    "clean": "rm -rf {./target/classes}"
  }
}
```

Dependency keys use `group:artifact`; values are versions. At runtime they are resolved as Maven coordinates such as `group:artifact:version`.

## CLI Usage

After building:

```bash
java -jar target/jam-1.0.0-SNAPSHOT.jar search jackson
java -jar target/jam-1.0.0-SNAPSHOT.jar install com.fasterxml.jackson.core:jackson-databind:2.17.1
java -jar target/jam-1.0.0-SNAPSHOT.jar install --save org.assertj:assertj-core:3.25.3
java -jar target/jam-1.0.0-SNAPSHOT.jar path
java -jar target/jam-1.0.0-SNAPSHOT.jar run --list
java -jar target/jam-1.0.0-SNAPSHOT.jar run build
```

Convenience commands execute matching scripts from `project.json`:

```bash
java -jar target/jam-1.0.0-SNAPSHOT.jar build
java -jar target/jam-1.0.0-SNAPSHOT.jar test
java -jar target/jam-1.0.0-SNAPSHOT.jar clean
java -jar target/jam-1.0.0-SNAPSHOT.jar package
```

## Script Variables

Scripts support cross-platform substitutions:

- `{{deps}}`: resolved dependency classpath
- `{/}`: platform file separator
- `{:}`: platform path separator
- `{~}`: user home directory
- `{./path}`: project-relative path using platform separators

Arguments can be passed to scripts with `-a` or `--arg`:

```bash
java -jar target/jam-1.0.0-SNAPSHOT.jar run build -a --verbose test -a smoke
```

## Repository and Cache Options

Common options include:

- `-c, --cache <dir>`: local Maven repository cache, defaulting to `JAM_CACHE` or `~/.m2/repository`
- `-d, --directory <dir>`: install directory, defaulting to `./lib`
- `-L, --local-only`: copy JARs instead of creating symlinks
- `-r, --repo <url|name=url>`: add a repository
- `-q, --quiet` and `-v, --verbose`: control output

For named repositories, credentials can be supplied with `JAM_REPO_<NAME>_USER` and `JAM_REPO_<NAME>_PASSWORD`.

## Source Layout

- `src/main/java/com/strategicgains/jam4j/Jam.java`: CLI entry point
- `src/main/java/com/strategicgains/jam4j/command`: picocli commands
- `src/main/java/com/strategicgains/jam4j/resolver`: Maven artifact resolution and search
- `src/main/java/com/strategicgains/jam4j/model`: `project.json` model
- `src/main/java/com/strategicgains/jam4j/script`: script execution and substitutions
- `src/test/java`: JUnit 5 and AssertJ tests

## License

See `LICENSE`.
