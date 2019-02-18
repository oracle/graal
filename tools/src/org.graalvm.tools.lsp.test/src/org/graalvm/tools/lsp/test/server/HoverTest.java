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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Test;

public class HoverTest extends TruffleLSPTest {

    @Test
    public void hoverNoCoverageDataAvailable() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        checkHover(uri, 1, 5, new Range(new Position(1, 4), new Position(1, 7)));
        checkHover(uri, 1, 8, new Range(new Position(1, 4), new Position(1, 9)));
        checkHover(uri, 0, 10, new Range(new Position(0, 9), new Position(3, 1)));
    }

    @Test
    public void hoverWithCoverageDataAvailable() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        Future<Boolean> futureCoverage = truffleAdapter.runCoverageAnalysis(uri);
        assertTrue(futureCoverage.get());

        Hover hover = checkHover(uri, 8, 10, new Range(new Position(8, 9), new Position(8, 12)));
        assertEquals(new Range(new Position(8, 9), new Position(8, 12)), hover.getRange());
        assertEquals(3, hover.getContents().getLeft().size());
        assertEquals("obj", hover.getContents().getLeft().get(0).getRight().getValue());
        assertEquals("Object", hover.getContents().getLeft().get(1).getLeft());
        assertEquals("meta-object: Object", hover.getContents().getLeft().get(2).getLeft());
    }

    private Hover checkHover(URI uri, int line, int column, Range range) throws InterruptedException, ExecutionException {
        Future<Hover> future = truffleAdapter.hover(uri, line, column);
        Hover hover = future.get();
        assertEquals(range, hover.getRange());
        return hover;
    }
}
