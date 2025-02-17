/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public final class BootstrapMethodsAttribute extends Attribute {

    public static final Symbol<Name> NAME = ParserNames.BootstrapMethods;

    public Entry[] getEntries() {
        return entries;
    }

    public static final class Entry {
        final char bootstrapMethodRef;

        @CompilationFinal(dimensions = 1) //
        final char[] bootstrapArguments;

        public int numBootstrapArguments() {
            return bootstrapArguments.length;
        }

        public Entry(char bootstrapMethodRef, char[] bootstrapArguments) {
            this.bootstrapMethodRef = bootstrapMethodRef;
            this.bootstrapArguments = bootstrapArguments;
        }

        public char argAt(int index) {
            return bootstrapArguments[index];
        }

        public char getBootstrapMethodRef() {
            return bootstrapMethodRef;
        }

    }

    private final Entry[] entries;

    public BootstrapMethodsAttribute(Symbol<Name> name, Entry[] entries) {
        assert name == NAME;
        this.entries = entries;
    }

    public Entry at(int index) {
        return entries[index];
    }

    @Override
    public Symbol<Name> getName() {
        return NAME;
    }
}
