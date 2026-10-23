# Build Guide

This guide explains how to build raspi-finance-ratpack from source on any machine (Linux, macOS or Windows, x86_64 or ARM64).

## TL;DR

```bash
# 1. Make sure JDK 21 is the active Java (see "Installing JDK 21" below)
java -version          # should print 21.x

# 2. Build (skipping tests)
./gradlew clean build -x test

# 3. Result (executable fat jar)
ls build/libs/raspi-finance-ratpack.jar
```

On Windows, see section 4: the repository has no `gradlew.bat`.

No database, Docker or network service (other than the dependency repositories) is needed to **build** the project.

## 1. Technology stack

| Component     | Version                               | Notes                                                       |
|---------------|---------------------------------------|-------------------------------------------------------------|
| Java (JDK)    | **21**                                | Same as the `Dockerfile` (`eclipse-temurin:21`)             |
| Gradle        | 9.4.1 (via the included wrapper)      | Do not use a system-wide Gradle install                     |
| Groovy        | 3.0.x (`gradle.properties`)           | No separate install needed                                  |
| Ratpack       | 2.0.0-rc-1                            |                                                             |
| jOOQ          | 3.21.x                                | Code generation is off; the build does not connect to a database |
| Database      | PostgreSQL                            | Runtime only                                                |
| Build output  | Executable fat jar (Shadow plugin)    | `build/libs/raspi-finance-ratpack.jar`                      |

All dependency versions are centralized in `gradle.properties` and locked in `gradle.lockfile` (Gradle dependency locking).

**About the Java version:** no Java toolchain is configured, so the project is compiled with the JDK that runs Gradle. The build was verified with JDK 21. Gradle 9 needs JDK 17 or newer to run; use JDK 21 to match the `Dockerfile`.

## 2. Prerequisites

1. **JDK 21.** A full JDK is required, a JRE is not enough. Any vendor works (Eclipse Temurin, Azul Zulu, Amazon Corretto, Microsoft Build of OpenJDK, Oracle, OpenJDK from your OS package manager, etc.).
2. **Internet access** (at least for the first build) to:
   - `services.gradle.org` (Gradle 9.4.1 distribution, only once)
   - `plugins.gradle.org` (Gradle plugins)
   - `repo.maven.apache.org` (Maven Central, dependencies)
3. **About 1 GB of free disk space** for Gradle and the dependency cache.
4. **Git**, to clone the repository.
5. **The file `src/ratpack/db_config.json` must exist.** `build.gradle` reads it while configuring the build (for the jOOQ settings). It is committed to the repository; the build does not connect to the database it points to.

You do **not** need Gradle or Groovy installed (except on Windows without WSL / Git Bash, see section 4).

## 3. Installing JDK 21

Pick the option for your platform. After installing, open a **new** terminal and check with `java -version` (it should report `21.x`).

### Any platform: SDKMAN! (Linux, macOS, WSL)

```bash
curl -s "https://get.sdkman.io" | bash
sdk list java | grep -E "21\.[0-9.]+"       # pick an available JDK 21 identifier
sdk install java <identifier>               # e.g. 21.x.x-tem
sdk use java <identifier>
```

### Linux

- **Debian / Ubuntu:** `sudo apt install openjdk-21-jdk`
- **Fedora / RHEL / CentOS:** `sudo dnf install java-21-openjdk-devel`
- **Arch:** `sudo pacman -S jdk21-openjdk`
- **Manual tarball:** download a JDK 21 `.tar.gz` for your architecture (x64 or aarch64, e.g. Raspberry Pi 64-bit) from <https://adoptium.net>, extract it anywhere and point `JAVA_HOME` to the extracted folder.

If several JDKs are installed, select JDK 21 with `sudo update-alternatives --config java` (Debian/Ubuntu), `sudo alternatives --config java` (Fedora/RHEL), or simply set `JAVA_HOME` for the build (see below).

### macOS (Intel and Apple Silicon)

