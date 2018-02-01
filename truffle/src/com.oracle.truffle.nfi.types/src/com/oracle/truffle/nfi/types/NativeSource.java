/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.types;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed representation of a Truffle NFI source. To use the Truffle NFI, evaluate a source with the
 * mime-type application/x-native. See {@link Parser} for the syntax of the Truffle NFI source.
 */
public final class NativeSource {

    private final String nfiId;
    private final String libraryDescriptor;

    private final List<String> preBoundSymbols;
    private final List<String> preBoundSignatures;

    NativeSource(String nfiId, String libraryDescriptor) {
        this.nfiId = nfiId;
        this.libraryDescriptor = libraryDescriptor;
        this.preBoundSymbols = new ArrayList<>();
        this.preBoundSignatures = new ArrayList<>();
    }

    public boolean isDefaultBackend() {
        return nfiId == null;
    }

    public String getNFIBackendId() {
        return nfiId;
    }

    public String getLibraryDescriptor() {
        return libraryDescriptor;
    }

    public int preBoundSymbolsLength() {
        return preBoundSymbols.size();
    }

    public String getPreBoundSymbol(int i) {
        return preBoundSymbols.get(i);
    }

    public String getPreBoundSignature(int i) {
        return preBoundSignatures.get(i);
    }

    void register(String symbol, String signature) {
        preBoundSymbols.add(symbol);
        preBoundSignatures.add(signature);
    }
}
