# jam4j

`jam4j` is an experimental Java Artifact Manager: a small CLI that brings an npm-like workflow to Java projects. It uses a `project.json` manifest to declare dependencies and scripts, resolves artifacts from Maven repositories with Apache Maven Resolver, and installs resolved JARs into a local `lib/` directory.

## Goals

- Manage Java dependencies from a JSON manifest instead of a Maven or Gradle build file.
- Resolve Maven Central artifacts and transitive compile-scope dependencies.
- Provide familiar commands such as `install`, `run`, `build`, `test`, `clean`, and `package`.
- Support portable script definitions through variable substitution.

## Project Status

This repository is an early implementation. The CLI, manifest model, Maven Resolver integration, local installation, script execution, and basic tests are present. Broader project-generation workflows remain design goals, not complete product features.

## Requirements

- Java 21
- Maven 3.9 or newer

## Build and Test

```bash
mvn test
mvn package
java -jar target/jam4j-1.0.0-SNAPSHOT.jar --help
java -jar target/jam4j-1.0.0-SNAPSHOT.jar --version
```

`mvn package` builds a shaded standalone JAR with `com.strategicgains.jam4j.Jam` as the entry point. The packaged CLI reports the Maven project version and build timestamp, for example `jam 1.0.0-SNAPSHOT (build 2026-05-04T21:18:42Z)`.

## Release Flow

CI runs on branch pushes and pull requests. It builds the shaded JAR and smoke-tests the CLI.

Versioned releases are created from Git tags. To publish a release:

1. Update the Maven version in `pom.xml` from a snapshot to the release version, for example `1.0.0-SNAPSHOT` to `1.0.0`.
2. Verify the release build locally:

   ```bash
   mvn clean package
   java -jar target/jam4j-1.0.0.jar --version
   ```

3. Commit the release version:

   ```bash
   git add pom.xml
   git commit -m "release: 1.0.0"
   ```

4. Create and push a matching tag:

   ```bash
   git tag v1.0.0
   git push origin main
   git push origin v1.0.0
   ```

   You can also push the branch and all local tags together:

   ```bash
   git push origin main --tags
   ```

The release workflow runs only for tags matching `v*`. It verifies that the tag version matches `pom.xml`, rejects `*-SNAPSHOT` versions, builds the package, and publishes a GitHub Release with:

- `jam4j-<version>.jar`
- `jam4j-<version>.jar.sha256`

After publishing, bump `pom.xml` to the next development snapshot and commit it:

