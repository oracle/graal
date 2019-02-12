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
import static org.junit.Assert.assertTrue;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;

public class ParsingTest extends TruffleLSPTest {

    @Test
    public void didOpenClose() {
        URI uri = createDummyFileUriForSL();
        String text = "function main() {return 3+3;}";
        truffleAdapter.parse(text, "sl", uri);
        truffleAdapter.didClose(uri);
    }

    @Test(expected = UnknownLanguageException.class)
    public void unknownlanguage() throws Throwable {
        URI uri = URI.create("file:///tmp/truffle-lsp-test-file-unknown-lang-id");

        Future<?> future = truffleAdapter.parse("", "unknown-lang-id", uri);
        try {
            future.get();
        } catch (ExecutionException ex) {
            throw ex.getCause();
        }
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

        {
            URI uri = createDummyFileUriForSL();
            String text = "function main";
            Future<?> future = truffleAdapter.parse(text, "sl", uri);
            try {
                future.get();
                Assert.fail();
            } catch (ExecutionException ex) {
                Collection<PublishDiagnosticsParams> diagnosticParams = ((DiagnosticsNotification) ex.getCause()).getDiagnosticParamsCollection();
                assertEquals(1, diagnosticParams.size());
                PublishDiagnosticsParams param = diagnosticParams.iterator().next();
                assertEquals(uri.toString(), param.getUri());
                List<Diagnostic> diagnostics = param.getDiagnostics();
                assertTrue(diagnostics.get(0).getMessage().contains("EOF"));
            }
        }

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

            {
                String textToReplaceEmpty = "";
                TextDocumentContentChangeEvent replaceEvent = new TextDocumentContentChangeEvent(new Range(new Position(0, 0), new Position(0, 30)),
                                textToReplaceEmpty.length(), textToReplaceEmpty);
                Future<TextDocumentSurrogate> future = truffleAdapter.processChangesAndParse(Arrays.asList(replaceEvent), uri);
                try {
                    surrogate = future.get();
                    Assert.fail();
                } catch (ExecutionException e) {
                    Collection<PublishDiagnosticsParams> diagnosticParamsCollection = ((DiagnosticsNotification) e.getCause()).getDiagnosticParamsCollection();
                    assertEquals(1, diagnosticParamsCollection.size());
                    PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
                    List<Diagnostic> diagnostics = diagnosticsParams.getDiagnostics();
                    assertTrue(diagnostics.get(0).getMessage().contains("EOF"));
                }
                assertEquals("", surrogate.getEditorText());
            }

        }
    }

    @Test
    public void parseingWithSyntaxErrors() throws InterruptedException {
        {
            URI uri = createDummyFileUriForSL();
            String text = "function main() {return 3+;}";

            Future<?> future = truffleAdapter.parse(text, "sl", uri);
            try {
                future.get();
                Assert.fail();
            } catch (ExecutionException e) {
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
