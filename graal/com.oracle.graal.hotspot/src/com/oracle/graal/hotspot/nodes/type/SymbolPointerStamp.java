/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes.type;

import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

import com.oracle.graal.compiler.common.type.AbstractPointerStamp;
import com.oracle.graal.compiler.common.type.Stamp;

public final class SymbolPointerStamp extends MetaspacePointerStamp {

    private static final SymbolPointerStamp SYMBOL = new SymbolPointerStamp(false, false);

    private static final SymbolPointerStamp SYMBOL_NON_NULL = new SymbolPointerStamp(true, false);

    private static final SymbolPointerStamp SYMBOL_ALWAYS_NULL = new SymbolPointerStamp(false, true);

    public static SymbolPointerStamp symbol() {
        return SYMBOL;
    }

    public static SymbolPointerStamp symbolNonNull() {
        return SYMBOL_NON_NULL;
    }

    private SymbolPointerStamp(boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        if (newNonNull) {
            assert !newAlwaysNull;
            return SYMBOL_NON_NULL;
        } else if (newAlwaysNull) {
            return SYMBOL_ALWAYS_NULL;
        } else {
            return SYMBOL;
        }
    }

    @Override
    public boolean isCompatible(Stamp otherStamp) {
        if (this == otherStamp) {
            return true;
        }
        return otherStamp instanceof SymbolPointerStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof HotSpotMetaspaceConstant) {
            return ((HotSpotMetaspaceConstant) constant).asSymbol() != null;
        } else {
            return super.isCompatible(constant);
        }
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return SYMBOL_ALWAYS_NULL;
        } else {
            assert ((HotSpotMetaspaceConstant) c).asSymbol() != null;
            return SYMBOL_NON_NULL;
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        HotSpotMemoryAccessProvider hsProvider = (HotSpotMemoryAccessProvider) provider;
        return hsProvider.readSymbolConstant(base, displacement);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("Symbol*");
        appendString(ret);
        return ret.toString();
    }
}
