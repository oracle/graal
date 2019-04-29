/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Specialized {@link BitSetMatcher} that exists simply because ascii bit set matchers occur often
 * and we can save one comparison when the high byte is {@code 0x00}.
 */
public abstract class NullHighByteBitSetMatcher extends InvertibleCharMatcher {

    private final CompilationFinalBitSet bitSet;

    NullHighByteBitSetMatcher(boolean inverse, CompilationFinalBitSet bitSet) {
        super(inverse);
        this.bitSet = bitSet;
    }

    public static NullHighByteBitSetMatcher create(boolean inverse, CompilationFinalBitSet bitSet) {
        return NullHighByteBitSetMatcherNodeGen.create(inverse, bitSet);
    }

    @Specialization
    protected boolean match(char c, @SuppressWarnings("unused") boolean compactString) {
        return result(bitSet.get(c));
    }

    @Override
    public int estimatedCost() {
        return 4;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public String toString() {
        return modifiersToString() + "{ascii " + bitSet + "}";
    }
}
