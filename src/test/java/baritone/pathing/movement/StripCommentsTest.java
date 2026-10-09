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

package baritone.pathing.movement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code ClickGatesTest#stripComments} is what a count of the questions a file asks is made from, so a
 * stripper that takes code with the comments, or leaves a comment's neighbours joined into a name that was
 * never written, is a count that is wrong without anything saying so. Each case here is one way the helper
 * used to go wrong or one way a literal-aware one could: the line break after a line comment, a comment
 * opener inside a string, a quote inside a character, a text block, an escaped quote and a stray one.
 */
public class StripCommentsTest {

    private static String strip(String source) {
        return ClickGatesTest.stripComments(source);
    }

    @Test
    public void aLineCommentLeavesItsLineBreakSoNothingIsJoined() {
        assertEquals("a();\nb();", strip("a();// note\nb();"));
        assertEquals("foo\nbar();", strip("foo// c\nbar();"));
    }

    @Test
    public void aBlockCommentLeavesASpaceSoNothingIsJoined() {
        assertEquals("a b", strip("a/**/b"));
        assertEquals("x();", strip("/** it's a note, and it doesn't count */ x();").trim());
    }

    @Test
    public void aLineCommentOpenerInAStringIsNotAComment() {
        final String url = "log(\"see https://x.y\"); click(Input.CLICK_LEFT);";
        assertEquals(url, strip(url));
    }

    @Test
    public void aBlockCommentOpenerInAStringDoesNotSwallowTheCodeAfterIt() {
        final String stripped = strip("String g = \"/*\"; click(Input.CLICK_RIGHT);\nint z; /* later */");
        assertTrue(stripped, stripped.contains("click(Input.CLICK_RIGHT);"));
        assertTrue(stripped, stripped.contains("int z;"));
        assertFalse(stripped, stripped.contains("later"));
    }

    @Test
    public void aCharacterLiteralDoesNotOpenAString() {
        assertEquals("char slash = '/'; char quote = '\"'; char tick = '\\''; int x = 1; \nint y = 2;",
                strip("char slash = '/'; char quote = '\"'; char tick = '\\''; int x = 1; // gone\nint y = 2;"));
    }

    @Test
    public void aTextBlockIsKeptWhole() {
        final String block = "String t = \"\"\"\n  // not a comment\n  /* nor this */\n  \"\"\"; call(); ";
        assertEquals(block, strip(block + "// gone"));
    }

    @Test
    public void anEscapedQuoteDoesNotCloseAString() {
        assertEquals("String s = \"a\\\"//b\"; x(); \ny();",
                strip("String s = \"a\\\"//b\"; x(); // gone\ny();"));
    }

    @Test
    public void aStrayQuoteDoesNotEatTheFile() {
        final String stripped = strip("String s = \"oops;\nclick(Input.CLICK_LEFT); // gone\n");
        assertTrue(stripped, stripped.contains("click(Input.CLICK_LEFT);"));
        assertFalse(stripped, stripped.contains("gone"));
    }

    @Test
    public void textWithNoCommentsIsReturnedAsItWas() {
        final String source = "final int x = a / b * c;\nif (x > 0) { go(\"x\", 'y'); }\n";
        assertEquals(source, strip(source));
    }
}
