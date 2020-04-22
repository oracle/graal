/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.graalvm.tools.lsp.server.types.DocumentHighlight;
import org.graalvm.tools.lsp.server.types.DocumentHighlightKind;
import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;

public class DocumentHighlightTest extends TruffleLSPTest {

    @Test
    public void variablesHighlightTest() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        futureOpen.get();

        checkHighlight(uri, 1, 4, DocumentHighlight.create(Range.create(Position.create(1, 4), Position.create(1, 5)), DocumentHighlightKind.Write),
                        DocumentHighlight.create(Range.create(Position.create(2, 11), Position.create(2, 12)), DocumentHighlightKind.Read));
        for (int column = 2; column <= 4; column++) {
            checkHighlight(uri, 6, column, DocumentHighlight.create(Range.create(Position.create(6, 2), Position.create(6, 5)), DocumentHighlightKind.Write),
                            DocumentHighlight.create(Range.create(Position.create(7, 2), Position.create(7, 5)), DocumentHighlightKind.Read),
                            DocumentHighlight.create(Range.create(Position.create(8, 9), Position.create(8, 12)), DocumentHighlightKind.Read));
        }
    }

    private void checkHighlight(URI uri, int line, int column, DocumentHighlight... verifiedHighlights) throws InterruptedException, ExecutionException {
        Future<List<? extends DocumentHighlight>> future = truffleAdapter.documentHighlight(uri, line, column);
        List<? extends DocumentHighlight> highlights = future.get();
        assertEquals(verifiedHighlights.length, highlights.size());
        for (int i = 0; i < verifiedHighlights.length; i++) {
            DocumentHighlight vh = verifiedHighlights[i];
            DocumentHighlight h = highlights.get(i);
            assertEquals(Integer.toString(i), vh.getKind(), h.getKind());
            assertTrue(Integer.toString(i), rangeCheck(vh.getRange(), h.getRange()));
        }
    }
}
