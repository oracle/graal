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
