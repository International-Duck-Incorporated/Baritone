# Building from Source

## Requirements

- Java 25 (Temurin recommended)
- Git
- ~4 GB RAM available for Gradle (or use the constrained recipe below)

## Quick Build

```bash
git clone https://gitlab.com/international-duck-incorporated/Baritone.git
cd Baritone
git checkout 26.3
./gradlew build
```

Artifacts are in `dist/`.

## Branches

| Branch | Minecraft | Checkout |
|--------|-----------|----------|
| `26.1` | 26.1, 26.1.1, 26.1.2 | `git checkout 26.1` |
| `26.2` | 26.2 | `git checkout 26.2` |
| `26.3` | 26.3 | `git checkout 26.3` |

## Memory-Constrained Builds

The project's default `gradle.properties` sets `-Xmx4G`, which kills a box with less than 4 GB RAM. Override it on the command line:

```bash
./gradlew build \
  -Dorg.gradle.jvmargs="-Xmx640m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC" \
  --no-daemon \
  --max-workers=1
```

For testing, use an init script to cap test forks:

```groovy
// /home/user/.cache/heap.init.gradle
allprojects {
    tasks.withType(Test) {
        maxHeapSize = '384m'
        minHeapSize = '64m'
        jvmArgs '-XX:+UseSerialGC', '-XX:MaxMetaspaceSize=160m'
        maxParallelForks = 1
    }
}
```

Then:

```bash
./gradlew test \
  -Dorg.gradle.jvmargs="-Xmx640m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC" \
  --no-daemon \
  --max-workers=1 \
  --init-script /home/user/.cache/heap.init.gradle
```

Cache directories on disk (not `/tmp`, which is a tmpfs on many boxes):

```bash
export GRADLE_USER_HOME=/home/user/.cache/gradle-home
./gradlew test --project-cache-dir /home/user/.cache/gradle-project-cache
```

## Install Java 25

If your system does not have Java 25:

```bash
mkdir -p /home/user/.cache
curl -sL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk25.tar.gz
tar xzf /tmp/jdk25.tar.gz -C /home/user/.cache
mv /home/user/.cache/jdk-25* /home/user/.cache/jdk25
export JAVA_HOME=/home/user/.cache/jdk25
export PATH="$JAVA_HOME/bin:$PATH"
```

## Build Artifacts

> The task and classifier names here are read out of each loader's `build.gradle`, which is the source of
> truth for them.

This fork builds one jar per loader rather than upstream's api / standalone / unoptimized trio; those three
names do not appear anywhere in the build. The loader modules are `fabric`, `forge`, `neoforge` and
`tweaker`, the archive base name is `baritone` (`gradle.properties`, with `neoforge` appending `-neoforge`),
and each module declares three variants:

| Task | Classifier | Description |
|------|-----------|-------------|
| `remapJar` | none | The remapped jar, built from the shadow jar's output. This is the one a loader loads. |
| `shadowJar` | `dev-shadow` | Dependencies shadowed in, not remapped. What `scripts/headless/README.md` drives. |
| `jar` | `dev` | The plain compiled jar, for debugging against sources. |

So `./gradlew :fabric:shadowJar` produces the Fabric `dev-shadow` jar - the invocation
`scripts/headless/README.md` is written around, and a known-good one: that harness has run this tree in a
real client.

Each is available for Forge, Fabric, and NeoForge loaders.

## Running Tests

```bash
./gradlew test
```

Test results are in `build/test-results/test/TEST-*.xml`. Always read counts from the XML, never from piped console output.

## Troubleshooting

### TLS / Dependency Resolution Failures

Put `GRADLE_USER_HOME` on disk, not in `/tmp`:

```bash
export GRADLE_USER_HOME=/home/user/.cache/gradle-home
```

### Out of Memory

Override the JVM args as shown above. The project's `-Xmx4G` is the line that kills small boxes.

### False "Cannot Find Symbol" Errors

Never `git pull --rebase` while a Gradle build is compiling. It produces false errors that look like a half-applied patch. Check `git status` and `git log -1` first.

### Swap

If `swapon` is not permitted on your box, the constrained build recipe above with `-Xmx640m` works without swap on a 1.9 GB box.

## IDE Setup

The project uses Unimined for Minecraft dependency management. Import as a Gradle project in your IDE. The Minecraft source is available in the unimined cache after any build:

```
caches/unimined/net/minecraft/minecraft/26.3/.../minecraft-26.3-official.jar
```

Class files can be read with `javap` from this jar.