```bash
git add pom.xml
git commit -m "chore: start 1.0.1-SNAPSHOT"
git push origin main
```

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
    "build": "javac -cp {{deps}} -d {{classes}} {{sources}}",
    "run": "java -cp {{deps}}{:}{{classes}} {{main}}",
    "test": "javac -cp {{deps:dev}} -d {{classes}} {{sources}} && javac -cp {{deps:dev}}{:}{{classes}} -d {{classes:test}} {{sources:test}} && java -cp {{deps:dev}}{:}{{classes}}{:}{{classes:test}} org.junit.platform.console.ConsoleLauncher --scan-classpath --disable-banner",
    "clean": "rm -rf {{output}}"
  }
}
```

Default scripts are provided for `build`, `run`, `package`, and `clean` if none are defined in `project.json`. Providing
a definition for a script name overrides the default. For example, if `build` is defined in `project.json`, `jam build` runs that script instead of the default.

Dependency keys use `group:artifact`; values are versions. At runtime they are resolved as Maven coordinates such as `group:artifact:version`.

The optional `sourceDir`, `testDir`, and `outputDir` fields override the default directories. Omit them to use the standard Maven layout:

```json
{
  "sourceDir": "src",
  "testDir": "test",
  "outputDir": "build"
}
```

## Script Variables

Scripts support cross-platform substitutions:

| Token | Expands to |
|---|---|
| `{{deps}}` | Resolved classpath of production dependencies only |
| `{{deps:dev}}` | Resolved classpath of production + dev dependencies |
| `{{sources}}` | Space-separated `*.java` files under `sourceDir` (default `src/main/java`) |
| `{{sources:test}}` | Space-separated `*.java` files under `testDir` (default `src/test/java`) |
| `{{classNames}}` | Space-separated fully-qualified class names derived from `sourceDir` |
| `{{classNames:test}}` | Space-separated fully-qualified class names derived from `testDir` |
| `{{classes}}` | Compiled classes output directory (`outputDir/classes`, default `target/classes`) |
| `{{classes:test}}` | Compiled test classes output directory (`outputDir/test-classes`, default `target/test-classes`) |
| `{{output}}` | Project output directory (`outputDir`, default `target`) |
| `{{main}}` | Main class from the `"main"` field in `project.json` |
| `{{jarflags}}` | `--main-class <mainClass>` when `"main"` is set, otherwise empty |
| `{/}` | Platform file separator |
| `{:}` | Platform path separator |
| `{~}` | User home directory |
| `{./path}` | Project-relative path using platform separators |

Scripts are executed through the system shell (`sh -c` on Unix/macOS, `cmd /c` on Windows), so standard shell operators such as `&&`, `||`, `;`, and pipes work exactly as they would on the command line.

Arguments can be passed to scripts with `-a` or `--arg`:

```bash
java -jar target/jam4j-1.0.0-SNAPSHOT.jar run build -a --verbose test -a smoke
```

## CLI Usage

After building:

```bash
java -jar target/jam4j-1.0.0-SNAPSHOT.jar search jackson
java -jar target/jam4j-1.0.0-SNAPSHOT.jar install com.fasterxml.jackson.core:jackson-databind:2.17.1
java -jar target/jam4j-1.0.0-SNAPSHOT.jar path
java -jar target/jam4j-1.0.0-SNAPSHOT.jar run --list
java -jar target/jam4j-1.0.0-SNAPSHOT.jar run build
```

Convenience commands execute matching scripts from `project.json`:

```bash
java -jar target/jam4j-1.0.0-SNAPSHOT.jar build
java -jar target/jam4j-1.0.0-SNAPSHOT.jar test
java -jar target/jam4j-1.0.0-SNAPSHOT.jar clean
java -jar target/jam4j-1.0.0-SNAPSHOT.jar package
```

The examples above use the shaded JAR directly. If you install or alias the JAR launcher as `jam`, replace `java -jar target/jam4j-1.0.0-SNAPSHOT.jar` with `jam`.

## Command Reference

The top-level command supports:

- `-h, --help`: show top-level help
- `-V, --version`: print the CLI version and build timestamp

Subcommands print command-specific usage when invocation fails validation, such as an unknown option or missing required argument.

Several commands share repository, cache, install-directory, and output options. Those shared options are documented in [Repository and Cache Options](#repository-and-cache-options).

### `jam init [options] [target-directory]`

Creates a scaffold `project.json`. If no target directory is supplied, `jam` writes to the current directory. The command prompts for missing project metadata and writes default `build`, `test`, `run`, and `clean` scripts.

Pass `--from <pom.xml>` to bootstrap from an existing Maven project. `jam` reads all `<dependency>` entries from the POM and writes them into a new `project.json`. Compile- and runtime-scoped dependencies are recorded under `dependencies`; test-, provided-, and system-scoped dependencies are recorded under `devDependencies`. `name` and `version` are populated from the POM's `artifactId` and `version` unless overridden with `--name` or `--version`.

Aliases: `n`

Options:

- `-f, --from <file>`: bootstrap `project.json` from a Maven `pom.xml`
- `--force`: overwrite `project.json` if it already exists
- `--name <name>`: project name
- `--version <version>`: project version
- `--main <class>`: main class

Examples:

```bash
jam init
jam init --name my-app --version 0.1.0 --main com.example.Main ./my-app
jam init --force
jam init --from pom.xml
jam init --from pom.xml --force
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

