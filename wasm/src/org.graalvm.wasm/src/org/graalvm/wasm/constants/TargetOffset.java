/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.constants;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class TargetOffset {
    public final int value;

    private TargetOffset(int value) {
        this.value = value;
    }

    public boolean isGreaterThanZero() {
        return value > 0;
    }

    public boolean isZero() {
        return value == 0;
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

    private static final int CACHE_SIZE = 256;
    @CompilationFinal(dimensions = 1) private static final TargetOffset[] CACHE;

    static {
        CACHE = new TargetOffset[CACHE_SIZE];
        CACHE[0] = MINUS_ONE;
        CACHE[1] = ZERO;
        for (int i = 2; i < CACHE_SIZE; i++) {
            CACHE[i] = new TargetOffset(i - 1);
        }
    }
}
