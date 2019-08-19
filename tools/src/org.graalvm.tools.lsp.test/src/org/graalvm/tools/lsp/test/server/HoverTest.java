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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.graalvm.tools.lsp.server.types.Hover;
import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class HoverTest extends TruffleLSPTest {

    @Test
    public void hoverNoCoverageDataAvailable() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        checkHover(uri, 1, 5, Range.create(Position.create(1, 4), Position.create(1, 7)));
        checkHover(uri, 1, 8, Range.create(Position.create(1, 4), Position.create(1, 9)));
        checkHover(uri, 0, 10, Range.create(Position.create(0, 9), Position.create(3, 1)));
    }

    @Test
    public void hoverWithCoverageDataAvailable() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        Future<Boolean> futureCoverage = truffleAdapter.runCoverageAnalysis(uri);
        assertTrue(futureCoverage.get());

        Hover hover = checkHover(uri, 8, 10, Range.create(Position.create(8, 9), Position.create(8, 12)));
        assertTrue(hover.getContents() instanceof List);
        assertEquals(3, ((List<?>) hover.getContents()).size());
        assertTrue(((List<?>) hover.getContents()).get(0) instanceof org.graalvm.tools.lsp.server.types.MarkedString);
        assertEquals("obj", ((org.graalvm.tools.lsp.server.types.MarkedString) ((List<?>) hover.getContents()).get(0)).getValue());
        assertEquals("Object", ((List<?>) hover.getContents()).get(1));
        assertEquals("meta-object: Object", ((List<?>) hover.getContents()).get(2));
    }

    private Hover checkHover(URI uri, int line, int column, Range range) throws InterruptedException, ExecutionException {
        Future<Hover> future = truffleAdapter.hover(uri, line, column);
        Hover hover = future.get();
        assertTrue(rangeCheck(range, hover.getRange()));
        return hover;
    }
}
