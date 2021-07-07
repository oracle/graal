/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class LinkedMethod {
    private final ParserMethod parserMethod;

    public ParserMethod getParserMethod() {
        return parserMethod;
    }

    // int vtableSlot; // not all methods have vtable entry
    protected int getFlags() {
        return parserMethod.getFlags();
    }

    protected Symbol<Signature> getRawSignature() {
        return parserMethod.getSignature();
    }

    protected Symbol<Name> getName() {
        return parserMethod.getName();
    }

    LinkedMethod(ParserMethod parserMethod) {
        this.parserMethod = parserMethod;
    }

    public Attribute getAttribute(Symbol<Name> name) {
        return parserMethod.getAttribute(name);
    }
}
