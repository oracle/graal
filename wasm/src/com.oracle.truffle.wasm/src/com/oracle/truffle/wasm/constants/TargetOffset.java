/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.constants;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.RepeatingNode;

public class TargetOffset implements RepeatingNode.ShouldContinue {

    public final int value;

    public TargetOffset(int value) {
        this.value = value;
    }

    public boolean isGreaterThanZero() {
        return value > 0;
    }

    public boolean isMinusOne() {
        return value == -1;
    }

    public TargetOffset decrement() {
        final int resultValue = value - 1;
        return createOrCached(resultValue);
    }

    public static TargetOffset createOrCached(int value) {
        // The cache index starts with value -1, so we need a +1 offset.
        final int resultCacheIndex = value + 1;
        if (resultCacheIndex < CACHE.length) {
            return CACHE[resultCacheIndex];
        }
        return new TargetOffset(value);
    }

    public static final TargetOffset MINUS_ONE = new TargetOffset(-1);
    public static final TargetOffset ZERO = new TargetOffset(0);

    @CompilationFinal(dimensions = 1) private static final TargetOffset[] CACHE = new TargetOffset[]{
                    MINUS_ONE,
                    ZERO,
                    new TargetOffset(1),
                    new TargetOffset(2),
                    new TargetOffset(3),
                    new TargetOffset(4),
                    new TargetOffset(5),
                    new TargetOffset(6),
                    new TargetOffset(7),
                    new TargetOffset(8),
                    new TargetOffset(9),
                    new TargetOffset(10),
                    new TargetOffset(11),
                    new TargetOffset(12),
                    new TargetOffset(13),
                    new TargetOffset(14),
                    new TargetOffset(15),
                    new TargetOffset(16),
                    new TargetOffset(17),
                    new TargetOffset(18),
                    new TargetOffset(19),
                    new TargetOffset(20),
                    new TargetOffset(21),
                    new TargetOffset(22),
                    new TargetOffset(23),
                    new TargetOffset(24),
                    new TargetOffset(25),
                    new TargetOffset(26),
                    new TargetOffset(27),
                    new TargetOffset(28),
                    new TargetOffset(29),
                    new TargetOffset(30),
                    new TargetOffset(31),
                    new TargetOffset(32)
    };

    @Override
    public boolean shouldContinue() {
        // This is a trick to avoid the load of the value field.
        // In particular, we avoid:
        //
        // return this.value == 0;
        //
        // This helps the partial evaluator short-circuit the
        // pattern with a diamond and a loop exit check,
        // when br_if occurs in the loop body.
        return this == ZERO;
    }
}
