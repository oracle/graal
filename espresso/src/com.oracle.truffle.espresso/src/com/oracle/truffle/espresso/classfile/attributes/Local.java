/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import java.util.Objects;

import org.graalvm.collections.Equivalence;

import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.jdwp.api.LocalRef;

/**
 * Describes the type and bytecode index range in which a local variable is live.
 */
public final class Local implements LocalRef {

    public static final Equivalence localEquivalence = new Equivalence() {
        @Override
        public boolean equals(Object a, Object b) {
            if (a instanceof Local && b instanceof Local) {
                return ((Local) a).sameLocal((Local) b);
            }
            return false;
        }

        @Override
        public int hashCode(Object o) {
            if (o instanceof Local) {
                return ((Local) o).sameLocalHash();
            }
            return o.hashCode();
        }
    };

    public static final Local[] EMPTY_ARRAY = new Local[0];

    private final Utf8Constant name;
    private final Utf8Constant type;
    private final int startBci;
    private final int endBci;
    private final int slot;

    public Local(Utf8Constant name, Utf8Constant type, int startBci, int endBci, int slot) {
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

    public Utf8Constant getName() {
        return name;
    }

    public Utf8Constant getType() {
        return type;
    }

    public int getSlot() {
        return slot;
    }

    public boolean sameLocal(Local other) {
        return this.startBci == other.startBci && this.endBci == other.endBci && this.slot == other.slot && this.name.equals(other.name);
    }

    public int sameLocalHash() {
        return Objects.hash(startBci, endBci, slot, name);
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

    @Override
    public String getNameAsString() {
        return name.toString();
    }

    @Override
    public String getTypeAsString() {
        return type.toString();
    }
}
