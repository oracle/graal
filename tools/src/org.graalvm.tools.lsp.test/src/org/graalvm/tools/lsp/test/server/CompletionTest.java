package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.junit.Test;

import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.builtins.SLHelloEqualsWorldBuiltin;
import com.oracle.truffle.sl.runtime.SLContext;

public class CompletionTest extends TruffleLSPTest {

    @Test
    public void globalsAndLocals() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        //@formatter:off
        /**
         * 0 function main() {
         * 1  return 3+3;
         * 2 }
         * 3 function abc(p1, p2) {
         * 4   varA = p1 + p2;
         * 5
         * 6   varB = p1 * p2;
         * 7   return varA;
         * 8 }
         * 9
         */
        //@formatter:on
        String text = "function main() {\n  return 3+3;\n}\nfunction abc(p1, p2) {\n  varA = p1 + p2;\n\n  varB = p1 * p2;\n  return varA;\n}\n";
        Future<?> future = truffleAdapter.parse(text, "sl", uri);
        future.get();

        int numberOfGlobalsItems = -1;

        {
            int line = 0;
            int column = 0;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertFalse(items.isEmpty());

            NodeInfo nodeInfo = SLContext.lookupNodeInfo(SLHelloEqualsWorldBuiltin.class);
            assertNotNull(nodeInfo);

            String shortName = nodeInfo.shortName();
            assertTrue("Built-in function " + shortName + " not found.", items.stream().anyMatch(item -> item.getLabel().startsWith(shortName)));
            assertTrue("p1 should not be found in main-function scope", items.stream().noneMatch(item -> item.getLabel().startsWith("p1")));

            numberOfGlobalsItems = items.size();
        }

        {
            int line = 1;
            int column = 12;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertFalse(items.isEmpty());

            NodeInfo nodeInfo = SLContext.lookupNodeInfo(SLHelloEqualsWorldBuiltin.class);
            assertNotNull(nodeInfo);

            String shortName = nodeInfo.shortName();
            assertTrue("Built-in function " + shortName + " not found.", items.stream().anyMatch(item -> item.getLabel().startsWith(shortName)));
            assertTrue("p1 should not be found in main-function scope", items.stream().noneMatch(item -> item.getLabel().startsWith("p1")));
            assertEquals(numberOfGlobalsItems, items.size());
        }

        {
            int line = 5;
            int column = 0;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertFalse(items.isEmpty());

            NodeInfo nodeInfo = SLContext.lookupNodeInfo(SLHelloEqualsWorldBuiltin.class);
            assertNotNull(nodeInfo);

            String shortName = nodeInfo.shortName();
            assertTrue("Built-in function " + shortName + " not found.", items.stream().anyMatch(item -> item.getLabel().startsWith(shortName)));
            assertTrue("p1 should be found in abc-function scope", items.stream().anyMatch(item -> item.getLabel().startsWith("p1")));
            assertTrue("varA should be found in abc-function scope", items.stream().anyMatch(item -> item.getLabel().startsWith("varA")));
            assertTrue("varB should not be found in main-function scope", items.stream().noneMatch(item -> item.getLabel().startsWith("varB")));
            assertEquals(numberOfGlobalsItems + 3, items.size());
        }

        {
            int line = 7;
            int column = 2;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertFalse(items.isEmpty());

            NodeInfo nodeInfo = SLContext.lookupNodeInfo(SLHelloEqualsWorldBuiltin.class);
            assertNotNull(nodeInfo);

            String shortName = nodeInfo.shortName();
            assertTrue("Built-in function " + shortName + " not found.", items.stream().anyMatch(item -> item.getLabel().startsWith(shortName)));
            assertTrue("p1 should be found in abc-function scope", items.stream().anyMatch(item -> item.getLabel().startsWith("p1")));
            assertTrue("varA should be found in abc-function scope", items.stream().anyMatch(item -> item.getLabel().startsWith("varA")));
            assertTrue("varB should be found in main-function scope", items.stream().anyMatch(item -> item.getLabel().startsWith("varB")));
            assertEquals(numberOfGlobalsItems + 4, items.size());
        }

        {
            int line = 9;
            int column = 0;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertFalse(items.isEmpty());

            NodeInfo nodeInfo = SLContext.lookupNodeInfo(SLHelloEqualsWorldBuiltin.class);
            assertNotNull(nodeInfo);

            String shortName = nodeInfo.shortName();
            assertTrue("Built-in function " + shortName + " not found.", items.stream().anyMatch(item -> item.getLabel().startsWith(shortName)));
            assertTrue("p1 should not be found in main-function scope", items.stream().noneMatch(item -> item.getLabel().startsWith("p1")));
            assertEquals(numberOfGlobalsItems, items.size());
        }

