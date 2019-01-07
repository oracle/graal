package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.SignatureHelp;
import org.junit.Test;

public class SignatureHelpTest extends TruffleLSPTest {

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
        Future<?> futureOpen = truffleAdapter.parse(text, "sl", uri);
        futureOpen.get();

        Future<SignatureHelp> futureSignatureHelp = truffleAdapter.signatureHelp(uri, 1, 7);
        SignatureHelp signatureHelp = futureSignatureHelp.get();
        // SL is not supporting GET_SIGNATURE message yet
        assertEquals(0, signatureHelp.getSignatures().size());
    }
}
