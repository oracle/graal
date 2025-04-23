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

import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;

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

    private final Symbol<Name> name;
    private final Symbol<Type> type;
    private final Symbol<?> typeSignature;
    private final char startBci;
    private final char endBci;
    private final char slot;

    public Local(Symbol<Name> name, Symbol<Type> type, Symbol<?> typeSignature, int startBci, int endBci, int slot) {
        assert type != null || typeSignature != null;
        this.name = name;
        this.startBci = (char) startBci;
        this.endBci = (char) endBci;
        this.slot = (char) slot;
        this.type = type;
        this.typeSignature = typeSignature;
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

    public Symbol<Type> getType() {
        return type;
    }

    public JavaKind getJavaKind() {
        return type == null ? JavaKind.Object : TypeSymbols.getJavaKind(type);
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
        // Keep compatibility with the old behavior.
        if (type == null) {
            return typeSignature.toString();
        }
        return type.toString();
    }
}
