# jam4j

`jam4j` is an experimental Java Artifact Manager: a small CLI that brings an npm-like workflow to Java projects. It uses a `project.json` manifest to declare dependencies and scripts, resolves artifacts from Maven repositories with Apache Maven Resolver, and installs resolved JARs into a local `lib/` directory.

## Goals

- Manage Java dependencies from a JSON manifest instead of a Maven or Gradle build file.
- Resolve Maven Central artifacts and transitive compile-scope dependencies.
- Provide familiar commands such as `install`, `run`, `build`, `test`, `clean`, and `package`.
- Support portable script definitions through variable substitution.

## Project Status

This repository is an early implementation. The CLI, manifest model, Maven Resolver integration, local installation, script execution, basic tests, and native image packaging are present. Broader project-generation workflows remain design goals, not complete product features.

## Requirements

- Java 21
- Maven 3.9 or newer
- GraalVM for Java 21 with `native-image`, when building the native executable

## Build and Test

```bash
mvn test
mvn package
java -jar target/jam-1.0.0-SNAPSHOT.jar --help
java -jar target/jam-1.0.0-SNAPSHOT.jar --version
```

`mvn package` builds a shaded standalone JAR with `com.strategicgains.jam4j.Jam` as the entry point. The packaged CLI reports the Maven project version and build timestamp, for example `jam 1.0.0-SNAPSHOT (built 2026-05-04T21:18:42Z)`.

## Native Executable