```bash
brew install --cask temurin@21
/usr/libexec/java_home -V                       # list installed JDKs
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Windows

- **winget:** `winget install EclipseAdoptium.Temurin.21.JDK`
- **Chocolatey:** `choco install temurin21`
- **Manual:** download the JDK 21 `.msi` from <https://adoptium.net> and enable the "Set JAVA_HOME" option in the installer.

### Using JDK 21 only for this build

You don't have to make JDK 21 your default Java. Setting `JAVA_HOME` in the current terminal is enough, because the Gradle wrapper uses it:

```bash
# Linux / macOS / WSL / Git Bash
export JAVA_HOME=/path/to/jdk21
export PATH="$JAVA_HOME/bin:$PATH"
```

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\path\to\jdk21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 4. Building

From the project root.

### Linux / macOS

```bash
# if needed, make the wrapper executable first: chmod +x gradlew
./gradlew clean build -x test
```

### Windows

The repository (like its upstream) only ships the Unix wrapper script `gradlew`, not `gradlew.bat`. Pick one of:

- **WSL** (recommended): install JDK 21 inside WSL and run `./gradlew clean build -x test` there.
- **Git Bash:** with JDK 21 installed on Windows, run `sh gradlew clean build -x test` from Git Bash.
- **Installed Gradle:** install Gradle 9.4.1 (`choco install gradle --version=9.4.1`, `scoop install gradle`, or the zip from <https://gradle.org/releases/>) and run `gradle clean build -x test` from cmd or PowerShell.

### What happens

The first build downloads Gradle 9.4.1 and all dependencies, which takes a few minutes. Later builds take seconds.

**Expected output:**

```
> Task :compileGroovy
> Task :shadowJar
...
> Task :codenarcMain
> Task :codenarcTest
...
BUILD SUCCESSFUL
```

**Artifact:** `build/libs/raspi-finance-ratpack.jar` (about 32 MB), an executable fat jar with all dependencies.

**CodeNarc stack traces are expected.** During `codenarcMain` / `codenarcTest` the console shows many stack traces with `BUG! exception in phase 'semantic analysis' ... Unsupported class file major version 65` and `Compilation error for non-default compiler phase`. CodeNarc's internal Groovy 3 cannot read Java 21 class files, so its "enhanced" rules fail. CodeNarc is configured with `ignoreFailures = true`, so this only affects the lint report and the build still ends with `BUILD SUCCESSFUL`.

### Useful Gradle tasks

| Command                          | What it does                                          |
|----------------------------------|-------------------------------------------------------|
| `./gradlew clean build -x test`  | Full build without running tests (recommended)        |
| `./gradlew shadowJar`            | Build only the fat jar                                |
| `./gradlew compileGroovy`        | Compile `src/main` only (fast compile check)          |
| `./gradlew test`                 | Run the Spock test suite                              |
| `./gradlew run`                  | Run the application (needs database and certificates, see section 6) |
| `./gradlew generateJooq`         | Regenerate jOOQ classes (needs the PostgreSQL database in `db_config.json`) |

## 5. Important: `src/ratpack/Ratpack.groovy` is not compiled by the build

In Ratpack applications, `src/ratpack/Ratpack.groovy` is a **script**: it is copied into the jar as source code and compiled only when the application starts. A syntax error in that file therefore does **not** fail `./gradlew build`; it only shows up at startup.

To check that `Ratpack.groovy` compiles without starting the application, build the jar and compile the script with the Groovy compiler and classpath contained in it:

```bash
./gradlew shadowJar
java -cp build/libs/raspi-finance-ratpack.jar org.codehaus.groovy.tools.FileSystemCompiler \
     -d build/ratpack-script-check src/ratpack/Ratpack.groovy
