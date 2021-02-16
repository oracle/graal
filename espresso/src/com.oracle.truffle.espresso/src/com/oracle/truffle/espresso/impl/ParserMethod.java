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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.runtime.Attribute;

/**
 * Immutable raw representation of methods in Espresso, this is the output of the parser.
 */
public final class ParserMethod {

    public static final ParserMethod[] EMPTY_ARRAY = new ParserMethod[0];

    private final int flags;
    private final Symbol<Name> name;
    private final Symbol<Signature> signature;

    public int getFlags() {
        return flags;
    }

    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    // Shared quickening recipes.
    // Stores BC + arguments in compact form.
    @CompilationFinal(dimensions = 1) //
    private long[] recipes;

    public static ParserMethod create(int flags, Symbol<Name> name, Symbol<Signature> signature, Attribute[] attributes) {
        return new ParserMethod(flags, name, signature, attributes);
    }

    public Symbol<Name> getName() {
        return name;
    }

    public Symbol<Signature> getSignature() {
        return signature;
    }

    public Attribute getAttribute(Symbol<Name> attributeName) {
        for (Attribute attribute : attributes) {
            if (attributeName.equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }

    public long[] getRecipes() {
        return recipes;
    }

    ParserMethod(int flags, Symbol<Name> name, Symbol<Signature> signature, Attribute[] attributes) {
        this.flags = flags;
        this.name = name;
        this.signature = signature;
        this.attributes = attributes;
    }
}
