/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.espresso.runtime.StaticObject;

public final class Locals {

    private final Object[] refs;
    private final long[] primitives;

    public Locals(int numberOfSlots) {
        this.refs = new Object[numberOfSlots];
        this.primitives = new long[numberOfSlots];
    }

    public int peekInt(int slot) {
        return (int) primitives[slot];
    }

    void putRawObject(int slot, Object value) {
        refs[slot] = value;
    }

    void putObject(int slot, StaticObject value) {
        assert value != null : "use putRawObject to write host null";
        refs[slot] = value;
    }

    public long peekLong(int slot) {
        return primitives[slot];
    }

    public StaticObject peekObject(int slot) {
        Object result = refs[slot];
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    Object peekRawObject(int slot) {
        return refs[slot];
    }

    public void putLong(int slot, long value) {
        primitives[slot] = value;
        assert peekLong(slot) == value;
    }

    public void putInt(int slot, int value) {
        primitives[slot] = value;
    }
}
