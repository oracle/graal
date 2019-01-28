package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.junit.Test;

public class CoverageTest extends TruffleLSPTest {

    @Test
    public void runConverageAnalysisTest() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        //@formatter:off
        /**
         *  0 function main() {
         *  1     x = abc();
         *  2     return x.p;
         *  3 }
         *  4
         *  5 function abc() {
         *  6   obj = new();
         *  7   obj.p = 1;
         *  8   return obj;
         *  9 }
         * 10
         * 11 function notCalled() {
         * 12   return abc();
         * 13 }
         */
        //@formatter:on
        String text = "function main() {\n    x = abc();\n    return x.p;\n}\n\nfunction abc() {\n  obj = new();\n  obj.p = 1;\n  return obj;\n}\n\nfunction notCalled() {\n  return abc();\n}";
        Future<?> futureOpen = truffleAdapter.parse(text, "sl", uri);
        futureOpen.get();

        {
            boolean caught = false;
            try {
                truffleAdapter.showCoverage(uri);
            } catch (RuntimeException e) {
                DiagnosticsNotification diagnosticsNotification = getDiagnosticsNotification(e);
                Collection<PublishDiagnosticsParams> diagnosticParamsCollection = diagnosticsNotification.getDiagnosticParamsCollection();
                assertEquals(1, diagnosticParamsCollection.size());
                PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
                assertEquals(uri.toString(), diagnosticsParams.getUri());
                List<Diagnostic> diagnostics = diagnosticsParams.getDiagnostics();
                assertEquals(6, diagnostics.size());
                assertEquals(range(1, 4, 1, 13), diagnostics.get(0).getRange());
                assertEquals(range(2, 4, 2, 14), diagnostics.get(1).getRange());
                assertEquals(range(6, 2, 6, 13), diagnostics.get(2).getRange());
                assertEquals(range(7, 2, 7, 11), diagnostics.get(3).getRange());
                assertEquals(range(8, 2, 8, 12), diagnostics.get(4).getRange());
                assertEquals(range(12, 2, 12, 14), diagnostics.get(5).getRange());
                caught = true;
            }
            assertTrue(caught);
        }

        {
            Future<Boolean> future = truffleAdapter.runCoverageAnalysis(uri);
            Boolean result = future.get();
            assertTrue(result);

            boolean caught = false;
            try {
                truffleAdapter.showCoverage(uri);
            } catch (RuntimeException e) {
                DiagnosticsNotification diagnosticsNotification = getDiagnosticsNotification(e);
                Collection<PublishDiagnosticsParams> diagnosticParamsCollection = diagnosticsNotification.getDiagnosticParamsCollection();
                assertEquals(1, diagnosticParamsCollection.size());
                PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
                assertEquals(uri.toString(), diagnosticsParams.getUri());
                List<Diagnostic> diagnostics = diagnosticsParams.getDiagnostics();
                assertEquals(1, diagnostics.size());
                assertEquals(range(12, 2, 12, 14), diagnostics.get(0).getRange());
                caught = true;
            }
            assertTrue(caught);
        }
    }
}
