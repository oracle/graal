/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public class PermittedSubclassesAttribute extends Attribute {
    public static final Symbol<Name> NAME = ParserNames.PermittedSubclasses;

    public static final PermittedSubclassesAttribute EMPTY = new PermittedSubclassesAttribute(NAME, new char[0]);

    @CompilerDirectives.CompilationFinal(dimensions = 1)//
    private final char[] classes;

    public PermittedSubclassesAttribute(Symbol<Name> name, char[] classes) {
        assert name == NAME;
        this.classes = classes;
    }

    public char[] getClasses() {
        return classes;
    }

    @Override
    public boolean isSame(Attribute other, ConstantPool thisPool, ConstantPool otherPool) {
        if (!super.isSame(other, thisPool, otherPool)) {
            return false;
        }
        PermittedSubclassesAttribute otherPermittedSubclassAttribute = (PermittedSubclassesAttribute) other;
        // build the name symbols of all permitted subclasses and compare
        Set<Symbol<Name>> thisSymbols = fillSymbols(classes, thisPool);
        Set<Symbol<Name>> otherSymbols = fillSymbols(otherPermittedSubclassAttribute.classes, otherPool);
        return thisSymbols.equals(otherSymbols);
    }

    private static Set<Symbol<Name>> fillSymbols(char[] classIndices, ConstantPool pool) {
        Set<Symbol<Name>> symbols = new HashSet<>();
        for (int classIndex : classIndices) {
            symbols.add(pool.className(classIndex));
        }
        return symbols;
    }

    @Override
    public Symbol<Name> getName() {
        return NAME;
    }
}
