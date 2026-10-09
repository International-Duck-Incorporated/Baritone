/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * {@code wiki/Settings.md} prints a table of setting names and their defaults. A reader who follows it is
 * typing those names into chat and setting those values, so a wrong row is not a documentation nit: a name
 * that does not exist is a command that errors, and a wrong default tells somebody what to change a number
 * <em>from</em> when the number they have is something else.
 * <p>
 * Eleven rows were wrong when this test was written - seven defaults, two settings that {@code Settings.java}
 * does not declare at all, one setting printed with the wrong type, and one item list missing an entry. The
 * worst was {@code movementTimeoutTicks} printed as 10 where the source says 100: a reader who "restored the
 * documented default" would have given every movement one tenth of its time and then wondered why the bot
 * kept giving up. Nothing caught any of it, because the page was prose and the source was Java and nothing
 * compared them.
 * <p>
 * So the page's rows are read and checked against {@code src/api/java/baritone/api/Settings.java}. The price
 * is that a default has to be written the way the source writes it - {@code 500} for {@code 500L},
 * {@code 0,0,0} for {@code new Vec3i(0, 0, 0)}, the block names for an item list - and that a default in a
 * shape this test does not yet know fails the build asking for the shape to be taught here, rather than being
 * skipped quietly. A row that cannot be checked is a row that can be wrong.
 */
public class SettingsWikiTest {

    private static final String WIKI = "wiki/Settings.md";

    private static final String SOURCE = "src/api/java/baritone/api/Settings.java";

    /**
     * A table row of the shape {@code | `name` | default | description |}. The description is not read.
     */
    private static final Pattern ROW = Pattern.compile("^\\|\\s*`(\\w+)`\\s*\\|\\s*([^|]*?)\\s*\\|");

    /**
     * A declared setting and its default expression. The type argument is matched allowing three levels of
     * nesting, because {@code Setting<List<Item>>} would end the match at the first {@code >} and
     * {@code Setting<Map<Block, List<Block>>>} - which {@code buildSubstitutes} and
     * {@code buildValidSubstitutes} are - would end it two early. Missing a declaration is worse than missing
     * a row: it makes the first test call a real setting invented, which is the exact mistake this test
     * exists to catch in the other direction. {@link #declaredDefaults} counts the declarations it parsed
     * against the number of {@code public final Setting<} in the file, so a fourth level of nesting or a
     * changed declaration shape fails the build instead of quietly shrinking the set of known settings.
     */
    private static final Pattern DECLARATION = Pattern.compile(
            "public final Setting<(?:[^<>]|<(?:[^<>]|<(?:[^<>]|<[^<>]*>)*>)*>)*>\\s+(\\w+)\\s*=\\s*new Setting<>\\((.*?)\\)\\s*;",
            Pattern.DOTALL);

    /**
     * How the file spells a setting declaration, counted separately so the pattern above can be held to it.
     */
    private static final String DECLARATION_START = "public final Setting<";

    /**
     * A default written as a whole number, which the source may suffix with {@code L}.
     */
    private static final Pattern NUMBER = Pattern.compile("(-?\\d+)L?");

    private static final Pattern VEC3I = Pattern.compile("new Vec3i\\((.*)\\)");

    private static final Pattern BLOCK_ITEM = Pattern.compile("Blocks\\.(\\w+)\\.asItem\\(\\)");

    @Test
    public void everySettingNamedInTheWikiIsDeclaredInTheSource() {
        final Map<String, String> declared = declaredDefaults();
        final TreeSet<String> invented = new TreeSet<>();
        for (Map.Entry<String, String> row : wikiRows().entrySet()) {
            if (!declared.containsKey(row.getKey())) {
                invented.add(row.getKey());
            }
        }
        assertTrue("the wiki names settings that Settings.java does not declare: " + invented + ". A reader"
                        + " types these into chat and gets nothing back. Either the name here is wrong - check"
                        + " for a near miss in Settings.java, since that is how both of the originals happened -"
                        + " or the setting was removed and the row should go with it.",
                invented.isEmpty());
    }

