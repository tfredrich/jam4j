# Task

I want to create a java-based dependency management system that behaves much like the nodejs npm application. I want it to use a json file (project.json) for the dependencies and be able to use those to build my Java projects.

# Core Design:

* A CLI tool (jam) with install, build, test, run, package commands mirroring npm's UX.
* A `project.json` manifest (like npm's package.json) with dependencies declared as "group:artifact": "version".
* Fetch JARs from Maven Central using its REST API — no need to reinvent the artifact ecosystem.
* Resolve transitive deps by parsing each artifact's POM file recursively.
* Cache to ~/.jam/cache (like ~/.m2 but flat), with a local lib/ dir per project (like node_modules) containing symlinks to the cached JARs.
* Build with javac, using the resolved classpath from the dependencies.
* Test with JUnit, running tests in the classpath.
* Package with jar, including dependencies in the manifest's Class-Path.

**The minimal stack:**

| Concern | Library |
|---|---|
| CLI framework | `picocli` |
| Dependency resolution | `maven-resolver-impl` + `maven-resolver-transport-http` |
| POM parsing (transitive deps) | `maven-model`, `maven-model-builder` |
| JSON manifest (`project.json`) | `jackson-databind` |

Use the **Apache Maven Resolver** (the artifact resolution engine Maven uses internally) is available as a standalone library. Use it directly without embedding all of Maven. That handles the hardest part (transitive dependency resolution, downloading from Maven Central, local cache) and you write only the CLI and JSON manifest layer on top.

- Maven Resolver already handles version conflict resolution, transitive dependency walking, and caching to `~/.m2/repository` — reusing the same cache as Maven/Gradle
- `picocli` is the standard for Java CLIs

## Rough `project.json` shape:

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "main": "com.example.Main",
  "dependencies": {
    "com.fasterxml.jackson.core:jackson-databind": "2.15.2"
  },
  "devDependencies": {
    "org.junit.jupiter:junit-jupiter": "5.10.0"
  },
  "scripts": {
    "build": "javac -cp {{deps}} -o target/ src/*.java",
    "test": "java -cp {{deps}} TestRunner",
    "run": "java -cp {{deps}} HelloWorld",
    "clean": "rm -f target/*.class",
    "package": "jar cf my-app.jar -C target/ ."
  }
}
```

### Executing scripts:

You can execute scripts using the `jam run` command:

```bash
$ jam run --list                 # Lists all available actions
$ jam run build                  # Runs the build action
$ jam run run --arg foo -a bar   # Passes "foo" and "bar" to the run action
$ jam run build -a --verbose run -a fubar   # Passes "--verbose" to build and "fubar" to run
```
Or use the convenient alias commands that exist especially for "clean", "build", "test" and "run" scripts:

```bash
$ jam build        # Executes the 'build' action
$ jam run          # Executes the 'run' action
$ jam test         # Executes the 'test' action
$ jam clean        # Executes the 'clean' action
$ jam package      # Executes the 'package' action
```

### Variable Substitution
Scripts support several variable substitution features for cross-platform compatibility:

* {{deps}} - Replaced with the full classpath of all dependencies
* {/} - Replaced with the file separator (\ on Windows, / on Linux/Mac)
* {:} - Replaced with the path separator (; on Windows, : on Linux/Mac)
* {~} - Replaced with the user's home directory (The actual path on Windows, ~ on Linux/Mac)
* {./path/to/file} - Converts relative paths to platform-specific format
* {./libs:./ext:~/usrlibs} - Converts entire class paths to platform-specific format

Example with cross-platform compatibility:

```json
{
  ...

  "scripts": {
    "build": "javac -cp {{deps}} -d {./target/classes} src{/}*.java",
    "run": "java -cp {{deps}}{:}{./target/classes} Main",
    "test": "java -cp {{deps}}{:}{./target/classes} org.junit.runner.JUnitCore TestSuite",
    "package": "jar cf {./target/my-app.jar} -C {./target/classes} ."
  }
}
```

## Commands:

Commands are implemented as subcommands of the main `jam` command. Each command has its own options and arguments.

### Common Options

These options are supported by all `jam` commands:

```
      --config=<configFile>
                        Path to user configuration file (default: JAM_CONFIG
                        environment variable, ~/.jam/config.json, or
                        ~/.jamcfg.json)
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
                        (default: value of JAM_CACHE environment variable;
                        whatever is set in Maven's settings.xml or
                        $HOME/.m2/repository
  -d, --directory=<directory>
                        Directory to copy artifacts to (defaults to `./lib`)
  -L, --local-only        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository to use when resolving
                        artifacts. Can be preceded by a name and an equals
                        sign, e.g. -r myrepo=https://my.repo.com/maven2.
                        When needing to pass user and password you can set
                        JAM_REPO_<name>_USER and JAM_REPO_<name>_PASSWORD
                        environment variables.
  -q, --quiet           Don't output non-essential information
  -v, --verbose         Enable verbose output for debugging
```

### Main Command

```
Usage: jam [-hvV] [COMMAND]

Options:
  -h, --help      Show this help message and exit.
  -v, --verbose   Enable verbose output for debugging
  -V, --version   Print version information and exit.

Commands:
  search   Search for Maven artifacts in repositories.
  install  Install artifacts and add them to project. dependencies.
  path     Print the classpath for the specified artifacts or project.json dependencies.
  run      Execute an action defined in project.json.
```

#### search (alias: s)

Search for Maven artifacts in repositories.

```
Usage: jam search [-iLqv] [-p=<projectFile>] [-c=<cacheDir>]
                  [-d=<directory>] [-m=<max>] [-r=<repositories>]...
                  artifactPattern

Parameters:
  artifactPattern       Partial or full artifact name to search for.

Options:
  -i, --interactive     Interactively search and select artifacts to install
  -m, --max=<max>       Maximum number of results to return
  -p, --project=<projectFile>
                        Project file to use (default './project.json')

Example:
  jam search httpclient
```

#### install (alias: i)

Install artifacts and add them to project.json dependencies.

```
Usage: jam install [-Lqv] [-p=<projectFile>] [-c=<cacheDir>] [-d=<directory>]
                   [-r=<repositories>]... [artifacts...]

Parameters:
  [artifacts...]        One or more artifacts to resolve. Artifacts have the
                        format <group>:<artifact>[:<extension>
                        [:<classifier>]]:<version>

Options:
  -p, --project=<projectFile>
                        Project file to use (default './project.json')

Example:
  jam install org.apache.httpcomponents:httpclient:4.5.14
  jam install                  # Install dependencies from project.json
```

#### path (alias: p)

Print the classpath for the specified artifacts or project.json dependencies.

```
Usage: jam path [-Lv] [-p=<projectFile>] [-c=<cacheDir>] [-d=<directory>]
                [-r=<repositories>]... [artifacts...]

Parameters:
  [artifacts...]        One or more artifacts to resolve. Artifacts have the
                        format <group>:<artifact>[:<extension>
                        [:<classifier>]]:<version>

Options:
  -p, --project=<projectFile>
                        Project file to use (default './project.json')

Example:
  jam path org.apache.httpcomponents:httpclient:4.5.14
  jam path             # Print classpath from project.json dependencies
```

#### run (alias: r)

Execute an action script defined in project.json.

```
Usage: jam run [-lLqv] [-p=<projectInfoFile>] [-c=<cacheDir>] [-d=<directory>]
              [-r=<repositories>]... [script...] [scriptsAndArguments...]

Parameters:
  [script...]           Name of the script to execute as defined in project.json's "scripts" section. Can specify multiple scripts to run sequentially.
  [scriptsAndArguments...]
                        Optional additional scripts and/or arguments to be
                        passed to the script(s)

Options:
  -l, --list            List all available scripts defined in project.json
  -p, --project=<projectFile>
                        Project file to use (default './project.json')

Example:
  jam run --list                # List all scripts defined in project.json
  jam run build                 # Execute the build script
  jam run test --arg verbose    # Pass 'verbose' arg to test script
  jam run build -a --fresh test -a verbose  # Chain scripts
```

You can also use convenient alias commands that exist especially for "clean", "build", "test" and "run" scripts:

```bash
Usage: jam clean [args...]
Usage: jam build [args...]
Usage: jam run [args...]
Usage: jam test [args...]

Parameters:
  [args...]             Optional arguments to pass to the script

Options:
  -p, --project=<projectFile>
                        Project file to use (default './project.json')

Example:
  jam build
  jam run --verbose debug
  jam test
```

Global options may be placed before a command or command chain and apply to the complete invocation:

```bash
jam --quiet clean build package
jam --project app.json --repo corp=https://repo.example install clean build package
```

The parameterless commands `clean`, `build`, `test`, and `package` can be chained and execute from left to right. Execution stops when a command fails. `upgrade` is standalone because it replaces the underlying jam installation; it cannot be combined with another command. Global options must appear before the first command, so `jam upgrade --quiet` is invalid while `jam --quiet upgrade` is valid.
