/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