        {
            // if line is out of range -> show nothing
            int line = 100;
            int column = 0;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertTrue(items.isEmpty());
        }

        {
            // if column is out of range -> show nothing
            int line = 8;
            int column = 5;
            Future<CompletionList> futureCompletions = truffleAdapter.completion(uri, line, column, null);
            CompletionList completionList = futureCompletions.get();
            assertFalse(completionList.isIncomplete());

            List<CompletionItem> items = completionList.getItems();
            assertTrue(items.isEmpty());
        }
    }

    @Test
    public void objectPropertyCompletionLocalFile() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        //@formatter:off
        /**
         *  0 function main() {
         *  1     obj = abc();
         *  2     obj;
         *  3 }
         *  4
         *  5 function abc() {
         *  6   obj = new();
         *  7   obj.p = 1;
         *  8   return obj;
         *  9 }
         * 10
         * 11 function never_called(obj) {
         * 12     obj;
         * 13 }
         * 14
         */
        //@formatter:on
        String text = "function main() {\n    obj = abc();\n    obj;\n}\n\nfunction abc() {\n  obj = new();\n  obj.p = 1;\n  return obj;\n}\n\nfunction never_called(obj) {\n    obj;\n}\n";
        Future<?> future = truffleAdapter.parse(text, "sl", uri);
        future.get();

        {
            String replacement = ".";
            Range range = new Range(new Position(2, 7), new Position(2, 7));
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent(range, replacement.length(), replacement);
            boolean thrown = false;
            try {
                Future<?> future2 = truffleAdapter.processChangesAndParse(Arrays.asList(event), uri);
                future2.get();
            } catch (RuntimeException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);

            Future<CompletionList> future3 = truffleAdapter.completion(uri, 2, 8, null);
            CompletionList completionList = future3.get();
            assertEquals(1, completionList.getItems().size());
            CompletionItem item = completionList.getItems().get(0);
            assertEquals("p", item.getLabel());
            assertEquals("Number", item.getDetail());
            assertEquals(CompletionItemKind.Property, item.getKind());
        }

        {
            String replacement1 = "";
            Range range1 = new Range(new Position(2, 7), new Position(2, 8));
            TextDocumentContentChangeEvent event1 = new TextDocumentContentChangeEvent(range1, replacement1.length(), replacement1);
            Future<?> future2 = truffleAdapter.processChangesAndParse(Arrays.asList(event1), uri);
            future2.get();

            String replacement2 = ".";
            Range range2 = new Range(new Position(12, 7), new Position(12, 7));
            TextDocumentContentChangeEvent event2 = new TextDocumentContentChangeEvent(range2, replacement2.length(), replacement2);
            boolean thrown = false;
            try {
                Future<?> future3 = truffleAdapter.processChangesAndParse(Arrays.asList(event2), uri);
                future3.get();
            } catch (RuntimeException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);

            thrown = false;
            try {
                Future<CompletionList> future4 = truffleAdapter.completion(uri, 12, 8, null);
                CompletionList completionList = future4.get();
                assertEquals(0, completionList.getItems().size());
            } catch (RuntimeException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);
        }
    }

    @Test
    public void objectPropertyCompletionViaCoverageData() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        //@formatter:off
        /**
         *  0 function main() {
         *  1     obj = abc();
         *  2     obj;
         *  3 }
         *  4
         *  5 function abc() {
         *  6   obj = new();
         *  7   obj.p = 1;
         *  8   return obj;
         *  9 }
         * 10
         * 11 function never_called(obj) {
         * 12     obj;
         * 13 }
         * 14
         */
        //@formatter:on
        String text = "function main() {\n    obj = abc();\n    obj;\n}\n\nfunction abc() {\n  obj = new();\n  obj.p = 1;\n  return obj;\n}\n\nfunction never_called(obj) {\n    obj;\n}\n";
        Future<?> future = truffleAdapter.parse(text, "sl", uri);
        future.get();

        Future<Boolean> futureCoverage = truffleAdapter.runCoverageAnalysis(uri);
        futureCoverage.get();

        {
            String replacement = ".";
            Range range = new Range(new Position(8, 12), new Position(8, 12));
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent(range, replacement.length(), replacement);
            boolean thrown = false;
            try {
                Future<?> future2 = truffleAdapter.processChangesAndParse(Arrays.asList(event), uri);
                future2.get();
            } catch (RuntimeException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);

            Future<CompletionList> future3 = truffleAdapter.completion(uri, 8, 13, null);
            CompletionList completionList = future3.get();
            assertEquals(1, completionList.getItems().size());
            CompletionItem item = completionList.getItems().get(0);
            assertEquals("p", item.getLabel());
            assertEquals("Number", item.getDetail());
            assertEquals(CompletionItemKind.Property, item.getKind());
        }
    }
}
