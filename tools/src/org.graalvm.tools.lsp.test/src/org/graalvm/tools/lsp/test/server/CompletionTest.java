/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
        String text = "function main() {\n" +        // 0
                        "  return 3+3;\n" +          // 1
                        "}\n" +                      // 2
                        "function abc(p1, p2) {\n" + // 3
                        "  varA = p1 + p2;\n" +      // 4
                        "\n" +                       // 5
                        "  varB = p1 * p2;\n" +      // 6
                        "  return varA;\n" +         // 7
                        "}\n";                       // 8
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
        Future<?> future = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        future.get();

        {
            String replacement = ".";
            Range range = new Range(new Position(2, 12), new Position(2, 12));
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent(range, replacement.length(), replacement);
            boolean thrown = false;
            try {
                Future<?> future2 = truffleAdapter.processChangesAndParse(Arrays.asList(event), uri);
                future2.get();
            } catch (ExecutionException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);

            Future<CompletionList> future3 = truffleAdapter.completion(uri, 2, 13, null);
            CompletionList completionList = future3.get();
            assertEquals(1, completionList.getItems().size());
            CompletionItem item = completionList.getItems().get(0);
            assertEquals("p", item.getLabel());
            assertEquals("Number", item.getDetail());
            assertEquals(CompletionItemKind.Property, item.getKind());
        }

        {
            String replacement1 = "";
            Range range1 = new Range(new Position(2, 12), new Position(2, 13));
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
            } catch (ExecutionException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);

            thrown = false;
            try {
                Future<CompletionList> future4 = truffleAdapter.completion(uri, 12, 8, null);
                CompletionList completionList = future4.get();
                assertEquals(0, completionList.getItems().size());
            } catch (ExecutionException e) {
                thrown = true;
                assertTrue(e.getCause() instanceof DiagnosticsNotification);
            }
            assertTrue(thrown);
        }
    }

    @Test
    public void objectPropertyCompletionViaCoverageData() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> future = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
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
            } catch (ExecutionException e) {
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
