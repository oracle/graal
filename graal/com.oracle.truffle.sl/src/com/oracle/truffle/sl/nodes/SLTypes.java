/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes;

import java.math.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The type system of SL, as explained in {@link SLMain}. Based on the {@link TypeSystem}
 * annotation, the Truffle DSL generates the subclass {@link SLTypesGen} with type test and type
 * conversion methods for all types. In this class, we only cover types where the automatically
 * generated ones would not be sufficient.
 */
@TypeSystem({long.class, BigInteger.class, boolean.class, String.class, SLFunction.class, SLNull.class})
public abstract class SLTypes {

    /**
     * Example of a manually specified type check that replaces the automatically generated type
     * check that the Truffle DSL would generate. For {@link SLNull}, we do not need an
     * {@code instanceof} check, because we know that there is only a {@link SLNull#SINGLETON
     * singleton} instance.
     */
    @TypeCheck
    public boolean isSLNull(Object value) {
        return value == SLNull.SINGLETON;
    }

    /**
     * Example of a manually specified type cast that replaces the automatically generated type cast
     * that the Truffle DSL would generate. For {@link SLNull}, we do not need an actual cast,
     * because we know that there is only a {@link SLNull#SINGLETON singleton} instance.
     */
    @TypeCast
    public SLNull asSLNull(Object value) {
        assert isSLNull(value);
        return SLNull.SINGLETON;
    }

    /**
     * Informs the Truffle DSL that a primitive {@code long} value can be used in all
     * specializations where a {@link BigInteger} is expected. This models the semantic of SL: It
     * only has an arbitrary precision Number type (implemented as {@link BigInteger}, and
     * {@code long} is only used as a performance optimization to avoid the costly
     * {@link BigInteger} arithmetic for values that fit into a 64-bit primitive value.
     */
    @ImplicitCast
    public BigInteger castBigInteger(long value) {
        return BigInteger.valueOf(value);
    }
}
