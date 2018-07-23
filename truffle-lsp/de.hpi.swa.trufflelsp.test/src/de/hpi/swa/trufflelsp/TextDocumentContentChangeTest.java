package de.hpi.swa.trufflelsp;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.Test;

public class TextDocumentContentChangeTest {

    private static void assertDocumentChanges(String oldText, String replacement, Range range, String expectedText) {
        TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent(range, replacement.length(), replacement);
        String actualText = TruffleAdapter.applyTextDocumentChanges(Arrays.asList(event), oldText, null);
        assertEquals(expectedText, actualText);
    }

    @Test
    public void applyTextDocumentChanges01() {
        assertDocumentChanges("", "a", new Range(new Position(0, 0), new Position(0, 0)), "a");
    }

    @Test
    public void applyTextDocumentChanges02() {
        assertDocumentChanges("a", "b", new Range(new Position(0, 0), new Position(0, 1)), "b");
    }

    @Test
    public void applyTextDocumentChanges03() {
        assertDocumentChanges("abc", "1", new Range(new Position(0, 1), new Position(0, 1)), "a1bc");
    }

    @Test
    public void applyTextDocumentChanges04() {
        assertDocumentChanges("\n", "1", new Range(new Position(0, 0), new Position(1, 0)), "1");
    }

    @Test
    public void applyTextDocumentChanges05() {
        assertDocumentChanges("abc\nefg\n\nhij", "#", new Range(new Position(0, 0), new Position(1, 0)), "#efg\n\nhij");
    }

    @Test
    public void applyTextDocumentChanges06() {
        assertDocumentChanges("abc\nefg\n\nhij", "#", new Range(new Position(2, 0), new Position(3, 0)), "abc\nefg\n#hij");
    }

    @Test
    public void applyTextDocumentChanges07() {
        assertDocumentChanges("abc\nefg\n\n", "#\n", new Range(new Position(3, 0), new Position(3, 0)), "abc\nefg\n\n#\n");
    }

    @Test
    public void applyTextDocumentChanges08() {
        assertDocumentChanges("abc\nefg\n\n", "a", null, "a");
    }

    @Test
    public void applyTextDocumentChangesList() {
        String oldText = "";

        String replacement1 = "a";
        TextDocumentContentChangeEvent event1 = new TextDocumentContentChangeEvent(new Range(new Position(0, 0), new Position(0, 0)), replacement1.length(), replacement1);
        String replacement2 = "\nefg\nhij";
        TextDocumentContentChangeEvent event2 = new TextDocumentContentChangeEvent(new Range(new Position(0, 1), new Position(0, 1)), replacement2.length(), replacement2);
        String replacement3 = "####";
        TextDocumentContentChangeEvent event3 = new TextDocumentContentChangeEvent(new Range(new Position(1, 0), new Position(2, 0)), replacement3.length(), replacement3);
        String replacement4 = "\n";
        TextDocumentContentChangeEvent event4 = new TextDocumentContentChangeEvent(new Range(new Position(1, 7), new Position(1, 7)), replacement4.length(), replacement4);

        String actualText = TruffleAdapter.applyTextDocumentChanges(Arrays.asList(event1, event2, event3, event4), oldText, null);
        assertEquals("a\n####hij\n", actualText);
    }
}
