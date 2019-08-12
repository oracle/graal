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

import org.graalvm.tools.lsp.server.types.Location;
import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;
import org.junit.Test;

public class DefinitionTest extends TruffleLSPTest {

    @Test
    public void gotoDefinitionForFunctions() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        Range abcRange = Range.create(Position.create(5, 9), Position.create(9, 1));

        int line = 1;
        for (int i = 4; i <= 9; i++) {
            checkDefinitions(uri, line, i, 1, abcRange);
        }
        checkNoDefinitions(uri, line, 3);
        checkNoDefinitions(uri, line, 10);

        line = 2;
        for (int i = 8; i <= 13; i++) {
            checkDefinitions(uri, line, i, 1, abcRange);
        }
        checkNoDefinitions(uri, line, 7);
        checkNoDefinitions(uri, line, 14);

        line = 6;
        // new() is built-in and has no SourceSection
        checkNoDefinitions(uri, line, 9);

        // check edge-cases / out-of bounds
        checkNoDefinitions(uri, 0, 0);
        checkNoDefinitions(uri, 10, 0);
        checkNoDefinitions(uri, 10, 1);
        checkNoDefinitions(uri, 11, 1);
    }

    @Test
    public void gotoDefinitionForFunctionsStatic() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        futureOpen.get();

        checkDefinitions(uri, 12, 3, 1, Range.create(Position.create(5, 9), Position.create(9, 1)));
    }

    private void checkNoDefinitions(URI uri, int line, int character) throws InterruptedException, ExecutionException {
        checkDefinitions(uri, line, character, 0, null);
    }

    private void checkDefinitions(URI uri, int line, int character, int defSize, Range locationRange) throws InterruptedException, ExecutionException {
        Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, character);
        List<? extends Location> definitions = future.get();
        assertEquals(defSize, definitions.size());
        if (defSize != 0) {
            Location location = definitions.get(0);
            assertTrue(rangeCheck(locationRange, location.getRange()));
        }
    }
}
