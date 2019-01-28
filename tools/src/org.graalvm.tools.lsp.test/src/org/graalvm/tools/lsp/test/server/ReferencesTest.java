package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Location;
import org.junit.Test;

public class ReferencesTest extends TruffleLSPTest {

    @Test
    public void findAllReferencesForFunctions() throws InterruptedException, ExecutionException {
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
        Future<?> futureOpen = truffleAdapter.parse(text, "sl", uri);
        futureOpen.get();

        {
            Future<List<? extends Location>> future = truffleAdapter.references(uri, 1, 4);
            List<? extends Location> definitions = future.get();
            assertEquals(2, definitions.size());
            assertEquals(range(1, 4, 1, 9), definitions.get(0).getRange());
            assertEquals(range(2, 8, 2, 13), definitions.get(1).getRange());
        }
    }

    @Test
    public void findAllReferencesForFunctionsStatic() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        //@formatter:off
        /**
         *  0 function main() {
         *  1     x = 1;
         *  2     return x;
         *  3 }
         *  4
         *  5 function abc() {
         *  6   obj = new();
         *  7   obj.p = 1;
         *  8   return obj;
         *  9 }
         * 10
         * 11 function notCalled() {
         * 12   abc();
         * 13   return abc();
         * 14 }
         */
        //@formatter:on
        String text = "function main() {\n    x = 1;\n    return x;\n}\n\nfunction abc() {\n  obj = new();\n  obj.p = 1;\n  return obj;\n}\n\nfunction notCalled() {\n  abc();\n  return abc();\n}";
        Future<?> futureOpen = truffleAdapter.parse(text, "sl", uri);
        futureOpen.get();

        {
            Future<List<? extends Location>> future = truffleAdapter.references(uri, 13, 10);
            List<? extends Location> definitions = future.get();
            assertEquals(2, definitions.size());
            assertEquals(range(12, 2, 12, 7), definitions.get(0).getRange());
            assertEquals(range(13, 9, 13, 14), definitions.get(1).getRange());
        }
    }
}
