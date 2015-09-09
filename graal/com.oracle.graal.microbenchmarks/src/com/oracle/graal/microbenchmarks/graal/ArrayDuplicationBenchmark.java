package com.oracle.graal.microbenchmarks.graal;

import java.util.*;

import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
public class ArrayDuplicationBenchmark {

    /** How large should the test-arrays be. */
    private static final int TESTSIZE = 300;

    private Object[][] testObjectArray;

    private Object[] dummy;

    @Setup
    public void setup() {
        testObjectArray = new Object[TESTSIZE][];
        for (int i = 0; i < TESTSIZE; i++) {
            testObjectArray[i] = new Object[20];
        }
    }

    @Setup(Level.Iteration)
    public void iterationSetup() {
        dummy = new Object[TESTSIZE * 3];
    }

    @TearDown(Level.Iteration)
    public void iterationTearDown() {
        dummy = null;
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public Object[] normalArraycopy() {
        for (int i = 0, j = 0; i < TESTSIZE; i++) {
            dummy[j++] = normalArraycopy(testObjectArray[i]);
        }
        return dummy;
    }

    public Object[] normalArraycopy(Object[] cache) {
        Object[] result = new Object[cache.length];
        System.arraycopy(cache, 0, result, 0, result.length);
        return result;
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public Object[] arraysCopyOf() {
        for (int i = 0, j = 0; i < TESTSIZE; i++) {
            dummy[j++] = arraysCopyOf(testObjectArray[i]);
        }
        return dummy;
    }

    public Object[] arraysCopyOf(Object[] cache) {
        return Arrays.copyOf(cache, cache.length);
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public Object[] cloneObjectArray() {
        for (int i = 0, j = 0; i < TESTSIZE; i++) {
            dummy[j++] = arraysClone(testObjectArray[i]);
        }
        return dummy;
    }

    public Object[] arraysClone(Object[] cache) {
        return (Object[]) cache.clone();
    }

}