    @Test
    public void everyDefaultInTheWikiIsTheDefaultInTheSource() {
        final Map<String, String> declared = declaredDefaults();
        final StringBuilder wrong = new StringBuilder();
        for (Map.Entry<String, String> row : wikiRows().entrySet()) {
            final String source = declared.get(row.getKey());
            if (source == null) {
                continue; // the first test owns names that do not exist
            }
            final String normalised = normalise(row.getKey(), source);
            if (!normalised.equals(row.getValue())) {
                wrong.append("\n  ").append(row.getKey()).append(": the wiki says \"").append(row.getValue())
                        .append("\", the source says \"").append(normalised).append("\"");
            }
        }
        assertEquals("the wiki prints a default the source does not agree with." + wrong + "\nWrite the default"
                        + " the way the source writes it: a whole number without the L, a Vec3i as its three"
                        + " components joined by commas, an item list as its block names in lower case. A"
                        + " reader takes this column as the number they are changing from.",
                0, wrong.length());
    }

    @Test
    public void theWikiStillHasRowsForThisTestToBeChecking() {
        assertTrue("no setting rows were found in " + WIKI + ", so both tests above are passing against an"
                        + " empty page. Either the tables were reformatted - in which case the row pattern in"
                        + " this test needs following, not the tests deleted - or the page was rewritten and"
                        + " the defaults it used to guarantee are gone with it.",
                wikiRows().size() >= 30);
    }

    /**
     * The rows of the wiki's tables, in the order they appear, as name to printed default.
     */
    private static Map<String, String> wikiRows() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final Map<String, String> rows = new LinkedHashMap<>();
        for (String line : read(root.resolve(WIKI))) {
            final Matcher row = ROW.matcher(line);
            if (row.find()) {
                rows.put(row.group(1), row.group(2).replace("`", "").trim());
            }
        }
        return rows;
    }

    /**
     * Every setting the source declares, as name to the default expression exactly as written.
     */
    private static Map<String, String> declaredDefaults() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final String source = String.join("\n", read(root.resolve(SOURCE)));
        final Map<String, String> declared = new LinkedHashMap<>();
        final Matcher declaration = DECLARATION.matcher(source);
        while (declaration.find()) {
            declared.put(declaration.group(1), declaration.group(2).trim());
        }
        int spelled = 0;
        for (int at = source.indexOf(DECLARATION_START); at >= 0; at = source.indexOf(DECLARATION_START, at + 1)) {
            spelled++;
        }
        assertEquals("the declaration pattern in this test parsed " + declared.size() + " settings out of the "
                        + spelled + " times \"" + DECLARATION_START + "\" is spelled in " + SOURCE + ", so it is"
                        + " missing some. A setting this test does not know about is a setting it would call"
                        + " invented the moment the wiki documents it, which is the mistake the test exists to"
                        + " catch in the other direction - so this is a build failure rather than a shrug. The"
                        + " usual cause is a new depth of nesting in a type argument, or a declaration written"
                        + " in a shape the pattern does not cover.",
                spelled, declared.size());
        assertTrue("no settings were parsed out of " + SOURCE + " at all, which means either the file moved or"
                        + " the pattern no longer matches how it is written. That is a change worth following,"
                        + " not a reason to stop checking the wiki against it.",
                !declared.isEmpty());
        return declared;
    }

    /**
     * The source's default expression, written the way the wiki is required to write it.
     */
    private static String normalise(String name, String source) {
        final Matcher vec = VEC3I.matcher(source);
        if (vec.matches()) {
            final StringBuilder joined = new StringBuilder();
            for (String component : vec.group(1).split(",")) {
                if (joined.length() > 0) {
                    joined.append(',');
                }
                joined.append(component.trim());
            }
            return joined.toString();
        }
        final Matcher block = BLOCK_ITEM.matcher(source);
        if (block.find()) {
            final StringBuilder names = new StringBuilder();
            do {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(block.group(1).toLowerCase());
            } while (block.find());
            return names.toString();
        }
        final Matcher number = NUMBER.matcher(source);
        if (number.matches()) {
            return number.group(1);
        }
        if (source.equals("true") || source.equals("false")) {
            return source;
        }
        throw new AssertionError("the default for " + name + " is written as \"" + source + "\", which this"
                + " test does not know how to compare against a table row. Teach it the shape here rather than"
                + " leaving the row unchecked - a default this test skips is a default that can be wrong in"
                + " the wiki with the build staying green.");
    }

    private static java.util.List<String> read(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The checkout, found by walking up from wherever this test was started: Gradle runs from the project
     * directory and so does the test runner beside the build scripts, but nothing guarantees either.
     */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
