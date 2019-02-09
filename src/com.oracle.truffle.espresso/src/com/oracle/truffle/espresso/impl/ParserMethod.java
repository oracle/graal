/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.Attributes;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

// Unresolved unlinked.
public final class ParserMethod {

    public static final ParserMethod[] EMPTY_ARRAY = new ParserMethod[0];

    private final int flags;
    private final int nameIndex;
    private final int signatureIndex;

    public int getFlags() {
        return flags;
    }

    private final Attributes attributes;

    // Shared quickening recipes.
    // Stores BC + arguments in compact form.
    @CompilationFinal(dimensions = 1) //
    private long[] recipes;

    public static ParserMethod create(int flags, int nameIndex, int signatureIndex, Attribute[] attributes) {
        return new ParserMethod(flags, nameIndex, signatureIndex, attributes);
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public int getSignatureIndex() {
        return signatureIndex;
    }

    public Attribute getAttribute(Symbol<Name> name) {
        return attributes.get(name);
    }

    public long[] getRecipes() {
        return recipes;
    }

    public ParserMethod(int flags, int nameIndex, int signatureIndex, Attribute[] attributes) {
        this.flags = flags;
        this.nameIndex = nameIndex;
        this.signatureIndex = signatureIndex;
        this.attributes = new Attributes(attributes);
    }
}
