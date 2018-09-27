package de.hpi.swa.trufflelsp.server;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Test;

import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class DefinitionTest extends TruffleLSPTest {

    @Test
    public void gotoDefinitionForFunctions() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        //@formatter:off
        /**
         *  0 function main() {
         *  1     abc();
         *  2     x = abc();
         *  3 }
         *  4
         *  5 function abc() {
         *  6   obj = new();
         *  7   obj.p = 1;
         *  8   return obj;
         *  9 }
         * 10
         */
        //@formatter:on
        String text = "function main() {\n    abc();\n    x = abc();\n}\n\nfunction abc() {\n  obj = new();\n  obj.p = 1;\n  return obj;\n}\n";
        Future<TextDocumentSurrogate> futureOpen = truffleAdapter.didOpen(uri, text, "sl");
        futureOpen.get();

        Range abcRange = new Range(new Position(5, 9), new Position(9, 1));

        int line = 1;
        {
            for (int i = 4; i <= 9; i++) {
                Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, i);
                List<? extends Location> definitions = future.get();
                assertEquals(1, definitions.size());
                Location location = definitions.get(0);
                assertEquals(abcRange, location.getRange());
            }
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 3);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 10);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        line = 2;
        {
            for (int i = 8; i <= 13; i++) {
                Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, i);
                List<? extends Location> definitions = future.get();
                assertEquals(1, definitions.size());
                Location location = definitions.get(0);
                assertEquals(abcRange, location.getRange());
            }
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 7);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 14);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        line = 6;
        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 9);
            List<? extends Location> definitions = future.get();
            // new() is built-in and has no SourceSection
            assertEquals(0, definitions.size());
        }

        // check edge-cases / out-of bounds
        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 0, 0);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 10, 0);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 10, 1);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 11, 1);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }
    }
}
