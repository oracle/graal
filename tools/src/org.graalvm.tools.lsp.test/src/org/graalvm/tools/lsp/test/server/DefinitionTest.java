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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Test;

public class DefinitionTest extends TruffleLSPTest {

    @Test
    public void gotoDefinitionForFunctions() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        Range abcRange = new Range(new Position(5, 9), new Position(9, 1));

        int line = 1;
        {
            for (int i = 4; i <= 9; i++) {
                Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, i);
                List<? extends Location> definitions = future.get();
                assertEquals(1, definitions.size());
                Location location = definitions.get(0);
                assertEquals(abcRange, location.getRange());
            }
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 3);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 10);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        line = 2;
        {
            for (int i = 8; i <= 13; i++) {
                Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, i);
                List<? extends Location> definitions = future.get();
                assertEquals(1, definitions.size());
                Location location = definitions.get(0);
                assertEquals(abcRange, location.getRange());
            }
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 7);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 14);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        line = 6;
        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, line, 9);
            List<? extends Location> definitions = future.get();
            // new() is built-in and has no SourceSection
            assertEquals(0, definitions.size());
        }

        // check edge-cases / out-of bounds
        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 0, 0);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 10, 0);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 10, 1);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 11, 1);
            List<? extends Location> definitions = future.get();
            assertEquals(0, definitions.size());
        }
    }

    @Test
    public void gotoDefinitionForFunctionsStatic() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ_NOT_CALLED, "sl", uri);
        futureOpen.get();

        {
            Future<List<? extends Location>> future = truffleAdapter.definition(uri, 12, 3);
            List<? extends Location> definitions = future.get();
            assertEquals(1, definitions.size());
            Location location = definitions.get(0);
            assertEquals(new Range(new Position(5, 9), new Position(9, 1)), location.getRange());
        }
    }
}
