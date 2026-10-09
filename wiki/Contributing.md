# Contributing

## Repository

**GitLab:** https://gitlab.com/international-duck-incorporated/Baritone

**Branches:**
- `26.1` - Minecraft 26.1
- `26.2` - Minecraft 26.2
- `26.3` - Minecraft 26.3 (active development)

## Getting Started

1. Fork the repository on GitLab
2. Clone your fork
3. Create a feature branch from `26.3`
4. Make your changes
5. Build and test
6. Submit a merge request

## Build and Test

```bash
git checkout 26.3
./gradlew build
./gradlew test
```

Read [Building](Building.md) for memory-constrained environments.

## Code Style

- Follow the existing code style
- Use meaningful variable and method names
- Add Javadoc for public API methods
- Keep methods focused and small

## Humanlike Features

If you are adding a humanlike behavior:

1. Read [HUMANLIKE.md](../HUMANLIKE.md) first
2. Add a row to the appropriate table
3. Write the rule as a Minecraft-free class where possible
4. Write tests with teeth (mutations that break when the rule is removed)
5. Add entries to the appropriate counted-edge test if you write to rotation, slot, buttons, clicks, or keys
6. Record in [AGENTS.md](../AGENTS.md) if coordinating with other agents

### The Counted Edges

Five tests guard the write surfaces:

| Test | Guards |
|------|--------|
| CameraWritesTest | Player rotation |
| HandWritesTest | Selected slot |
| ButtonWritesTest | Button presses and swings |
| ClickGatesTest | Click destinations |
| KeyWritesTest | Movement input |

If your change writes to any of these surfaces, add an entry with a reason and a shape pin.

## Testing

- Run the full suite: `./gradlew test`
- Read counts from `build/test-results/test/TEST-*.xml`, never from piped output
- Check teeth: mutate your rule and verify the test fails
- Green on your box is not enough; check the integrated tree

## Commits

Use meaningful commit messages:

```
humanize: smooth yaw acceleration
humanize: add contextual sprint windup
humanize: reduce abrupt movement reversal
test: cover sprint transition timing
```

Avoid:

```
update
changes
fix
stuff
```

## Patches

Meaningful changes should have a standalone patch in `patches/`:

```bash
git format-patch -1 <commit>
```

Before choosing a number, check existing patches. Patch numbers are shared state.

## Merge Requests

1. One logical change per MR
2. Include tests
3. Update documentation if needed
4. Describe what changed and why
5. Reference related issues

## Issues

Report bugs and feature requests on the [GitLab issue tracker](https://gitlab.com/international-duck-incorporated/Baritone/-/issues).

Include:
- Minecraft version
- Baritone version
- Steps to reproduce
- Expected vs actual behavior
- Crash reports (use the unoptimized jar)

## License

LGPL 3.0 with anime exception. See [LICENSE](../LICENSE).
