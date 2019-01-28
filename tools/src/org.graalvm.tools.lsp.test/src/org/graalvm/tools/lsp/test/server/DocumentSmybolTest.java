package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.junit.Test;

public class DocumentSmybolTest extends TruffleLSPTest {

    @Test
    public void documentSymbol() throws InterruptedException, ExecutionException {
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

        Future<List<? extends SymbolInformation>> futureSymbol = truffleAdapter.documentSymbol(uri);
        List<? extends SymbolInformation> symbols = futureSymbol.get();

        assertEquals(2, symbols.size());

        Optional<? extends SymbolInformation> symbolOptMain = symbols.stream().filter(symbol -> symbol.getName().equals("main")).findFirst();
        assertTrue(symbolOptMain.isPresent());
        Range rangeMain = new Range(new Position(0, 9), new Position(3, 1));
        assertEquals(rangeMain, symbolOptMain.get().getLocation().getRange());

        Optional<? extends SymbolInformation> symbolOptAbc = symbols.stream().filter(symbol -> symbol.getName().equals("abc")).findFirst();
        Range rangeAbc = new Range(new Position(5, 9), new Position(9, 1));
        assertEquals(rangeAbc, symbolOptAbc.get().getLocation().getRange());
    }
}
