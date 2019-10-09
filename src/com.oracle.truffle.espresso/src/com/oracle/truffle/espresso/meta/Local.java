/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.meta;

import java.util.Objects;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;

/**
 * Describes the type and bytecode index range in which a local variable is live.
 */
public final class Local {

    public static final Local[] EMPTY_ARRAY = new Local[0];

    private final Symbol<Name> name;
    private final Symbol<Name> type;
    private final int startBci;
    private final int endBci;
    private final int slot;

    public Local(Symbol<Name> name, Symbol<Name> type, int startBci, int endBci, int slot) {
        this.name = name;
        this.startBci = startBci;
        this.endBci = endBci;
        this.slot = slot;
        this.type = type;
    }

    public int getStartBCI() {
        return startBci;
    }

    public int getEndBCI() {
        return endBci;
    }

    public Symbol<Name> getName() {
        return name;
    }

    public Symbol<Name> getType() {
        return type;
    }

    public int getSlot() {
        return slot;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Local)) {
            return false;
        }
        Local that = (Local) obj;
        return this.name.equals(that.name) && this.startBci == that.startBci && this.endBci == that.endBci && this.slot == that.slot && this.type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, startBci, endBci, slot);
    }

    @Override
    public String toString() {
        return "LocalImpl<name=" + name + ", type=" + type + ", startBci=" + startBci + ", endBci=" + endBci + ", slot=" + slot + ">";
    }
}
