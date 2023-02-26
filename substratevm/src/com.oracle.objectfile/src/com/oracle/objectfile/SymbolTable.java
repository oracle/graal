/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile;

import com.oracle.objectfile.ObjectFile.Symbol;

public interface SymbolTable extends Iterable<Symbol> {
    Symbol getSymbol(String name);

    Symbol newDefinedEntry(String name, ObjectFile.Section referencedSection, long referencedOffset, long size, boolean isGlobal, boolean isCode);

    Symbol newUndefinedEntry(String name, boolean isCode);

    /**
     * Simple sanity check: don't let a symbol replace an already defined symbol, to be used with
     * {@link java.util.Map#compute}.
     */
    static <T extends Symbol> T tryReplace(T oldEntry, T newEntry) {
        if (oldEntry == null || (!oldEntry.isDefined() && oldEntry.getName().equals(newEntry.getName()))) {
            return newEntry;
        }
        throw new RuntimeException("Illegal replacement of symbol table entry");
    }
}
