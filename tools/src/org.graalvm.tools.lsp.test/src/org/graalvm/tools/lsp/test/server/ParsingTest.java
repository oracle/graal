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

import java.io.File;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.graalvm.tools.lsp.server.types.Diagnostic;
import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.PublishDiagnosticsParams;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;
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
        File testFile = File.createTempFile("truffle-lsp-test-file-unknown-lang-id", "");
        testFile.deleteOnExit();
        URI uri = testFile.toURI();

        Future<?> future = truffleAdapter.parse("", "unknown-lang-id", uri);
        try {
            future.get();
        } catch (ExecutionException ex) {
            throw ex.getCause();
        }
    }

    @Test
    public void unknownlanguageIdButMIMETypeFound() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();

        Future<?> future = truffleAdapter.parse("function main() {return 42;}", "unknown-lang-id", uri);
        future.get();
    }

    @Test
    public void parseOK() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        String text = "function main() {return 3+3;}";
        Future<?> future = truffleAdapter.parse(text, "sl", uri);
        future.get();
    }

    @Test
    public void parseEOF() throws InterruptedException {
        URI uri = createDummyFileUriForSL();
        String text = "function main";
        Future<?> future = truffleAdapter.parse(text, "sl", uri);
        try {
            future.get();
            fail();
        } catch (ExecutionException ex) {
            Collection<PublishDiagnosticsParams> diagnosticParams = ((DiagnosticsNotification) ex.getCause()).getDiagnosticParamsCollection();
            assertEquals(1, diagnosticParams.size());
            PublishDiagnosticsParams param = diagnosticParams.iterator().next();
            assertEquals(uri.toString(), param.getUri());
            List<Diagnostic> diagnostics = param.getDiagnostics();
            assertTrue(diagnostics.get(0).getMessage().contains("EOF"));
        }
    }

    @Test
    public void changeAndParse() throws InterruptedException, ExecutionException {
        TextDocumentSurrogate surrogate;
        URI uri = createDummyFileUriForSL();

        String text = "function main() {return 3+3;}";
        Future<?> futureParse = truffleAdapter.parse(text, "sl", uri);
        futureParse.get();

        // Insert +4
        checkChange(uri, Range.create(Position.create(0, 27), Position.create(0, 27)), "+4",
                        "function main() {return 3+3+4;}");

        // Delete
        checkChange(uri, Range.create(Position.create(0, 24), Position.create(0, 26)), "",
                        "function main() {return 3+4;}");

        // Replace
        checkChange(uri, Range.create(Position.create(0, 17), Position.create(0, 29)), "\n  return 42;\n}",
                        "function main() {\n  return 42;\n}");

        // Insert at the end
        checkChange(uri, Range.create(Position.create(2, 1), Position.create(2, 1)), "\n",
                        "function main() {\n  return 42;\n}\n");
        checkChange(uri, Range.create(Position.create(3, 0), Position.create(3, 0)), " ",
                        "function main() {\n  return 42;\n}\n ");

        // Multiline replace
        checkChange(uri, Range.create(Position.create(0, 16), Position.create(3, 1)), "{return 1;}",
                        "function main() {return 1;}");

        // No change
        surrogate = checkChange(uri, Range.create(Position.create(0, 1), Position.create(0, 1)), "",
                        "function main() {return 1;}");
        // Replace to empty
        try {
            checkChange(uri, Range.create(Position.create(0, 0), Position.create(0, 30)), "", null);
            fail();
        } catch (ExecutionException e) {
            Collection<PublishDiagnosticsParams> diagnosticParamsCollection = ((DiagnosticsNotification) e.getCause()).getDiagnosticParamsCollection();
            assertEquals(1, diagnosticParamsCollection.size());
            PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
            List<Diagnostic> diagnostics = diagnosticsParams.getDiagnostics();
            assertTrue(diagnostics.get(0).getMessage().contains("EOF"));
        }
        assertEquals("", surrogate.getEditorText());
    }

    private TextDocumentSurrogate checkChange(URI uri, Range range, String change, String editorText) throws InterruptedException, ExecutionException {
        TextDocumentContentChangeEvent event = TextDocumentContentChangeEvent.create(change).setRange(range).setRangeLength(change.length());
        Future<TextDocumentSurrogate> future = truffleAdapter.processChangesAndParse(Arrays.asList(event), uri);
        TextDocumentSurrogate surrogate = future.get();
        assertEquals(editorText, surrogate.getEditorText());
        assertSame(surrogate.getEditorText(), surrogate.getEditorText());
        return surrogate;
    }

    @Test
    public void parseingWithSyntaxErrors() throws InterruptedException {
        URI uri = createDummyFileUriForSL();
        String text = "function main() {return 3+;}";

        Future<?> future = truffleAdapter.parse(text, "sl", uri);
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            DiagnosticsNotification diagnosticsNotification = getDiagnosticsNotification(e);
            Collection<PublishDiagnosticsParams> diagnosticParamsCollection = diagnosticsNotification.getDiagnosticParamsCollection();
            assertEquals(1, diagnosticParamsCollection.size());
            PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
            assertEquals(1, diagnosticsParams.getDiagnostics().size());
            assertTrue(rangeCheck(0, 26, 0, 27, diagnosticsParams.getDiagnostics().get(0).getRange()));
        }
    }
}
