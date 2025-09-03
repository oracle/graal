/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.descriptors;

/**
 * Manages access to "name" symbols used in class files and other JVM structures.
 * <p>
 * Name symbols represent identifiers like class names, method names, field names, etc. While names
 * do not have a strictly defined format, they typically follow Java identifier naming conventions.
 */
public final class NameSymbols {
    private final Symbols symbols;

    /**
     * Creates a new NameSymbols instance to manage name symbol operations.
     *
     * @param symbols The underlying Symbols instance that handles symbol storage and retrieval
     */
    public NameSymbols(Symbols symbols) {
        this.symbols = symbols;
    }

    /**
     * Looks up an existing name symbol from a byte sequence. Only returns symbols that have been
     * previously created.
     *
     * @param nameBytes The name as a byte sequence to look up
     * @return The existing Symbol for the name, or null if no matching symbol exists
     */
    public Symbol<Name> lookup(ByteSequence nameBytes) {
        return symbols.lookup(nameBytes);
    }

    /**
     * Looks up an existing name symbol from a string. Only returns symbols that have been
     * previously created.
     *
     * @param nameString The name as a string to look up
     * @return The existing Symbol for the name, or null if no matching symbol exists
     */
    public Symbol<Name> lookup(String nameString) {
        return lookup(ByteSequence.create(nameString));
    }

    /**
     * Creates a new name symbol or retrieves an existing one from a string. If the symbol doesn't
     * exist, it will be created.
     *
     * @param nameString The name as a string to create or retrieve
     * @return A new or existing Symbol representing the name
     */
    public Symbol<Name> getOrCreate(String nameString) {
        return getOrCreate(ByteSequence.create(nameString));
    }

    /**
     * Creates a new name symbol or retrieves an existing one from a byte sequence. If the symbol
     * doesn't exist, it will be created.
     *
     * @param nameBytes The name as a byte sequence to create or retrieve
     * @return A new or existing Symbol representing the name
     */
    public Symbol<Name> getOrCreate(ByteSequence nameBytes) {
        return getOrCreate(nameBytes, false);
    }

    /**
     * Creates a new name symbol or retrieves an existing one from a byte sequence. If the symbol
     * doesn't exist, it will be created.
     *
     * @param ensureStrongReference if {@code true}, the returned symbol is guaranteed to be
     *            strongly referenced by the symbol table
     * @param nameBytes The name as a byte sequence to create or retrieve
     * @return A new or existing Symbol representing the name
     */
    public Symbol<Name> getOrCreate(ByteSequence nameBytes, boolean ensureStrongReference) {
        return symbols.getOrCreate(nameBytes, ensureStrongReference);
    }

    /**
     * Determines if a package represents the unnamed package. The unnamed package is represented by
     * an empty symbol.
     *
     * @param pkg The package to check
     * @return true if the package is unnamed (zero length), false otherwise
     */
    public static boolean isUnnamedPackage(Symbol<Name> pkg) {
        return pkg.length() == 0;
    }
}
