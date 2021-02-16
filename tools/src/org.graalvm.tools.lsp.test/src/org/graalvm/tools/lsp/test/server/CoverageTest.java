/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.tools.lsp.server.types.Coverage;
import org.junit.Test;

public class CoverageTest extends TruffleLSPTest {

    @Test
    public void runConverageAnalysisTest() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        futureOpen.get();

        Coverage coverage = truffleAdapter.getCoverage(uri).get();
        assertTrue(coverage != null);
        assertEquals(7, coverage.getUncovered().size());
        assertTrue(rangeCheck(1, 4, 1, 13, coverage.getUncovered().get(0)));
        assertTrue(rangeCheck(2, 4, 2, 12, coverage.getUncovered().get(1)));
        assertTrue(rangeCheck(6, 2, 6, 13, coverage.getUncovered().get(2)));
        assertTrue(rangeCheck(7, 2, 7, 11, coverage.getUncovered().get(3)));
        assertTrue(rangeCheck(8, 2, 8, 12, coverage.getUncovered().get(4)));
        assertTrue(rangeCheck(12, 2, 12, 7, coverage.getUncovered().get(5)));
        assertTrue(rangeCheck(13, 2, 13, 14, coverage.getUncovered().get(6)));

        Future<Boolean> future = truffleAdapter.runCoverageAnalysis(uri);
        Boolean result = future.get();
        assertTrue(result);

        coverage = truffleAdapter.getCoverage(uri).get();
        assertTrue(coverage != null);
        assertEquals(2, coverage.getUncovered().size());
        assertTrue(rangeCheck(12, 2, 12, 7, coverage.getUncovered().get(0)));
        assertTrue(rangeCheck(13, 2, 13, 14, coverage.getUncovered().get(1)));
    }
}
