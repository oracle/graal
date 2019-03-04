/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.io.ByteSequence;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@State(Scope.Thread)
@Fork(value = 1)
public class ByteSequenceBenchmark {

    private static final int ITERATIONS = 10000;

    @State(Scope.Thread)
    public static class HashingState {
        final ByteSequence[] sequences = new ByteSequence[ITERATIONS];
        private final byte[] buffer = new byte[4096];
        {
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = (byte) i;
            }
        }

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < sequences.length; i++) {
                sequences[i] = ByteSequence.create(buffer);
            }
        }

    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public int testHashing(HashingState state) {
        int hash = 0;
        for (int i = 0; i < state.sequences.length; i++) {
            hash += state.sequences[i].hashCode();
        }
        return hash;
    }

}
