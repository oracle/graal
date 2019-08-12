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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.graalvm.tools.lsp.server.types.SymbolInformation;
import org.junit.Test;

public class DocumentSmybolTest extends TruffleLSPTest {

    @Test
    public void documentSymbol() throws InterruptedException, ExecutionException {
        URI uri = createDummyFileUriForSL();
        Future<?> futureOpen = truffleAdapter.parse(PROG_OBJ, "sl", uri);
        futureOpen.get();

        Future<List<? extends SymbolInformation>> futureSymbol = truffleAdapter.documentSymbol(uri);
        List<? extends SymbolInformation> symbols = futureSymbol.get();

        assertEquals(2, symbols.size());

        Optional<? extends SymbolInformation> symbolOptMain = symbols.stream().filter(symbol -> symbol.getName().equals("main")).findFirst();
        assertTrue(symbolOptMain.isPresent());
        assertTrue(rangeCheck(0, 9, 3, 1, symbolOptMain.get().getLocation().getRange()));

        Optional<? extends SymbolInformation> symbolOptAbc = symbols.stream().filter(symbol -> symbol.getName().equals("abc")).findFirst();
        assertTrue(rangeCheck(5, 9, 9, 1, symbolOptAbc.get().getLocation().getRange()));
    }
}