Resolves artifacts and prints a platform-specific Java classpath. If artifacts are supplied, those coordinates are resolved. If no artifacts are supplied, `jam path` reads `dependencies` from `project.json` (production only by default).

Aliases: `p`

Options:

- `-p, --project <file>`: project file to read, default `./project.json`
- `--dev`: include `devDependencies` in the classpath (production + dev)

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Examples:

```bash
jam path
jam path --dev
jam path org.junit.jupiter:junit-jupiter:5.10.2
java -cp "$(jam path):target/classes" com.example.Main
java -cp "$(jam path --dev):target/classes:target/test-classes" org.junit.platform.console.ConsoleLauncher --scan-classpath
```

### `jam run [options] <script> [args...]`

Executes one or more named scripts from `project.json`. Before running scripts, `jam` resolves project dependencies and makes them available via script variables: `{{deps}}` for production dependencies only, and `{{deps:dev}}` for production + dev dependencies. If no `run` script is defined but a `"main"` class is set, `jam run` synthesizes a default: `java -cp {{deps}}{:}{{classes}} {{main}}`.

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

Builds a fat (uber) JAR containing the project's compiled classes and all production dependencies merged into a single archive. If a `package` script is defined in `project.json`, that script runs instead and the native build is skipped.

The native build:
- Merges all production dependency JARs into the output archive
- Concatenates `META-INF/services/*` entries so SPI registrations from all JARs survive
- Strips JAR signature files so the repacked archive remains valid
- Sets `Main-Class` in the manifest when `"main"` is configured in `project.json`
- Project classes from `--classes` always override dependency entries with the same path

Output JAR defaults to `target/<name>-<version>.jar` (using `name` and `version` from `project.json`).

Options:

- `-p, --project <file>`: project file to read, default `./project.json`
- `-o, --output <file>`: output JAR path (default: `target/<name>-<version>.jar`)
- `--classes <dir>`: compiled classes directory (default: `<outputDir>/classes`)

Also supports the shared options: `--config`, `-c, --cache`, `-d, --directory`, `-L, --local-only`, `-r, --repo`, `--ignore-pom-repos`, `-q, --quiet`, and `-v, --verbose`.

Examples:

```bash
jam package
jam package --output target/myapp.jar
jam package --classes target/classes --output dist/myapp-1.0.jar
```

Use `--` before script arguments that begin with `-` so picocli does not parse them as `jam package` options (only relevant when a `package` script is defined).

#### Comparison with Maven Shade Plugin

**The same:**
- Strips JAR signature files (`*.SF`, `*.RSA`, `*.DSA`, `*.EC`) so the repacked archive stays valid
- Merges `META-INF/services/*` by concatenation, preserving all SPI registrations across all JARs
- Sets `Main-Class` in the manifest
- First-in-wins for duplicate class/resource entries — the same as Shade's default behavior without custom transformers

**Different:**
- **No package relocation** — Shade can rewrite bytecode (via ASM) to rename packages and eliminate version conflicts at runtime; `jam package` does not. If two dependencies require incompatible versions of the same library, one version is silently dropped.
- **No pluggable resource transformers** — Shade ships transformers for XML merging, `NOTICE`/`LICENSE` appending, Plexus component descriptors, and more; `jam package` only handles `META-INF/services/`.
- **`NOTICE`/`LICENSE` files are not merged** — first-in-wins, so license attributions from some dependencies may be silently dropped from the output archive.
- **No artifact or class filtering** — Shade can exclude specific JARs or class patterns from the output; `jam package` always includes all production dependencies.
- **`module-info.class` is not handled specially** — the entry from whichever JAR is processed first is used as-is; Shade has dedicated handling for multi-release JARs and JPMS modules.

If any of these limitations matter for your project, define a `package` script in `project.json` and invoke the Maven Shade Plugin (or another tool) directly.

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
