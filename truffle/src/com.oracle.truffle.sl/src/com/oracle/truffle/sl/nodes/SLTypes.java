/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * The type system of SL, as explained in {@link SLLanguage}. Based on the {@link TypeSystem}
 * annotation, the Truffle DSL generates the subclass {@link SLTypesGen} with type test and type
 * conversion methods for some types. In this class, we only cover types where the automatically
 * generated ones would not be sufficient.
 */
@TypeSystem({long.class, boolean.class})
public abstract class SLTypes {

    /**
     * Example of a manually specified type check that replaces the automatically generated type
     * check that the Truffle DSL would generate. For {@link SLNull}, we do not need an
     * {@code instanceof} check, because we know that there is only a {@link SLNull#SINGLETON
     * singleton} instance.
     */
    @TypeCheck(SLNull.class)
    public static boolean isSLNull(Object value) {
        return value == SLNull.SINGLETON;
    }

    /**
     * Example of a manually specified type cast that replaces the automatically generated type cast
     * that the Truffle DSL would generate. For {@link SLNull}, we do not need an actual cast,
     * because we know that there is only a {@link SLNull#SINGLETON singleton} instance.
     */
    @TypeCast(SLNull.class)
    public static SLNull asSLNull(Object value) {
        assert isSLNull(value);
        return SLNull.SINGLETON;
    }

    /**
     * Informs the Truffle DSL that a primitive {@code long} value can be used in all
     * specializations where a {@link SLBigNumber} is expected. This models the semantic of SL: It
     * only has an arbitrary precision Number type (implemented as {@link SLBigNumber}, and
     * {@code long} is only used as a performance optimization to avoid the costly
     * {@link SLBigNumber} arithmetic for values that fit into a 64-bit primitive value.
     */
    @ImplicitCast
    @TruffleBoundary
    public static SLBigNumber castBigNumber(long value) {
        return new SLBigNumber(BigInteger.valueOf(value));
    }
}