With SDKMAN and a Java 21 GraalVM installed, build the native CLI with:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.2-graalce
mvn -Pnative package
./target/jam --help
./target/jam --version
```

The `native` Maven profile uses GraalVM Native Build Tools and produces `target/jam` for the current OS and CPU architecture. Replace `21.0.2-graalce` with the Java 21 GraalVM candidate installed on your machine. Native image builds are platform-specific, so release builds should run once per target platform. The native executable uses the same generated version metadata as the shaded JAR.

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

The examples above use the shaded JAR directly. If you install or alias the native executable as `jam`, replace `java -jar target/jam-1.0.0-SNAPSHOT.jar` with `jam`.

## Command Reference

The top-level command supports:

- `-h, --help`: show top-level help
- `-V, --version`: print the CLI version and build timestamp

Subcommands print command-specific usage when invocation fails validation, such as an unknown option or missing required argument.

Several commands share repository, cache, install-directory, and output options. Those shared options are documented in [Repository and Cache Options](#repository-and-cache-options).

### `jam init [options] [target-directory]`

Creates a scaffold `project.json`. If no target directory is supplied, `jam` writes to the current directory. The command prompts for missing project metadata and writes default `build`, `test`, `run`, and `clean` scripts.

Aliases: `n`

Options:

- `--force`: overwrite `project.json` if it already exists
- `--name <name>`: project name
- `--version <version>`: project version
- `--main <class>`: main class

Examples:

```bash
jam init
jam init --name my-app --version 0.1.0 --main com.example.Main ./my-app
jam init --force
```

### `jam search [options] <query...>`

Searches for Maven artifacts. By default, search includes Maven Central and returns up to 20 results. Multi-word queries are accepted.

Aliases: `s`

Options:

- `-i, --interactive`: prompt for result numbers to install after searching
- `-m, --max <count>`: maximum number of results to return, default `20`
- `--snapshots`: include snapshot versions
- `--local`: include artifacts from the local Maven cache
- `--no-central`: skip Maven Central and search only the local cache or configured repositories
- `-p, --project <file>`: project file to update during interactive installs, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Examples:

```bash
jam search jackson
jam search -m 5 "junit jupiter"
jam search --local --no-central assertj
jam search --interactive jackson databind
```

### `jam install [options] [artifact...]`

Resolves and installs artifacts into the local library directory. Artifacts must use Maven coordinates in `group:artifact:version` form. If artifacts are supplied and the project file exists, `jam` also records them in `dependencies` in `project.json`.

If no artifacts are supplied, `jam install` reads all `dependencies` and `devDependencies` from `project.json` and installs them.

Aliases: `i`

Options:

- `-p, --project <file>`: project file to read or update, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Examples:

```bash
jam install
jam install com.fasterxml.jackson.core:jackson-databind:2.17.1
jam install -d vendor/lib org.assertj:assertj-core:3.25.3
jam install --ignore-pom-repos com.hazelcast:hazelcast:5.5.0
```

### `jam path [options] [artifact...]`

Resolves artifacts and prints a platform-specific Java classpath. If artifacts are supplied, those coordinates are resolved. If no artifacts are supplied, `jam path` reads all `dependencies` and `devDependencies` from `project.json`.

Aliases: `p`

Options:

- `-p, --project <file>`: project file to read, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Examples:

```bash
jam path
jam path org.junit.jupiter:junit-jupiter:5.10.2
java -cp "$(jam path):target/classes" com.example.Main
```

### `jam run [options] <script> [args...]`

Executes one or more named scripts from `project.json`. Before running scripts, `jam` resolves all project dependencies and makes that classpath available to the `{{deps}}` script variable.

Aliases: `r`

Options:

- `-l, --list`: list scripts defined in `project.json`
- `-p, --project <file>`: project file to read, default `./project.json`
- `-a, --arg <arg>`: pass an argument to the preceding script. This is parsed by `jam run` rather than declared as a picocli option, so it appears after a script name.

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Examples:

```bash
jam run --list
jam run build
jam run build -a --release -a 21
jam run clean build test
```

When several script names are supplied, `jam` runs them in order. Arguments following a script are passed to that script until another known script name is encountered.

### `jam build [options] [--] [args...]`

Runs the `build` script from `project.json`. This is a convenience command equivalent to `jam run build`, with any supplied arguments forwarded to the script.

Options:

- `-p, --project <file>`: project file to read, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Example:

```bash
jam build -- --release 21
```

Use `--` before script arguments that begin with `-` so picocli does not parse them as `jam build` options.

### `jam test [options] [--] [args...]`

Runs the `test` script from `project.json`. This is a convenience command equivalent to `jam run test`, with any supplied arguments forwarded to the script.

Options:

- `-p, --project <file>`: project file to read, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Example:

```bash
jam test smoke
```

Use `--` before script arguments that begin with `-` so picocli does not parse them as `jam test` options.

### `jam clean [options] [--] [args...]`

Runs the `clean` script from `project.json`. This is a convenience command equivalent to `jam run clean`, with any supplied arguments forwarded to the script.

Options:

- `-p, --project <file>`: project file to read, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Example:

```bash
jam clean
```

Use `--` before script arguments that begin with `-` so picocli does not parse them as `jam clean` options.

### `jam package [options] [--] [args...]`

Runs the `package` script from `project.json`. This is a convenience command equivalent to `jam run package`, with any supplied arguments forwarded to the script.

Options:

- `-p, --project <file>`: project file to read, default `./project.json`

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Example:

```bash
jam package
```

Use `--` before script arguments that begin with `-` so picocli does not parse them as `jam package` options.

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

Common options are available on `search`, `install`, `path`, `run`, `build`, `test`, `clean`, and `package`:

- `--config <file>`: user configuration file, defaulting to `JAM_CONFIG`, `~/.jam/config.json`, or `~/.jamcfg.json`
- `-c, --cache <dir>`: local Maven repository cache, defaulting to `JAM_CACHE` or `~/.m2/repository`
- `-d, --directory <dir>`: install directory, defaulting to `./lib`
- `-L, --local-only`: copy JARs instead of creating symlinks
- `-r, --repo <url|name=url>`: add a repository
- `--ignore-pom-repos`: strict mode; ignore repositories declared by dependency POMs and resolve only from Maven Central plus `--repo` repositories
- `-q, --quiet` and `-v, --verbose`: control output

For named repositories, credentials can be supplied with `JAM_REPO_<NAME>_USER` and `JAM_REPO_<NAME>_PASSWORD`.

By default, artifact resolution follows Maven behavior and may use repositories declared in dependency POMs. Use `--ignore-pom-repos` when you want reproducible strict resolution from only Maven Central and repositories you explicitly declare:

```bash
jam install --ignore-pom-repos --repo internal=https://repo.example.com/maven2 com.example:tool:1.0.0
jam path --ignore-pom-repos com.hazelcast:hazelcast:5.5.0
```

## Source Layout

- `src/main/java/com/strategicgains/jam4j/Jam.java`: CLI entry point
- `src/main/java/com/strategicgains/jam4j/command`: picocli commands
- `src/main/java/com/strategicgains/jam4j/resolver`: Maven artifact resolution and search
- `src/main/java/com/strategicgains/jam4j/model`: `project.json` model
- `src/main/java/com/strategicgains/jam4j/script`: script execution and substitutions
- `src/test/java`: JUnit 5 and AssertJ tests

## License

See `LICENSE`.
