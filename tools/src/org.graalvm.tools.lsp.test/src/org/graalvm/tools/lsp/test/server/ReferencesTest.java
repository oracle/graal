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

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Location;
import org.junit.Test;

public class ReferencesTest extends TruffleLSPTest {

    @Test
    public void findAllReferencesForFunctions() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        Future<List<? extends Location>> future = truffleAdapter.references(uri, 1, 4);
        List<? extends Location> definitions = future.get();
        assertEquals(2, definitions.size());
        assertEquals(range(1, 4, 1, 9), definitions.get(0).getRange());
        assertEquals(range(2, 8, 2, 13), definitions.get(1).getRange());
    }

    @Test
    public void findAllReferencesForFunctionsStatic() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        futureOpen.get();

        Future<List<? extends Location>> future = truffleAdapter.references(uri, 13, 10);
        List<? extends Location> definitions = future.get();
        assertEquals(3, definitions.size());
        assertEquals(range(1, 8, 1, 13), definitions.get(0).getRange());
        assertEquals(range(12, 2, 12, 7), definitions.get(1).getRange());
        assertEquals(range(13, 9, 13, 14), definitions.get(2).getRange());
    }
}
