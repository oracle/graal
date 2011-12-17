/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

/**
 * Represents a value that is yet to be bound to a machine location (such as
 * a {@linkplain CiRegister register} or stack {@linkplain CiAddress address})
 * by a register allocator.
 */
public final class CiVariable extends CiValue {

    /**
     * The identifier of the variable. This is a non-zero index in a contiguous 0-based name space.
     */
    public final int index;

    /**
     * Creates a new variable.
     * @param kind
     * @param index
     */
    private CiVariable(CiKind kind, int index) {
        super(kind);
        this.index = index;
    }

    private static CiVariable[] generate(CiKind kind, int count) {
        CiVariable[] variables = new CiVariable[count];
        for (int i = 0; i < count; i++) {
            variables[i] = new CiVariable(kind, i);
        }
        return variables;
    }

    private static final int CACHE_PER_KIND_SIZE = 100;

    /**
     * Cache of common variables.
     */
    private static final CiVariable[][] cache = new CiVariable[CiKind.values().length][];
    static {
        for (CiKind kind : CiKind.values()) {
            cache[kind.ordinal()] = generate(kind, CACHE_PER_KIND_SIZE);
        }
    }

    /**
     * Gets a variable for a given kind and index.
     *
     * @param kind
     * @param index
     * @return the corresponding {@code CiVariable}
     */
    public static CiVariable get(CiKind kind, int index) {
        //assert kind == kind.stackKind() : "Variables can be only created for stack kinds";
        assert index >= 0;
        CiVariable[] cachedVars = cache[kind.ordinal()];
        if (index < cachedVars.length) {
            return cachedVars[index];
        }
        return new CiVariable(kind, index);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof CiVariable) {
            CiVariable var = (CiVariable) obj;
            return kind == var.kind && index == var.index;
        }
        return false;
    }

    @Override
    public boolean equalsIgnoringKind(CiValue o) {
        if (this == o) {
            return true;
        }
        if (o instanceof CiVariable) {
            CiVariable var = (CiVariable) o;
            return index == var.index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (index << 4) | kind.ordinal();
    }

    @Override
    public String name() {
        return "v" + index;
    }
}