```

No output and exit code 0 mean the script compiles. On Windows, write the command on one line and use `build\libs\raspi-finance-ratpack.jar`.

## 6. Running the application

The application needs:

- the PostgreSQL database configured in `src/ratpack/db_config.json`;
- TLS certificate files in `ssl/` as configured in `src/ratpack/config.yml` (the `ssl/` folder is git-ignored and not part of the repository);
- port **8443** (HTTPS).

```bash
./gradlew run
# or
java -jar build/libs/raspi-finance-ratpack.jar
```

See `CLAUDE.md` and `deploy-podman.sh` in the repository for more details about the runtime setup.

## 7. Running the tests

```bash
./gradlew test
```

The Spock tests run without a database. At the time of writing, 128 tests run and 1 fails: `finance.service.AccountServiceSpec > accountRename renames and returns updated account`. The test stubs the repository so that the target account always "already exists", so the service correctly refuses the rename. This is a problem in the test itself and does not affect compilation or packaging. Report: `build/reports/tests/test/index.html`.

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | `gradle/wrapper/gradle-wrapper.jar` is missing | Make sure your checkout contains `gradle/wrapper/gradle-wrapper.jar`, then pull or re-clone. |
| `'gradlew' is not recognized` / `gradlew.bat not found` (Windows) | The repository has no `gradlew.bat` | Use WSL, Git Bash or an installed Gradle (section 4). |
| `Gradle requires JVM 17 or later to run` | Gradle 9 is running on JDK 8 or 11 | Set `JAVA_HOME` to JDK 21 (section 3). Check with `./gradlew -version`. |
| `JAVA_HOME is set to an invalid directory` | `JAVA_HOME` points to a non-existent folder or to a JRE | Point it to the root folder of a JDK 21 (the folder that contains `bin/javac`). |
| `FileNotFoundException: .../src/ratpack/db_config.json` at configuration time | The file is missing from your checkout | Restore it from git; `build.gradle` needs it even though the build does not connect to the database. |
| `Resolved 'x:y:a' which is not part of the dependency lock state` / `Dependency lock state ... out of date` | Dependencies were changed without updating `gradle.lockfile` | Regenerate the lock file with `./gradlew dependencies --write-locks` and commit it. |
| Many CodeNarc stack traces (`Unsupported class file major version 65`) | Known CodeNarc limitation with Java 21 | Harmless; see section 4. |
| Error in `Ratpack.groovy` only at startup | The script is compiled at runtime | Check it with the command in section 5. |
| `Could not resolve all dependencies ...` / connection timeouts | No access to Maven Central, the Gradle Plugin Portal or `services.gradle.org` | Check internet / firewall. Behind a proxy, add `systemProp.https.proxyHost`, `systemProp.https.proxyPort` (and the `http.` variants) to `~/.gradle/gradle.properties`. |
| `FileNotFoundException: ~/.gradle/wrapper/dists/gradle-9.4.1-bin/.../gradle-9.4.1-bin.zip` | Corrupted or partial Gradle distribution in the local cache | Delete `~/.gradle/wrapper/dists/gradle-9.4.1-bin` and build again. |
| `Permission denied: ./gradlew` | The execute bit was lost (e.g. ZIP download, Windows checkout) | `chmod +x gradlew` |
| `/bin/sh^M: bad interpreter` | `gradlew` was checked out with Windows (CRLF) line endings | `git config core.autocrlf input` and re-checkout, or run `sed -i 's/\r$//' gradlew`. |
| `java.lang.OutOfMemoryError` during compilation | Not enough heap for the Groovy compiler | Add `org.gradle.jvmargs=-Xmx1g` to `gradle.properties` (project root or `~/.gradle`). |

## 9. Build-related changes made to this repository

The source code and the build configuration were already correct; no source, `build.gradle` or `gradle.properties` change was needed. The only change:

1. **Added `gradle/wrapper/gradle-wrapper.jar`** (the official Gradle 9.4.1 wrapper, SHA-256 `55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c`, matching <https://services.gradle.org/distributions/gradle-9.4.1-wrapper.jar.sha256>). It was missing, so `./gradlew` could not start. The upstream repository has it.
