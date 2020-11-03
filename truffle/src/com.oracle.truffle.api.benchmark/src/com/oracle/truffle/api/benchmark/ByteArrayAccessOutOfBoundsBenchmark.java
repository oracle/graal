/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.benchmark;

import com.oracle.truffle.api.memory.ByteArraySupport;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Random;

/**
 * This benchmarks compares different ways to access a sequence of integers with bounds checks. Half
 * of the accesses are out of bounds.
 * <p>
 * In interpreter mode (without Truffle compilation), {@link #bytesArrayCondition} and
 * {@link #intsArrayCondition} are ~1.2x faster than {@link #bytesArrayException} and
 * {@link #intsArrayException} respectively. This is the reason for exposing
 * {@link ByteArraySupport#inBounds}.
 */
@State(Scope.Benchmark)
public class ByteArrayAccessOutOfBoundsBenchmark extends TruffleBenchmark {
    static final int N = 100000;
    final byte[] bytes = new byte[N * 4];
    final int[] ints = new int[N];
    final int[] indices = new int[N];

    @Setup(Level.Trial)
    public void setup() {
        Random r = new Random();
        r.setSeed(0);
        for (int i = 0; i < N; ++i) {
            final int value = r.nextInt();
            ByteArraySupport.bigEndian().putInt(bytes, i * 4, value);
            ints[i] = value;
        }

        for (int i = 0; i < N; ++i) {
            indices[i] = r.nextInt(N * 2);
        }
    }

    @Benchmark
    public long intsArrayException() {
        long sum = 0;
        for (int i = 0; i < N; i++) {
            try {
                sum += ints[indices[i]];
            } catch (ArrayIndexOutOfBoundsException e) {
                sum += 1;
            }
        }
        return sum;
    }

    @Benchmark
    public long intsArrayCondition() {
        long sum = 0;
        for (int i = 0; i < N; i++) {
            final int index = indices[i];
            if (Integer.compareUnsigned(index, ints.length) < 0) {
                sum += ints[index];
            } else {
                sum += 1;
            }
        }
        return sum;
    }

    @Benchmark
    public long bytesArrayException() {
        long sum = 0;
        for (int i = 0; i < N; i++) {
            try {
                sum += ByteArraySupport.bigEndian().getInt(bytes, indices[i] * 4);
            } catch (IndexOutOfBoundsException e) {
                sum += 1;
            }
        }
        return sum;
    }

    @Benchmark
    public long bytesArrayCondition() {
        long sum = 0;
        for (int i = 0; i < N; i++) {
            final int index = indices[i] * 4;
            if (ByteArraySupport.bigEndian().inBounds(bytes, index, Integer.BYTES)) {
                sum += ByteArraySupport.bigEndian().getInt(bytes, index);
            } else {
                sum += 1;
            }
        }
        return sum;
    }
}
