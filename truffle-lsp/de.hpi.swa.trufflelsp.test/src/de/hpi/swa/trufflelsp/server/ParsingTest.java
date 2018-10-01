package de.hpi.swa.trufflelsp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.Test;

import de.hpi.swa.trufflelsp.exceptions.UnknownLanguageException;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class ParsingTest extends TruffleLSPTest {

    @Test
    public void didOpenClose() {
        URI uri = createDummyFileUriForSL();
        String text = "function main() {return 3+3;}";
        truffleAdapter.parse(text, "sl", uri);
        truffleAdapter.didClose(uri);
    }

    @Test(expected = UnknownLanguageException.class)
    public void unknownlanguage() throws InterruptedException, ExecutionException {
        URI uri = URI.create("file:///tmp/truffle-lsp-test-file-unknown-lang-id");

        Future<?> future = truffleAdapter.parse("", "unknown-lang-id", uri);
        future.get();
    }

    @Test()
    public void unknownlanguageIdButMIMETypeFound() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();

        Future<?> future = truffleAdapter.parse("function main() {return 42;}", "unknown-lang-id", uri);
        future.get();
    }

    @Test
    public void parse() throws InterruptedException, ExecutionException {
        {
            URI uri = createDummyFileUriForSL();
            String text = "function main() {return 3+3;}";
            Future<?> future = truffleAdapter.parse(text, "sl", uri);
            future.get();

            assertTrue(diagnostics.isEmpty());
        }

        // TODO(ds) failing, see https://github.com/graalvm/simplelanguage/issues/40
// {
// URI uri = createDummyFileUri();
// String text = "function main";
// truffleAdapter.didOpen(uri, text, "sl");
//
// Future<Void> future = truffleAdapter.parse(text, "sl", uri);
// future.get();
//
// assertEquals(1, diagnostics.size());
// assertTrue(diagnostics.get(uri).get(0).getMessage().contains("EOF"));
// }

        {
            diagnostics.clear();

            TextDocumentSurrogate surrogate;
            URI uri = createDummyFileUriForSL();

            {
                String text = "function main() {return 3+3;}";
                Future<?> future = truffleAdapter.parse(text, "sl", uri);
                future.get();
            }

            {
                String textToInsert = "+4";
                TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent(new Range(new Position(0, 27), new Position(0, 27)), textToInsert.length(), textToInsert);
                Future<TextDocumentSurrogate> future = truffleAdapter.processChangesAndParse(Arrays.asList(event), uri);
                surrogate = future.get();

                assertTrue(diagnostics.isEmpty());
                assertEquals("function main() {return 3+3+4;}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToDelete = "";
                TextDocumentContentChangeEvent deletionEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 24), new Position(0, 26)), textToDelete.length(), textToDelete);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(deletionEvent), uri);
                future.get();

                assertTrue(diagnostics.isEmpty());
                assertEquals("function main() {return 3+4;}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToReplaceSingleLine = "\n  return 42;\n}";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 17), new Position(0, 29)), textToReplaceSingleLine.length(),
                                textToReplaceSingleLine);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertTrue(diagnostics.isEmpty());
                assertEquals("function main() {\n  return 42;\n}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToInsertAtEnd = "\n";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(2, 1), new Position(2, 1)), textToInsertAtEnd.length(), textToInsertAtEnd);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertTrue(diagnostics.isEmpty());
                assertEquals("function main() {\n  return 42;\n}\n", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToInsertAtNewLineTerminatedLine = " ";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(3, 0), new Position(3, 0)), textToInsertAtNewLineTerminatedLine.length(),
                                textToInsertAtNewLineTerminatedLine);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertTrue(diagnostics.isEmpty());
                assertEquals("function main() {\n  return 42;\n}\n ", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToReplaceMultiLine = "{return 1;}";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 16), new Position(3, 1)), textToReplaceMultiLine.length(),
                                textToReplaceMultiLine);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertTrue(diagnostics.isEmpty());
                assertEquals("function main() {return 1;}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            // TODO(ds) failing, see https://github.com/oracle/graal/pull/555
            /*
             * { String textToReplaceEmpty = ""; TextDocumentContentChangeEvent replaceEvent = new
             * TextDocumentContentChangeEvent(new Range(new Position(0, 0), new Position(0, 30)),
             * textToReplaceEmpty.length(), textToReplaceEmpty); Future<Void> future =
             * truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
             * future.get();
             *
             * assertTrue(diagnostics.isEmpty()); assertEquals("", surrogate.getEditorText());
             * assertEquals(surrogate.getEditorText(), surrogate.getEditorText()); }
             */
        }
    }
}
