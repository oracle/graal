/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.interop;

import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.Value.Optimized;
import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FunctionProxyBenchmark extends TruffleBenchmark {

    private static final int OPERATIONS = 100;
    private static final int INPUT_MASK = 63;

    @FunctionalInterface
    public interface NoArguments {
        double apply();
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedNoArguments extends NoArguments {
    }

    public static final class HostPayload {
        @HostAccess.Export public final int number;

        HostPayload(int number) {
            this.number = number;
        }
    }

    @FunctionalInterface
    public interface HostAndInt {
        double apply(HostPayload object, int value);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedHostAndInt extends HostAndInt {
    }

    @FunctionalInterface
    public interface StringAndInt {
        double apply(String text, int value);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedStringAndInt extends StringAndInt {
    }

    @FunctionalInterface
    public interface Doubles {
        double apply(double x, double y);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedDoubles extends Doubles {
    }

    @FunctionalInterface
    public interface MixedPrimitives {
        double apply(boolean z, byte b, short s, int i, long l, float f, double d);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedMixedPrimitives extends MixedPrimitives {
    }

    @FunctionalInterface
    public interface AllPrimitives {
        double apply(boolean z, byte b, short s, char c, int i, long l, float f, double d);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedAllPrimitives extends AllPrimitives {
    }

    @FunctionalInterface
    public interface SeventeenArguments {
        double apply(boolean z0, byte b0, short s0, int i0, long l0, float f0, double d0,
                        boolean z1, byte b1, short s1, int i1, long l1, float f1, double d1, int i2, long l2, double d2);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedSeventeenArguments extends SeventeenArguments {
    }

    @FunctionalInterface
    public interface EighteenArguments {
        double apply(boolean z0, byte b0, short s0, int i0, long l0, float f0, double d0,
                        boolean z1, byte b1, short s1, int i1, long l1, float f1, double d1, int i2, long l2, double d2, int i3);
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedEighteenArguments extends EighteenArguments {
    }

    @State(Scope.Thread)
    public static class NoArgumentsState {

        Context context;
        Value value;
        NoArguments dynamic;
        GeneratedNoArguments generated;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "0");
            dynamic = value.as(NoArguments.class);
            generated = value.as(GeneratedNoArguments.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double noArgumentsDynamic(NoArgumentsState state) {
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            checksum += state.dynamic.apply();
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double noArgumentsGenerated(NoArgumentsState state) {
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            checksum += state.generated.apply();
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double noArgumentsValue(NoArgumentsState state) {
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            checksum += state.value.execute().asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class HostAndIntState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        HostAndInt dynamic;
        GeneratedHostAndInt generated;
        final HostPayload[] objects = new HostPayload[INPUT_MASK + 1];
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "host");
            dynamic = value.as(HostAndInt.class);
            generated = value.as(GeneratedHostAndInt.class);
            for (int i = 0; i < objects.length; i++) {
                objects[i] = new HostPayload(base + i);
            }
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double hostAndIntDynamic(HostAndIntState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply(state.objects[index], base + index);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double hostAndIntGenerated(HostAndIntState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply(state.objects[index], base + index);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double hostAndIntValue(HostAndIntState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute(state.objects[index], base + index).asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class StringAndIntState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        StringAndInt dynamic;
        GeneratedStringAndInt generated;
        final String[] strings = new String[INPUT_MASK + 1];
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "string");
            dynamic = value.as(StringAndInt.class);
            generated = value.as(GeneratedStringAndInt.class);
            for (int i = 0; i < strings.length; i++) {
                strings[i] = "x".repeat(base + i);
            }
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double stringAndIntDynamic(StringAndIntState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply(state.strings[index], base + index);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double stringAndIntGenerated(StringAndIntState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply(state.strings[index], base + index);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double stringAndIntValue(StringAndIntState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute(state.strings[index], base + index).asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class DoublesState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        Doubles dynamic;
        GeneratedDoubles generated;
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "2");
            dynamic = value.as(Doubles.class);
            generated = value.as(GeneratedDoubles.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double doublesDynamic(DoublesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double doublesGenerated(DoublesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double doublesValue(DoublesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute(base + index + 0.25, base + index + 0.75).asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class MixedPrimitivesState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        MixedPrimitives dynamic;
        GeneratedMixedPrimitives generated;
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "7");
            dynamic = value.as(MixedPrimitives.class);
            generated = value.as(GeneratedMixedPrimitives.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double mixedPrimitivesDynamic(MixedPrimitivesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double mixedPrimitivesGenerated(MixedPrimitivesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double mixedPrimitivesValue(MixedPrimitivesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75).asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class AllPrimitivesState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        AllPrimitives dynamic;
        GeneratedAllPrimitives generated;
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "8");
            dynamic = value.as(AllPrimitives.class);
            generated = value.as(GeneratedAllPrimitives.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double allPrimitivesDynamic(AllPrimitivesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply((index & 1) == 0, (byte) index, (short) (base + index), (char) ('A' + (index & 15)), base + index, (long) base + index, index + 0.25f, index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double allPrimitivesGenerated(AllPrimitivesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply((index & 1) == 0, (byte) index, (short) (base + index), (char) ('A' + (index & 15)), base + index, (long) base + index, index + 0.25f, index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double allPrimitivesValue(AllPrimitivesState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute((index & 1) == 0, (byte) index, (short) (base + index), (char) ('A' + (index & 15)), base + index, (long) base + index, index + 0.25f, index +
                            0.75).asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class SeventeenArgumentsState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        SeventeenArguments dynamic;
        GeneratedSeventeenArguments generated;
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "17");
            dynamic = value.as(SeventeenArguments.class);
            generated = value.as(GeneratedSeventeenArguments.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double seventeenArgumentsDynamic(SeventeenArgumentsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75,
                            (index & 2) == 0, (byte) (index + 1), (short) (base + index + 1), base + index + 1, (long) base + index + 1, index + 1.25f, index + 1.75,
                            base + index + 2, (long) base + index + 2, index + 2.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double seventeenArgumentsGenerated(SeventeenArgumentsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75,
                            (index & 2) == 0, (byte) (index + 1), (short) (base + index + 1), base + index + 1, (long) base + index + 1, index + 1.25f, index + 1.75,
                            base + index + 2, (long) base + index + 2, index + 2.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double seventeenArgumentsValue(SeventeenArgumentsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75,
                            (index & 2) == 0, (byte) (index + 1), (short) (base + index + 1), base + index + 1, (long) base + index + 1, index + 1.25f, index + 1.75,
                            base + index + 2, (long) base + index + 2, index + 2.75).asDouble();
        }
        return checksum;
    }

    @State(Scope.Thread)
    public static class EighteenArgumentsState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        EighteenArguments dynamic;
        GeneratedEighteenArguments generated;
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "18");
            dynamic = value.as(EighteenArguments.class);
            generated = value.as(GeneratedEighteenArguments.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double eighteenArgumentsDynamic(EighteenArgumentsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.apply((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75,
                            (index & 2) == 0, (byte) (index + 1), (short) (base + index + 1), base + index + 1, (long) base + index + 1, index + 1.25f, index + 1.75,
                            base + index + 2, (long) base + index + 2, index + 2.75, base + index + 3);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double eighteenArgumentsGenerated(EighteenArgumentsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.apply((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75,
                            (index & 2) == 0, (byte) (index + 1), (short) (base + index + 1), base + index + 1, (long) base + index + 1, index + 1.25f, index + 1.75,
                            base + index + 2, (long) base + index + 2, index + 2.75, base + index + 3);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double eighteenArgumentsValue(EighteenArgumentsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.execute((index & 1) == 0, (byte) index, (short) (base + index), base + index, (long) base + index, index + 0.25f, index + 0.75,
                            (index & 2) == 0, (byte) (index + 1), (short) (base + index + 1), base + index + 1, (long) base + index + 1, index + 1.25f, index + 1.75,
                            base + index + 2, (long) base + index + 2, index + 2.75, base + index + 3).asDouble();
        }
        return checksum;
    }

    @FunctionalInterface
    public interface ObjectMethods {
        double regular(double x, double y);

        default double supported(double x, double y) {
            return -1;
        }

        default double unsupported(double x, double y) {
            return regular(x, y);
        }
    }

    @Optimized
    @FunctionalInterface
    public interface GeneratedObjectMethods extends ObjectMethods {
    }

    @State(Scope.Thread)
    public static class ObjectMethodsState {

        @Param({"0", "1024"}) public int base;

        Context context;
        Value value;
        ObjectMethods dynamic;
        GeneratedObjectMethods generated;
        int cursor;

        @Setup
        public void setup() {
            context = Context.newBuilder(FunctionProxyBenchmarkLanguage.ID).allowHostAccess(HostAccess.EXPLICIT).build();
            value = context.eval(FunctionProxyBenchmarkLanguage.ID, "members");
            dynamic = value.as(ObjectMethods.class);
            generated = value.as(GeneratedObjectMethods.class);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectRegularDynamic(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.regular(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectRegularGenerated(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.regular(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectRegularValue(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.invokeMember("regular", base + index + 0.25, base + index + 0.75).asDouble();
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectSupportedDefaultDynamic(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.supported(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectSupportedDefaultGenerated(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.supported(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectSupportedDefaultValue(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.value.invokeMember("supported", base + index + 0.25, base + index + 0.75).asDouble();
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectUnsupportedDefaultDynamic(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.dynamic.unsupported(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectUnsupportedDefaultGenerated(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            checksum += state.generated.unsupported(base + index + 0.25, base + index + 0.75);
        }
        return checksum;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double objectUnsupportedDefaultValue(ObjectMethodsState state) {
        int base = state.base;
        double checksum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int index = state.cursor++ & INPUT_MASK;
            String member = state.value.canInvokeMember("unsupported") ? "unsupported" : "regular";
            checksum += state.value.invokeMember(member, base + index + 0.25, base + index + 0.75).asDouble();
        }
        return checksum;
    }
}
