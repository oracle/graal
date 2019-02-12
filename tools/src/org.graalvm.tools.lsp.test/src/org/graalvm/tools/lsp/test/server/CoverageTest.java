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
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        futureOpen.get();

        {
            Future<?> showCoverage = truffleAdapter.showCoverage(uri);
            boolean caught = false;
            try {
                showCoverage.get();
            } catch (ExecutionException e) {
                DiagnosticsNotification diagnosticsNotification = getDiagnosticsNotification(e);
                Collection<PublishDiagnosticsParams> diagnosticParamsCollection = diagnosticsNotification.getDiagnosticParamsCollection();
                assertEquals(1, diagnosticParamsCollection.size());
                PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
                assertEquals(uri.toString(), diagnosticsParams.getUri());
                List<Diagnostic> diagnostics = diagnosticsParams.getDiagnostics();
                assertEquals(7, diagnostics.size());
                assertEquals(range(1, 4, 1, 13), diagnostics.get(0).getRange());
                assertEquals(range(2, 4, 2, 12), diagnostics.get(1).getRange());
                assertEquals(range(6, 2, 6, 13), diagnostics.get(2).getRange());
                assertEquals(range(7, 2, 7, 11), diagnostics.get(3).getRange());
                assertEquals(range(8, 2, 8, 12), diagnostics.get(4).getRange());
                assertEquals(range(12, 2, 12, 7), diagnostics.get(5).getRange());
                assertEquals(range(13, 2, 13, 14), diagnostics.get(6).getRange());
                caught = true;
            }
            assertTrue(caught);
        }

        {
            Future<Boolean> future = truffleAdapter.runCoverageAnalysis(uri);
            Boolean result = future.get();
            assertTrue(result);

            Future<?> showCoverage = truffleAdapter.showCoverage(uri);
            boolean caught = false;
            try {
                showCoverage.get();
            } catch (ExecutionException e) {
                DiagnosticsNotification diagnosticsNotification = getDiagnosticsNotification(e);
                Collection<PublishDiagnosticsParams> diagnosticParamsCollection = diagnosticsNotification.getDiagnosticParamsCollection();
                assertEquals(1, diagnosticParamsCollection.size());
                PublishDiagnosticsParams diagnosticsParams = diagnosticParamsCollection.iterator().next();
                assertEquals(uri.toString(), diagnosticsParams.getUri());
                List<Diagnostic> diagnostics = diagnosticsParams.getDiagnostics();
                assertEquals(2, diagnostics.size());
                assertEquals(range(12, 2, 12, 7), diagnostics.get(0).getRange());
                assertEquals(range(13, 2, 13, 14), diagnostics.get(1).getRange());
                caught = true;
            }
            assertTrue(caught);
        }
    }
}
