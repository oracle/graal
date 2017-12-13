/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.objectfile.ObjectFile.Symbol;

public interface SymbolTable extends Iterable<Symbol> {
    List<Symbol> symbolsWithName(String symName);

    Symbol newDefinedEntry(String name, ObjectFile.Section referencedSection, long referencedOffset, long size, boolean isGlobal, boolean isCode);

    Symbol newUndefinedEntry(String name, boolean isCode);

    int indexOf(Symbol sym);

    Symbol get(int n);

    boolean contains(Symbol symbol);

    default Symbol uniqueDefinedSymbolWithName(String symName) {
        List<Symbol> possibles = new ArrayList<>();
        for (Symbol s : symbolsWithName(symName)) {
            if (s.isDefined()) {
                possibles.add(s);
            }
        }
        switch (possibles.size()) {
            case 0:
                return null;
            case 1:
                return possibles.get(0);
            default:
                throw new IllegalStateException("multiple definitions for symbol " + symName);
        }
    }

    default Set<Integer> symbolIndicesForName(String symName) {
        HashSet<Integer> s = new HashSet<>();
        for (Symbol sym : symbolsWithName(symName)) {
            s.add(indexOf(sym));
        }
        return s;
    }
}
