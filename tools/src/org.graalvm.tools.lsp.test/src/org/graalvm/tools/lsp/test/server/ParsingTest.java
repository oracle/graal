package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.junit.Test;

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

                assertEquals("function main() {return 3+3+4;}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToDelete = "";
                TextDocumentContentChangeEvent deletionEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 24), new Position(0, 26)), textToDelete.length(), textToDelete);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(deletionEvent), uri);
                future.get();

                assertEquals("function main() {return 3+4;}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToReplaceSingleLine = "\n  return 42;\n}";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 17), new Position(0, 29)), textToReplaceSingleLine.length(),
                                textToReplaceSingleLine);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertEquals("function main() {\n  return 42;\n}", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToInsertAtEnd = "\n";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(2, 1), new Position(2, 1)), textToInsertAtEnd.length(), textToInsertAtEnd);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertEquals("function main() {\n  return 42;\n}\n", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToInsertAtNewLineTerminatedLine = " ";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(3, 0), new Position(3, 0)), textToInsertAtNewLineTerminatedLine.length(),
                                textToInsertAtNewLineTerminatedLine);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

                assertEquals("function main() {\n  return 42;\n}\n ", surrogate.getEditorText());
                assertEquals(surrogate.getEditorText(), surrogate.getEditorText());
            }

            {
                String textToReplaceMultiLine = "{return 1;}";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 16), new Position(3, 1)), textToReplaceMultiLine.length(),
                                textToReplaceMultiLine);
                Future<?> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                future.get();

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

    @Test
    public void parseingWithSyntaxErrors() throws InterruptedException, ExecutionException {
        {
            URI uri = createDummyFileUriForSL();
            String text = "function main() {return 3+;}";

            try {
                Future<?> future = truffleAdapter.parse(text, "sl", uri);
                future.get();
                assertTrue(false);
            } catch (RuntimeException e) {
                DiagnosticsNotification diagnosticsNotification = getDiagnosticsNotification(e);
                Collection<PublishDiagnosticsParams> diagnosticParamsCollection = diagnosticsNotification.getDiagnosticParamsCollection();
                assertEquals(1, diagnosticParamsCollection.size());
                PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
                assertEquals(1, diagnosticsParams.getDiagnostics().size());
                assertEquals(new Range(new Position(0, 26), new Position(0, 27)), diagnosticsParams.getDiagnostics().get(0).getRange());
            }
        }
    }
}
