/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.test;

import static org.junit.Assume.assumeTrue;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import jdk.graal.compiler.phases.common.writesinking.WriteSinkingPhase;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.duplication.phases.DeDuplicationPhase;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.UseTrappingNullChecksPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * A small set of test checking the capabilities of the write sinking optimization.
 * <p>
 * In particular, it performs both behavior tests (in which graph shapes are checked) and
 * correctness tests.
 */
@Ignore("Legacy mid-tier write sinking tests")
public class WriteSinkingConsistencyTest extends GraalCompilerTest {

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();
        var pos = s.getMidTier().findPhase(FrameStateAssignmentPhase.class, true);
        pos.add(new WriteSinkingPhase());
        return s;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
            if (!lb.canNeverOverflow()) {
                lb.setCanNeverOverflow();
            }
        }

    }

    @Before
    public void checkOptions() {
        assumeTrue(GraalOptions.OptFloatingReads.getValue(getWriteSinkingTestOptions()));
    }

    private static final int SIZE = 128;

    static class Bla {
        static int s;
        static volatile int sv;

        int a;
        int b;
        int c;
        int d;
        volatile int v;

        Bla(int a, int b, int c, int d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }

        Bla() {
        }

        @Override
        public String toString() {
            return a + "," + b + "," + c + "," + d;
        }

        public Bla copy() {
            return new Bla(a, b, c, d);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Bla) {
                Bla that = (Bla) obj;
                return a == that.a && b == that.b && c == that.c && d == that.d;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return a * 11 + b * 13 + c * 17 + d * 31;
        }
    }

    // STABLE ITERATION ORDER: Tests a hash map.
    static final class FinalHashMap<U, V> extends HashMap<U, V> {
        private static final long serialVersionUID = -6679462014847475037L;
        static final Class<?> VALUE_ITERATOR_CLASS;

        static {
            Class<?> cl = null;
            try {
                cl = Class.forName("java.util.HashMap$ValueIterator");
            } catch (ClassNotFoundException e) {
                throw GraalError.shouldNotReachHere(e);
            }
            VALUE_ITERATOR_CLASS = cl;
        }
    }

    /**
     * Pattern test for map iterators.
     */
    @SuppressWarnings("unchecked")
    public static Object hashIteratorSnippet(FinalHashMap<Object, Object> map) {
        // Cast allows inlining.
        Iterator<Object> itr = (Iterator<Object>) FinalHashMap.VALUE_ITERATOR_CLASS.cast(map.values().iterator());
        Object o = null;
        while (itr.hasNext()) {
            o = itr.next();
        }
        return o;
    }

    @Test
    public void hashIteratorTest() {
        FinalHashMap<Object, Object> map = new FinalHashMap<>();
        map.put("foo", "bar");
        testInnerLoopExtraction("hashIteratorSnippet", null);
        test(getWriteSinkingTestOptions(), "hashIteratorSnippet", map);
    }

    /**
     * In the loop, the read should get eliminated, and writes to {@code a} should sink. Candidate
     * for conditional write sinking for {@code bla.d}.
     */
    public static int fieldWriteSinkSnippet(Bla bla, int b) {
        bla.a = b;
        for (int i = 0; i < b; i++) {
            bla.a = bla.a + i;
            bla.d = bla.b + i;
        }
        return bla.a;
    }

    @Test
    public void forFieldEscapeTest() {
        testLoopExtraction("fieldWriteSinkSnippet", null);
    }

    /**
     * Checks that write sinking can handle control-flow merges to take writes out of loops.
     *
     * <pre>
     * if (c) {...} else {...}
     * b.a = phi(toWrite1, toWrite2);
     * </pre>
     */
    public static int splitObjectAccessSnippet(Bla b, int toWrite1, int toWrite2, boolean c) {
        Objects.requireNonNull(b);
        for (int i = 0; i < SIZE; i++) {
            if (c) {
                b.a = toWrite1;
            } else {
                b.a = toWrite2;
            }
            b.b = i; // Force presence of a merge
        }
        return b.a;
    }

    @Test
    public void splitAccessTest() {
        testLoopExtraction("splitObjectAccessSnippet", null);
    }

    /**
     * This is an example of an array write with constant offset that should sink.
     */
    public static int forArrayAccessSnippet(int[] bla, int b) {
        bla[0] = b;
        for (int i = 0; i < b; i++) {
            bla[0] = bla[0] + i;
        }
        return bla[0];
    }

    @Test
    public void arrayOutOfLoop() {
        testLoopExtraction("forArrayAccessSnippet", null);
    }

    public static int arrayAccessPossiblyOutOfBounds(int[] bla, int b) {
        bla[0] = b;
        for (int i = 0; i < b; i++) {
            bla[4] = bla[0] + i;
        }
        return bla[0];
    }

    @Test
    public void testArrayAccessPossiblyOutOfBounds() {
        testLoopExtraction("arrayAccessPossiblyOutOfBounds", null);

        Result res = test(getWriteSinkingTestOptions(), "arrayAccessPossiblyOutOfBounds", new int[]{0, 1, 2, 3, 4}, 3);
        Assert.assertNotNull(res);
        Assert.assertEquals(3, res.returnValue);

        res = test(getWriteSinkingTestOptions(), "arrayAccessPossiblyOutOfBounds", new int[]{0, 1, 2, 3}, 3);
        Assert.assertNotNull(res);
        Assert.assertEquals(ArrayIndexOutOfBoundsException.class, res.exception.getClass());
    }

    /**
     * Checks that potentially aliasing writes are committed in order.
     */
    public static int orderCheckSnippet(Bla b0, Bla b1, Bla b2, Bla b3, Bla b4, Bla b5, Bla b6, Bla b7, Bla b8, Bla b9) {
        for (int i = 0; i < SIZE; i++) {
            b0.a = b1.b;
            b1.a = b2.b;
            b2.a = b3.b;
            b3.a = b4.b;
            b4.a = b5.b;
            b5.a = b6.b;
            b6.a = b7.b;
            b7.a = b8.b;
            b8.a = b9.b;
            b9.a = b0.b;
        }
        return b0.a;
    }

    @Test
    public void orderCheckTest() {
        Bla b1 = new Bla(1, 2, 3, 4);
        Bla b2 = new Bla(5, 6, 7, 8);
        test(getWriteSinkingTestOptions(), "orderCheckSnippet", b1, b2, b1, b1, b1, b1, b1, b1, b1, b1);
    }

    /**
     * Checks the merge behavior with branches that writes at aliasing location in a different
     * order.
     */
    public static int splitOrder(Bla b1, Bla b2, int a, int b) {
        Objects.requireNonNull(b1);
        Objects.requireNonNull(b2);
        for (int i = 0; i < SIZE; i++) {
            if (b1.c < b2.c) {
                b1.a = a;
                b2.a = b;
            } else {
                b2.a = a;
                b1.a = b;
            }
            b1.c = b2.d;
        }
        return a + b;
    }

    @Test
    public void splitOrderTest() {
        testUnchangedGraph("splitOrder", null);
    }

    /**
     * Validation test.
     */
    public int[] testComplexSnippet(Bla bla, int d) {
        bla.a = 3;
        int y = 5;
        bla.b = 7;
        for (int i = 0; i < d; i++) {
            for (int j = 0; GraalDirectives.injectBranchProbability(0.99, j < i); j++) {
                bla.b += bla.a;
            }
            y = bla.a ^ bla.b;
            if ((i & 4) == 0) {
                bla.b--;
            } else if ((i & 8) == 0) {
                Runtime.getRuntime().totalMemory();
            }
        }
        return new int[]{bla.a, y, bla.b};
    }

    @Test
    public void validateComplexTest() {
        testCondWriteSimplificationMemoryPhi("testComplexSnippet", null);

        for (int i = 0; i < 20; i++) {
            test(getWriteSinkingTestOptions(), "testComplexSnippet", new Bla(), i);
        }
    }

    /**
     * Validation test.
     */
    public static int forSinkSnippet(Bla bla, int b, boolean c) {
        bla.a = b;
        for (int i = 0; i < b; i++) {
            bla.a = bla.a + i;
        }
        if (c) {
            GraalDirectives.deoptimize();
        }
        return bla.a;
    }

    @Test
    public void validateSinkTest() {
        testLoopExtraction("forSinkSnippet", null);
        test(getWriteSinkingTestOptions(), "forSinkSnippet", new Bla(), 10, false);
    }

    /**
     * Simple nesting. the write to {@code bla.a} should sink out.
     */
    public static Bla nestedLoopsSnippet(Bla in) {
        Bla bla = in.copy();
        // Prevent escaping bla
        GraalDirectives.blackhole(bla);
        bla.a = 1;
        bla.b = 20;
        for (int i = 0; i < bla.d; i++) {
            for (int j = i; j < bla.b; j++) {
                bla.a = j * i;
            }

        }
        return bla;
    }

    @Test
    public void nestedLoopsTest() {
        for (int i = 0; i < 20; i++) {
            for (int j = i; j < 20; j++) {
                for (int k = j; k < 20; k++) {
                    for (int l = k; l < 20; l++) {
                        test(getWriteSinkingTestOptions(), "nestedLoopsSnippet", new Bla(i, j, k, l));
                    }
                }
            }
        }
        testLoopExtraction("nestedLoopsSnippet", null);
    }

    /**
     * Complex control flow for nested loops. Currently, the writes in the innermost loops can sink
     * out. When [GR-31730] will be done, all writes to bla.a and bla.b should sink out of all
     * loops.
     */
    public static Bla nestedLoopsSnippetHard(Bla bla) {
        bla.a = bla.c;
        bla.b = bla.d;
        outer: //
        for (int i = 0; i < bla.d; i++) {
            if (i < bla.c / 2) {
                // Loop end for outer.
                continue;
            }
            middle: //
            for (int j = i; j < bla.b; j++) {
                if (i == bla.c) {
                    // Loop exit for middle
                    bla.b = j * j;
                    // Loop end for outer
                    continue outer;
                }
                // This is the only write that should stay in the loop.
                bla.c = i;
                if (j % 4 == bla.b) {
                    // Loop exit for middle
                    break;
                }
                // inner: //
                for (int k = 0; k < j; k++) {
                    bla.a = bla.a + 2;
                    if (k > (i + j) / 2) {
                        // Loop exit for inner
                        // Loop exit for middle
                        // Loop end for outer
                        break middle;
                    }
                    bla.b = bla.b - 2;
                    if (k < bla.a) {
                        // Loop exit for inner
                        // Loop end for middle
                        continue middle;
                    }
                    // Loop end for inner,
                }
                bla.a = j * i;
                if (i % (j + 1) > 1) {
                    // Loop exit for middle
                    // Loop exit for outer
                    break outer;
                }
                // Loop end for middle
            }
            // Loop end for outer
        }
        return bla;
    }

    /**
     * Validation test.
     */
    @Test
    public void nestedLoopsHardTest() {
        testInnerLoopExtraction("nestedLoopsSnippetHard", null);
        testCondWriteSimplificationMemoryPhi("nestedLoopsSnippetHard", null);
        for (int i = 0; i < 20; i++) {
            for (int j = i; j < 20; j++) {
                for (int k = j; k < 20; k++) {
                    for (int l = k; l < 20; l++) {
                        test(getWriteSinkingTestOptions(), "nestedLoopsSnippetHard", new Bla(i, j, k, l));
                    }
                }
            }
        }
    }

    /**
     * All writes should flow out.
     */
    public static Bla loopOverrideSnippet(Bla bla) {
        Objects.requireNonNull(bla);
        for (int i = 0; i < bla.b; i++) {
            bla.a = bla.b;
            if ((i & 2) > 0) {
                bla.a = bla.c;
                continue;
            } else {
                if ((i & 3) > 1) {
                    bla.a = bla.d;
                    continue;
                }
            }
            bla.a *= bla.a;
        }
        return bla;
    }

    @Test
    public void loopOverrideTest() {
        testLoopExtraction("loopOverrideSnippet", null);
    }

    /**
     * The write looks like something WS could take out, but the address written to is not
     * loop-invariant.
     */
    public static void loopVariantAddressSnippet(Bla[] blas) {
        Objects.requireNonNull(blas);
        for (int i = 0; i < blas.length; i++) {
            Bla bla = blas[i];
            bla.a = i;
        }
    }

    @Test
    public void loopVariantAddressTest() {
        testUnchangedGraph("loopVariantAddressSnippet", null);
    }

    /**
     * Candidate for conditional write sinking.
     */
    public static int notUsedInLoopSnippet(Bla in) {
        for (int i = 0; i < in.c; i++) {
            in.d = i + in.d;
        }
        return in.d;
    }

    @Test
    public void notUsedInLoopTest() {
        testLoopExtraction("notUsedInLoopSnippet", null);
    }

    /**
     * Checks that static fields sink.
     */
    public static void staticStorageAccess(int initVal) {
        Bla.s = initVal;
        for (int i = 0; i < 1024; i++) {
            Bla.s = i;
        }
    }

    @Test
    public void staticStorageAccessTest() {
        testLoopExtraction("staticStorageAccess", null);
    }

    /**
     * Checks that volatile instance field write does not sink.
     */
    public static void volatileInstanceFieldWrite(Bla bla, int initVal) {
        bla.v = initVal;
        for (int i = 0; i < 1024; i++) {
            bla.v = i;
        }
    }

    @Test
    public void testVolatileInstanceFieldWrite() {
        testUnchangedGraph("volatileInstanceFieldWrite", null);
    }

    /**
     * Checks that volatile static field write does not sink.
     */
    public static void volatileStaticFieldWrite(int initVal) {
        Bla.sv = initVal;
        for (int i = 0; i < 1024; i++) {
            Bla.sv = i;
        }
    }

    @Test
    public void testVolatileStaticFieldWrite() {
        testUnchangedGraph("volatileStaticFieldWrite", null);
    }

    public static class MyClassWithBoolean {
        public boolean result = false;
    }

    public static void writeBooleanField(MyClassWithBoolean obj, boolean b) {
        obj.result = b;
        for (int i = 0; i < 1024; i++) {
            obj.result = false;
        }
    }

    @Test
    public void testWriteBooleanField() {
        for (int i = 0; i < 100; i++) {
            final MyClassWithBoolean obj = new MyClassWithBoolean();
            obj.result = true;
            writeBooleanField(obj, true);
            Assert.assertFalse(obj.result);
        }

        testLoopExtraction("writeBooleanField", null);

        final MyClassWithBoolean obj = new MyClassWithBoolean();
        obj.result = true;
        test(getWriteSinkingTestOptions(), "writeBooleanField", obj, true);
        Assert.assertFalse(obj.result);
    }

    public static class MyClassWithShort {
        public short result = 0;
    }

    public static void writeShortField(MyClassWithShort obj, short initVal) {
        obj.result = initVal;
        for (int i = 0; i < 1024; i++) {
            obj.result = (short) i;
        }
    }

    @Test
    public void testWriteShortField() {
        for (int i = 0; i < 100; i++) {
            final MyClassWithShort obj = new MyClassWithShort();
            obj.result = -1;
            writeShortField(obj, (short) 32);
            Assert.assertEquals((short) 1023, obj.result);
        }

        testLoopExtraction("writeShortField", null);

        final MyClassWithShort obj = new MyClassWithShort();
        obj.result = -1;
        test(getWriteSinkingTestOptions(), "writeShortField", obj, (short) 32);
        Assert.assertEquals((short) 1023, obj.result);
    }

    public static class MyClassWithInteger {
        public int result = 0;
    }

    public static void writeIntegerField(MyClassWithInteger obj, int initVal) {
        obj.result = initVal;
        for (int i = 0; i < 1024; i++) {
            obj.result = i;
        }
    }

    @Test
    public void testWriteIntegerField() {
        for (int i = 0; i < 100; i++) {
            final MyClassWithInteger obj = new MyClassWithInteger();
            obj.result = -1;
            writeIntegerField(obj, 32);
            Assert.assertEquals((short) 1023, obj.result);
        }

        testLoopExtraction("writeIntegerField", null);

        final MyClassWithInteger obj = new MyClassWithInteger();
        obj.result = -1;
        test(getWriteSinkingTestOptions(), "writeIntegerField", obj, 32);
        Assert.assertEquals(1023, obj.result);
    }

    // See https://stackoverflow.com/a/21388198
    private static ByteBuffer cloneByteBuffer(final ByteBuffer original) {
        // Create clone with same capacity as original.
        final ByteBuffer clone = (original.isDirect()) ? ByteBuffer.allocateDirect(original.capacity()) : ByteBuffer.allocate(original.capacity());

        // copy from the beginning
        original.rewind();
        clone.put(original);
        original.rewind();
        clone.flip();

        return clone;
    }

    private static ByteBuffer prepareByteBuffer(Random random, byte maxValueToWrite) {
        // Prepare buffer with random numbers
        final int bufferSize = 9;
        final ByteBuffer bb = ByteBuffer.allocateDirect(bufferSize);
        for (int j = 0; j < bufferSize; j++) {
            bb.put(j, (byte) random.nextInt(Byte.MAX_VALUE / 2));
        }
        // Force the largest value to be in the second half
        // but no fixed value/index to avoid premature optimization
        int idx = random.nextInt(bufferSize / 2) + bufferSize / 2;
        bb.put(idx, maxValueToWrite);

        return bb;
    }

    public static int getMaxValueOfByteBuffer(ByteBuffer bb, int lenInner) {
        Objects.requireNonNull(bb);

        int maxValue = Byte.MIN_VALUE;
        while (bb.hasRemaining()) {
            maxValue = Math.max(maxValue, bb.get());

            for (int i = 0; i < lenInner; i++) {
                maxValue = Math.max(maxValue, bb.get());
            }
        }
        return maxValue;
    }

    @Test
    public void testWriteByteBuffer() {
        final Random random = new Random(0);

        for (int i = 0; i < 8192; i++) {
            final int maxValueToWrite = random.nextInt(Byte.MAX_VALUE / 2) + Byte.MAX_VALUE / 2;
            final ByteBuffer bb = prepareByteBuffer(random, (byte) maxValueToWrite);
            final int maxValue = getMaxValueOfByteBuffer(bb, 2);
            Assert.assertEquals(maxValueToWrite, maxValue);
            GraalDirectives.blackhole(maxValue);
        }

        final int maxValueToWrite = random.nextInt(Byte.MAX_VALUE / 2) + Byte.MAX_VALUE / 2;
        final ByteBuffer bb = prepareByteBuffer(random, (byte) maxValueToWrite);
        final ArgSupplier bbSupplier = () -> cloneByteBuffer(bb);
        final Result res = test(getWriteSinkingTestOptions(), "getMaxValueOfByteBuffer", bbSupplier, 2);
        Assert.assertNotNull(res);
        Assert.assertEquals(maxValueToWrite, res.returnValue);
    }

    private static ByteBuffer prepareByteBufferSimple(int bufferSize) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(bufferSize);
        for (int j = 0; j < bufferSize; j++) {
            bb.put(j, (byte) 1);
        }

        return bb;
    }

    public static int sumByteBufferNested(ByteBuffer bb, int lenInner) {
        Objects.requireNonNull(bb);

        int sum = 0;
        while (bb.hasRemaining()) {
            sum += bb.get();

            for (int i = 0; i < lenInner; i++) {
                sum += bb.get();
            }
        }
        return sum;
    }

    @Test
    public void testSumByteBufferNested() {
        final int bufferSize = 9;

        for (int i = 0; i < 8192; i++) {
            final ByteBuffer bb = prepareByteBufferSimple(bufferSize);
            final int sum = sumByteBufferNested(bb, 2);
            Assert.assertEquals(bufferSize, sum);
        }

        final ByteBuffer bb = prepareByteBufferSimple(bufferSize);
        final ArgSupplier bbSupplier = () -> cloneByteBuffer(bb);
        final Result res = test(getWriteSinkingTestOptions(), "sumByteBufferNested", bbSupplier, 2);
        Assert.assertNotNull(res);
        Assert.assertEquals(bufferSize, res.returnValue);
    }

    public static void incrementNested(MyClassWithInteger obj, int countOuter, int countInner) {
        while (obj.result < countOuter) {
            obj.result++;

            for (int i = 0; i < countInner; i++) {
                obj.result++;
            }
        }
    }

    @Test
    public void testIncrementNested() {
        final int countOuter = 9;
        final int countInner = 2;
        for (int i = 0; i < 8192; i++) {
            final MyClassWithInteger counter = new MyClassWithInteger();
            incrementNested(counter, countOuter, countInner);
            Assert.assertEquals(countOuter, counter.result);
        }

        final MyClassWithInteger counter = new MyClassWithInteger();
        final Result res = test(getWriteSinkingTestOptions(), "incrementNested", counter, countOuter, countInner);
        Assert.assertNotNull(res);
        Assert.assertEquals(countOuter, counter.result);
    }

    private static class MyStringParser {
        private final String source;
        private final int length;
        private int pos = 0;

        MyStringParser(String source) {
            this.source = source;
            this.length = source.length();
        }
    }

    public static void skipWhiteSpaceIf(MyStringParser strParser) {
        while (strParser.pos < strParser.length) {
            if (strParser.source.charAt(strParser.pos) == ' ') {
                strParser.pos++;
            } else {
                return;
            }
        }
    }

    @Test
    public void testSkipWhiteSpaceIf() {
        for (int i = 0; i < 8192; i++) {
            MyStringParser strParser = new MyStringParser("  A");
            skipWhiteSpaceIf(strParser);
            Assert.assertEquals(2, strParser.pos);
            skipWhiteSpaceIf(strParser);
            Assert.assertEquals(2, strParser.pos);

            strParser = new MyStringParser("AB  CD");
            skipWhiteSpaceIf(strParser);
            Assert.assertEquals(0, strParser.pos);
            skipWhiteSpaceIf(strParser);
            Assert.assertEquals(0, strParser.pos);

            strParser = new MyStringParser("   ");
            skipWhiteSpaceIf(strParser);
            Assert.assertEquals(3, strParser.pos);
            skipWhiteSpaceIf(strParser);
            Assert.assertEquals(3, strParser.pos);
        }

        MyStringParser strParser = new MyStringParser("  A");
        test(getWriteSinkingTestOptions(), "skipWhiteSpaceIf", strParser);
        Assert.assertEquals(2, strParser.pos);
        test(getWriteSinkingTestOptions(), "skipWhiteSpaceIf", strParser);
        Assert.assertEquals(2, strParser.pos);
    }

    public static int readAfterInvertedLoop(MyClassWithInteger start, int init, int limit) {
        if (start == null) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        int i = init;
        if (GraalDirectives.injectBranchProbability(0.99999, i < limit)) {
            do {
                start.result++;
                i++;
            } while (GraalDirectives.injectIterationCount(10000, i < limit));
        }

        return start.result;
    }

    @Test
    public void testReadAfterInvertedLoop() {
        final ArgSupplier startSupplier = MyClassWithInteger::new;
        Result res = test(getWriteSinkingTestOptions(), "readAfterInvertedLoop", startSupplier, 0, 5);
        Assert.assertNotNull(res);
        Assert.assertEquals(5, res.returnValue);
    }

    public static int readFromTwoExits(MyClassWithInteger start, int init, int limit1, int limit2, int limit3) {
        if (start == null) {
            GraalDirectives.deoptimizeAndInvalidate();
        }

        int i = init;
        if (GraalDirectives.injectBranchProbability(0.99999, i < limit1)) {
            outer: while (GraalDirectives.injectIterationCount(10000, true)) {
                while (GraalDirectives.injectIterationCount(10000, true)) {
                    i++;

                    if (i > limit1) {
                        break outer;
                    }

                    start.result++;

                    if (i > limit2) {
                        break outer;
                    }

                    start.result++;

                    if (i > limit3) {
                        continue outer;
                    }
                }
            }

        }

        return start.result;
    }

    @Test
    public void testReadFromTwoExits() {
        final ArgSupplier startSupplier = MyClassWithInteger::new;
        Result res;

        res = test(getWriteSinkingTestOptions(), "readFromTwoExits", startSupplier, 0, 10, 5, 2);
        Assert.assertNotNull(res);
        Assert.assertEquals(11, res.returnValue);

        res = test(getWriteSinkingTestOptions(), "readFromTwoExits", startSupplier, 0, 5, 10, 2);
        Assert.assertNotNull(res);
        Assert.assertEquals(10, res.returnValue);
    }

    @Test
    public void testExcludeFieldsSpecific() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(WriteSinkingPhase.Options.WriteSinkingExcludeFields, "Bla.d");
        testUnchangedGraph("notUsedInLoopSnippet", new OptionValues(map));

        map.put(WriteSinkingPhase.Options.WriteSinkingExcludeFields, "Bla.a,Bla.d");
        testUnchangedGraph("fieldWriteSinkSnippet", new OptionValues(map));

        // Bla.d should sink out of inner loop
        map.put(WriteSinkingPhase.Options.WriteSinkingExcludeFields, "Bla.a");
        testInnerLoopExtraction("fieldWriteSinkSnippet", new OptionValues(map));
    }

    @Test
    public void testExcludeFieldsGlob() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(WriteSinkingPhase.Options.WriteSinkingExcludeFields, "Bla.*");
        testUnchangedGraph("fieldWriteSinkSnippet", new OptionValues(map));
        testLoopExtraction("writeIntegerField", new OptionValues(map));

        map.put(WriteSinkingPhase.Options.WriteSinkingExcludeFields, "*");
        testUnchangedGraph("fieldWriteSinkSnippet", new OptionValues(map));
        testUnchangedGraph("writeIntegerField", new OptionValues(map));
    }

    public static void simpleLoopWithWriteSinkDirective(Bla obj, int initVal) {
        obj.a = initVal;
        for (int i = 0; i < 1024; i++) {
            GraalDirectives.neverWriteSink();
            obj.a = i;
        }
    }

    @Test
    public void testSimpleLoopWithWriteSinkDirective() {
        for (int i = 0; i < 100; i++) {
            final Bla obj = new Bla();
            obj.a = -1;
            simpleLoopWithWriteSinkDirective(obj, 32);
            Assert.assertEquals((short) 1023, obj.a);
        }

        testUnchangedGraph("simpleLoopWithWriteSinkDirective", null);

        final Bla obj = new Bla();
        obj.a = -1;
        test(getWriteSinkingTestOptions(), "simpleLoopWithWriteSinkDirective", obj, 32);
        Assert.assertEquals(1023, obj.a);
    }

    public static Bla nestedLoopOuterSinkableSnippet(Bla in) {
        Bla bla = in.copy();
        // Prevent escaping bla
        GraalDirectives.blackhole(bla);
        bla.a = 1;
        bla.b = 20;
        for (int i = 0; i < bla.d; i++) {
            for (int j = i; j < bla.b; j++) {
                GraalDirectives.controlFlowAnchor();
            }
            bla.a = bla.b * i;
        }
        return bla;
    }

    @Test
    public void testNestedLoopOuterSinkableSnippet() {
        for (int i = 0; i < 20; i++) {
            for (int j = i; j < 20; j++) {
                for (int k = j; k < 20; k++) {
                    for (int l = k; l < 20; l++) {
                        test(getWriteSinkingTestOptions(), "nestedLoopOuterSinkableSnippet", new Bla(i, j, k, l));
                    }
                }
            }
        }
        testLoopExtraction("nestedLoopOuterSinkableSnippet", null);
    }

    public static Bla nestedLoopOuterSinkableAdditionalExitInnerSnippet(Bla in) {
        Bla bla = in.copy();
        // Prevent escaping bla
        GraalDirectives.blackhole(bla);
        bla.a = 1;
        bla.b = 20;
        for (int i = 0; i < bla.d; i++) {
            for (int j = i; j < bla.b; j++) {
                GraalDirectives.controlFlowAnchor();
                if (bla.c > 100) {
                    break;
                }
            }
            bla.a = bla.b * i;
        }
        return bla;
    }

    @Test
    public void testNestedLoopOuterSinkableAdditionalExitInnerSnippet() {
        testLoopExtraction("nestedLoopOuterSinkableAdditionalExitInnerSnippet", null);
    }

    public static Bla nestedLoopOuterSinkableAdditionalExitOuterSnippet(Bla in) {
        Bla bla = in.copy();
        // Prevent escaping bla
        GraalDirectives.blackhole(bla);
        bla.a = 1;
        bla.b = 20;
        for (int i = 0; i < bla.d; i++) {
            for (int j = i; j < bla.b; j++) {
                GraalDirectives.controlFlowAnchor();
            }
            if (bla.c > 100) {
                break;
            }
            bla.a = bla.b * i;
        }
        return bla;
    }

    @Test
    public void testNestedLoopOuterSinkableAdditionalExitOuterSnippet() {
        testLoopExtraction("nestedLoopOuterSinkableAdditionalExitOuterSnippet", null);
    }

    public static Bla nestedLoopOuterSinkableAdditionalExitsInnerOuterSnippet(Bla in) {
        Bla bla = in.copy();
        // Prevent escaping bla
        GraalDirectives.blackhole(bla);
        bla.a = 1;
        bla.b = 20;
        for (int i = 0; i < bla.d; i++) {
            for (int j = i; j < bla.b; j++) {
                GraalDirectives.controlFlowAnchor();
                if (bla.c > 10) {
                    break;
                }
            }
            if (bla.c > 100) {
                break;
            }
            bla.a = bla.b * i;
        }
        return bla;
    }

    @Test
    public void testNestedLoopOuterSinkableAdditionalExitsInnerOuterSnippet() {
        testLoopExtraction("nestedLoopOuterSinkableAdditionalExitsInnerOuterSnippet", null);
    }

    private static int removableLoopSnippet(Bla obj, int limit) {
        for (int i = 0; i < limit; i++) {
            obj.a = i;
        }
        return obj.a;
    }

    @Test
    public void testRemovableLoopSnippet() {
        for (int i = 0; i < 100; i++) {
            final Bla obj = new Bla();
            int res = removableLoopSnippet(obj, 1024);
            Assert.assertEquals(1023, res);
            Assert.assertEquals(1023, obj.a);
        }

        final Bla obj = new Bla();
        Result res = test(getWriteSinkingTestOptions(), "removableLoopSnippet", obj, 1024);
        Assert.assertEquals(1023, res.returnValue);
        Assert.assertEquals(1023, obj.a);
    }

    private static final int BLOCK_LENGTH_SMALL = 4;

    public static void removableLoopWithReadEliminationSmallSnippet(Bla obj) {
        while (obj.a > BLOCK_LENGTH_SMALL) {
            obj.a = obj.a - BLOCK_LENGTH_SMALL;
        }
    }

    @Test
    public void testRemovableLoopWithReadEliminationSmallSnippet() {
        for (int i = 0; i < 10; i++) {
            final Bla obj = new Bla();
            obj.a = 40 * BLOCK_LENGTH_SMALL;
            removableLoopWithReadEliminationSmallSnippet(obj);
            Assert.assertEquals(BLOCK_LENGTH_SMALL, obj.a);
        }

        final Bla obj = new Bla();
        obj.a = 40 * BLOCK_LENGTH_SMALL;
        test(getWriteSinkingTestOptions(), "removableLoopWithReadEliminationSmallSnippet", obj);
        Assert.assertEquals(BLOCK_LENGTH_SMALL, obj.a);
    }

    private static final int BLOCK_LENGTH_LARGE = 5;

    public static void removableLoopWithReadEliminationLargeSnippet(Bla obj) {
        while (obj.a > BLOCK_LENGTH_LARGE) {
            obj.a = obj.a - BLOCK_LENGTH_LARGE;
        }
    }

    @Test
    public void testRemovableLoopWithReadEliminationLargeSnippet() {
        for (int i = 0; i < 10; i++) {
            final Bla obj = new Bla();
            obj.a = 40 * BLOCK_LENGTH_LARGE;
            removableLoopWithReadEliminationLargeSnippet(obj);
            Assert.assertEquals(BLOCK_LENGTH_LARGE, obj.a);
        }

        final Bla obj = new Bla();
        obj.a = 40 * BLOCK_LENGTH_LARGE;
        test(getWriteSinkingTestOptions(), "removableLoopWithReadEliminationLargeSnippet", obj);
        Assert.assertEquals(BLOCK_LENGTH_LARGE, obj.a);
    }

    public static int optimizablePhiFromIVNontrivialStride(int notEnteredValue, int start, int end) {
        int optimizablePhi = notEnteredValue;
        for (int i = start; i < end; i += 5) {
            optimizablePhi = i;
        }
        return optimizablePhi;
    }

    @Test
    public void runOptimizablePhiFromIVNontrivialStride() {
        test("optimizablePhiFromIVNontrivialStride", 23, 10, 10);   // loop not entered
        test("optimizablePhiFromIVNontrivialStride", 23, 10, 1000); // loop entered
    }

    Function<EconomicMap<FixedNode, Integer>, Phase> countLoopWritesPhase = (loops) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph graph) {
            ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
            for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
                int writes = 0;
                for (HIRBlock block : loop.getBlocks()) {
                    if (block.getLoop() == loop) {
                        for (FixedNode node : block.getNodes()) {
                            if (node instanceof WriteNode) {
                                writes++;
                            }
                        }
                    }
                }
                loops.put(loop.getHeader().getBeginNode(), writes);
            }
        }
    };

    Function<EconomicMap<FixedNode, Integer>, Phase> checkLoopExtractionPhase = (loops) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph graph) {
            ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
            for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
                int writes = 0;
                for (HIRBlock block : loop.getBlocks()) {
                    if (block.getLoop() == loop) {
                        for (FixedNode node : block.getNodes()) {
                            if (node instanceof WriteNode && !(((WriteNode) node).next() instanceof DeoptimizeNode)) {
                                writes++;
                            }
                        }
                    }
                }
                int old = loops.get(loop.getHeader().getBeginNode());
                if (old == 0) {
                    assertTrue(writes == 0);
                } else {
                    assertTrue(writes < old);
                }
            }
        }
    };

    Function<EconomicMap<FixedNode, Integer>, Phase> checkInnerLoopExtractionPhase = (loops) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph graph) {
            ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
            for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
                if (loop.getChildren().isEmpty()) {
                    int writes = 0;
                    for (HIRBlock block : loop.getBlocks()) {
                        if (block.getLoop() == loop) {
                            for (FixedNode node : block.getNodes()) {
                                if (node instanceof WriteNode && !(((WriteNode) node).next() instanceof DeoptimizeNode)) {
                                    writes++;
                                }
                            }
                        }
                    }
                    int old = loops.get(loop.getHeader().getBeginNode());
                    if (old == 0) {
                        assertTrue(writes == 0);
                    } else {
                        assertTrue(writes < old);
                    }
                }
            }
        }
    };

    Function<EconomicSet<Graph>, Phase> rememberGraph = (graphContainer) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph beforeWriteSinking) {
            graphContainer.add(beforeWriteSinking.copy(beforeWriteSinking.getDebug()));
        }
    };

    Function<EconomicSet<Graph>, Phase> checkGraphIdentical = (graphContainer) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph afterWriteSinking) {
            Graph beforeWriteSinking = graphContainer.iterator().next();
            assertEquals((StructuredGraph) beforeWriteSinking, afterWriteSinking);
        }
    };

    Function<EconomicSet<MergeNode>, Phase> rememberMergeNodes = (mergeNodes) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph graph) {
            for (MergeNode mergeNode : graph.getNodes(MergeNode.TYPE)) {
                mergeNodes.add(mergeNode);
            }
        }
    };

    Function<EconomicSet<MergeNode>, Phase> checkNewMergeNodeMemoryPhiConsistency = (oldMergeNodes) -> new TestPhase() {
        @Override
        protected void run(StructuredGraph graph) {
            for (MergeNode mergeNode : graph.getNodes(MergeNode.TYPE)) {
                if (oldMergeNodes.contains(mergeNode)) {
                    continue;
                }
                for (MemoryPhiNode memPhi : mergeNode.memoryPhis()) {
                    assert memPhi.valueCount() == 2;
                    ValueNode[] memPhiValues = memPhi.values().toArray(ValueNode.EMPTY_ARRAY);
                    int writePredecessorIndex = -1;
                    if (memPhiValues[0] instanceof WriteNode && ((WriteNode) memPhiValues[0]).getLastLocationAccess().asNode().equals(memPhiValues[1])) {
                        writePredecessorIndex = 0;
                    } else if (memPhiValues[1] instanceof WriteNode && ((WriteNode) memPhiValues[1]).getLastLocationAccess().asNode().equals(memPhiValues[0])) {
                        writePredecessorIndex = 1;
                    }
                    WriteNode write = (WriteNode) memPhiValues[writePredecessorIndex];

                    EndNode[] mergeNodePreds = mergeNode.cfgPredecessors().snapshot().toArray(new EndNode[0]);
                    assertTrue(branchContainsWrite(mergeNodePreds[writePredecessorIndex], write));
                }
            }
        }
    };

    private static boolean branchContainsWrite(Node endNode, WriteNode writeNode) {
        for (Node cur = endNode; !(cur instanceof IfNode); cur = cur.predecessor()) {
            if (cur.equals(writeNode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Since writes currently bumps at safepoints while sinking, remove them for the consistency
     * checks.
     */
    private static final class RemoveSafepointsTestPhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            for (SafepointNode safepoint : graph.getNodes().filter(SafepointNode.class)) {
                graph.removeFixed(safepoint);
            }
        }

        @Override
        public CharSequence getName() {
            return "RemoveSafepointsTestPhase";
        }
    }

    static OptionValues getWriteSinkingTestOptions() {
        OptionValues options = getInitialOptions();
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create(options.getMap());
        // Disable a few transformations, as it makes some of the tested shapes disappear.
        map.put(GraalOptions.PartialUnroll, false);
        map.put(DeDuplicationPhase.Options.OptDeDuplication, false);
        map.put(GraalOptions.LoopUnswitch, false);
        map.put(GraalOptions.OptDuplication, false);
        return new OptionValues(map);
    }

    /**
     * Compiles a given method using the write sinking optimization. Ensures that all loops in the
     * graph have at least one less write in their body.
     */
    public void testLoopExtraction(String snippet, OptionValues additionalOptions) {
        EconomicMap<FixedNode, Integer> loopCounts = EconomicMap.create();
        doTest(snippet, WriteSinkingPhase.class, countLoopWritesPhase.apply(loopCounts), WriteSinkingPhase.class, checkLoopExtractionPhase.apply(loopCounts), additionalOptions);
    }

    /**
     * Compiles a given method using the write sinking optimization. Ensures that innermost loops in
     * the graph have at least one less write in their body.
     */
    public void testInnerLoopExtraction(String snippet, OptionValues additionalOptions) {
        EconomicMap<FixedNode, Integer> loopCounts = EconomicMap.create();
        doTest(snippet, WriteSinkingPhase.class, countLoopWritesPhase.apply(loopCounts), WriteSinkingPhase.class, checkInnerLoopExtractionPhase.apply(loopCounts), additionalOptions);
    }

    /**
     * Compiles a given method using the write sinking optimization. Check that write sinking is not
     * applicable and thus the graph remains unchanged (idempotent phase).
     */
    public void testUnchangedGraph(String snippet, OptionValues additionalOptions) {
        EconomicSet<Graph> graphContainer = EconomicSet.create();
        doTest(snippet, WriteSinkingPhase.class, rememberGraph.apply(graphContainer), WriteSinkingPhase.class, checkGraphIdentical.apply(graphContainer), additionalOptions);
    }

    /**
     * Compiles a given method using the write sinking optimization. Ensures that the conditional
     * write simplification performs a correct memory phi replacement (if mphi required).
     */
    public void testCondWriteSimplificationMemoryPhi(String snippet, OptionValues additionalOptions) {
        EconomicSet<MergeNode> mergeNodes = EconomicSet.create();
        doTest(snippet, WriteSinkingPhase.class, rememberMergeNodes.apply(mergeNodes), WriteSinkingPhase.class, checkNewMergeNodeMemoryPhiConsistency.apply(mergeNodes), additionalOptions);
    }

    /**
     * Performs a compilation of the given snippet, up until write sinking is applied. Graph shape
     * checks are performed in the for of the {@code beforePhase} and {@code afterPhase} arguments,
     * which wraps around the write sinking phase to do these checks...
     */
    private void doTest(String snippet,
                    Class<? extends BasePhase<? super CoreProviders>> beforeRef,
                    Phase beforePhase,
                    Class<? extends BasePhase<? super CoreProviders>> afterRef,
                    Phase afterPhase,
                    OptionValues additionalOptions) {
        // Merge additional options
        OptionValues options = getWriteSinkingTestOptions();
        OptionValues completeOptions = mergeOptionValues(options, additionalOptions);

        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO, completeOptions);
        Suites suites = createSuites(completeOptions);
        suites.getHighTier().apply(graph, getDefaultHighTierContext());
        MidTierContext defaultMidTierContext = getDefaultMidTierContext();
        PhaseSuite<MidTierContext> midTier = suites.getMidTier();

        ListIterator<BasePhase<? super MidTierContext>> cursor;
        cursor = midTier.findPhase(beforeRef, true);
        cursor.previous();
        cursor.add(new RemoveSafepointsTestPhase());
        if (beforePhase != null) {
            cursor.add(beforePhase);
        }
        cursor = midTier.findPhase(afterRef, true);
        if (afterPhase != null) {
            cursor.add(afterPhase);
        }
        // Check graph is still schedulable after WS
        cursor.add(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        midTier.apply(graph, defaultMidTierContext);
    }

    static OptionValues mergeOptionValues(OptionValues options1, OptionValues options2) {
        if (options2 == null) {
            return options1;
        }
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create(options1.getMap());
        map.putAll(options2.getMap());
        return new OptionValues(map);
    }

    public static int arrayIteratorSnippet(Object[] values) {
        ArrayIteratorTest<Object> obs = new ArrayIteratorTest<>();
        obs.values = values;
        int res = 0;
        for (Object n : obs.values()) {
            res += n.hashCode();
        }
        return res;
    }

    static class ArrayIteratorTest<T> {

        T[] values;

        public Iterable<T> values() {
            return new AIterable();
        }

        public class AIterable implements Iterable<T> {

            @Override
            public Iterator<T> iterator() {
                return new Iterator<>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        forward();
                        return i < ArrayIteratorTest.this.values.length;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public T next() {
                        final int pos = i;
                        final T value = ArrayIteratorTest.this.values[pos];
                        i++;
                        forward();
                        return value;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void forward() {
                        while (i < ArrayIteratorTest.this.values.length && ArrayIteratorTest.this.values[i] == null) {
                            i++;
                        }
                    }

                };
            }

        }
    }

    @Ignore("GR-46155 - Remove memory graph before WS")
    @SuppressWarnings("rawtypes")
    @Test
    public void iteratorTest() {
        Object[] values = new Object[]{null, 1, 2, 3, 4, null};
        for (int i = 0; i < 10000; i++) {
            arrayIteratorSnippet(values);
        }
        testInnerLoopExtraction("arrayIteratorSnippet", null);
        OptionValues options = getInitialOptions();
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create(options.getMap());
        map.put(GraalOptions.PartialUnroll, false);
        map.put(GraalOptions.OptReadElimination, false);
        map.put(GraalOptions.PartialEscapeAnalysis, false);
        map.put(GraalOptions.LoopPeeling, false);
        map.put(GraalOptions.OptDuplication, false);
        map.put(GraalOptions.OptDeoptimizationGrouping, false);
        map.put(UseTrappingNullChecksPhase.Options.UseTrappingNullChecks, false);
        map.put(GraalOptions.RawConditionalElimination, false);

        test(new OptionValues(options, map), "arrayIteratorSnippet", new GraalCompilerTest.ArgSupplier() {
            @Override
            public Object get() {
                return values;
            }
        });
    }

}
