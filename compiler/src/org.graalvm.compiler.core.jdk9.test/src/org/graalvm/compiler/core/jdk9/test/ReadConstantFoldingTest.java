/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.jdk9.test;

import static jdk.internal.misc.Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_CHAR_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_FLOAT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_INT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_LONG_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_SHORT_BASE_OFFSET;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.test.AddExports;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Exercise the constant folding of {@link RawLoadNode} and it's lowered form to ensure that it
 * always succeeds when it's possible.
 */
@AddExports("java.base/jdk.internal.misc")
public class ReadConstantFoldingTest extends GraalCompilerTest {
    static final Unsafe U = Unsafe.getUnsafe();
    private static final List<Object> StableArrays = new ArrayList<>();
    // These are treated as @Stable fields thanks to the applyStable method
    static final boolean[] STABLE_BOOLEAN_ARRAY = register(new boolean[16]);
    static final byte[] STABLE_BYTE_ARRAY = register(new byte[16]);
    static final short[] STABLE_SHORT_ARRAY = register(new short[8]);
    static final char[] STABLE_CHAR_ARRAY = register(new char[8]);
    static final int[] STABLE_INT_ARRAY = register(new int[4]);
    static final long[] STABLE_LONG_ARRAY = register(new long[2]);
    static final float[] STABLE_FLOAT_ARRAY = register(new float[4]);
    static final double[] STABLE_DOUBLE_ARRAY = register(new double[2]);
    static final Object[] STABLE_OBJECT_ARRAY = register(new Object[4]);

    static {
        // Tests will set/reset the first element of those arrays.
        // Place a canary on the second element to catch issues involving reading more than the
        // first element.
        STABLE_BYTE_ARRAY[1] = 0x10;
        STABLE_SHORT_ARRAY[1] = 0x10;
        STABLE_CHAR_ARRAY[1] = 0x10;
        STABLE_BOOLEAN_ARRAY[1] = true;
    }

    @Before
    public void before() {
        Setter.reset();
    }

    static class Setter {

        static boolean nonDefaultZ() {
            return true;
        }

        // the integer values are selected to make sure sign/zero-extension behaviour is tested.
        static byte nonDefaultB() {
            return -1;
        }

        static short nonDefaultS() {
            return -1;
        }

        static char nonDefaultC() {
            return Character.MAX_VALUE;
        }

        static int nonDefaultI() {
            return -1;
        }

        static long nonDefaultJ() {
            return -1;
        }

        static float nonDefaultF() {
            return Float.MAX_VALUE;
        }

        static double nonDefaultD() {
            return Double.MAX_VALUE;
        }

        private static void setZ(boolean defaultVal) {
            STABLE_BOOLEAN_ARRAY[0] = defaultVal ? false : nonDefaultZ();
        }

        private static void setB(boolean defaultVal) {
            STABLE_BYTE_ARRAY[0] = defaultVal ? 0 : nonDefaultB();
        }

        private static void setS(boolean defaultVal) {
            STABLE_SHORT_ARRAY[0] = defaultVal ? 0 : nonDefaultS();
        }

        private static void setC(boolean defaultVal) {
            STABLE_CHAR_ARRAY[0] = defaultVal ? 0 : nonDefaultC();
        }

        private static void setI(boolean defaultVal) {
            STABLE_INT_ARRAY[0] = defaultVal ? 0 : nonDefaultI();
        }

        private static void setJ(boolean defaultVal) {
            STABLE_LONG_ARRAY[0] = defaultVal ? 0 : nonDefaultJ();
        }

        private static void setF(boolean defaultVal) {
            STABLE_FLOAT_ARRAY[0] = defaultVal ? 0 : nonDefaultF();
        }

        private static void setD(boolean defaultVal) {
            STABLE_DOUBLE_ARRAY[0] = defaultVal ? 0 : nonDefaultD();
        }

        private static void setL(boolean defaultVal) {
            STABLE_OBJECT_ARRAY[0] = defaultVal ? null : new Object();
        }

        static void reset() {
            setZ(false);
            setB(false);
            setS(false);
            setC(false);
            setI(false);
            setJ(false);
            setF(false);
            setD(false);
            setL(false);
        }
    }

    private static <T> T register(T t) {
        StableArrays.add(t);
        return t;
    }

    /**
     * Should the compilation completely constant fold.
     */
    private boolean shouldFold;

    /**
     * Insert an {@link OpaqueNode} after parsing that is removed by
     * {@link #checkHighTierGraph(StructuredGraph)} to ensure that the lowered form also correctly
     * constant folds.
     */
    private boolean insertOpaque;

    @Before
    public void reset() {
        shouldFold = true;
        insertOpaque = false;
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        StructuredGraph g = parseForCompile(installedCodeOwner, options);
        if (insertOpaque) {
            for (RawLoadNode node : g.getNodes().filter(RawLoadNode.class)) {
                OpaqueNode opaque = new OpaqueNode(node.object());
                g.unique(opaque);
                node.replaceFirstInput(node.object(), opaque);
            }
        }
        return super.getCode(installedCodeOwner, g, true, installAsDefault, options);
    }

    /**
     * Only parse the test methods since the compiled might form crash because of oop unsafety.
     */
    void doParse(String name) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        getCode(method);
        insertOpaque = true;
        getCode(method);
    }

    void doTest(String name) {
        test(name);
        insertOpaque = true;
        test(name);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        NodeIterable<ReturnNode> returnNodes = graph.getNodes(ReturnNode.TYPE);
        Assert.assertEquals(1, returnNodes.count());
        Assert.assertEquals("expected constant return value" + (insertOpaque ? " for opaque" : ""), shouldFold, returnNodes.first().result().isJavaConstant());
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (insertOpaque) {
            // Remove the OpaquNode so that the lowered form can be tested as well.
            for (OpaqueNode node : graph.getNodes().filter(OpaqueNode.class)) {
                node.replaceAndDelete(node.getValue());
            }
        }
        super.checkHighTierGraph(graph);
    }

    @Override
    protected StructuredGraph parse(Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        StructuredGraph graph = super.parse(builder, graphBuilderSuite);
        applyStable(graph);
        return graph;
    }

    /**
     * Finds each {@link ConstantNode} in {@code graph} that wraps one of the {@link #StableArrays}
     * and replaces it with {@link ConstantNode} with a
     * {@linkplain ConstantNode#getStableDimension() stable dimension} of 1.
     */
    private void applyStable(StructuredGraph graph) {
        if (graph.method().getDeclaringClass().toJavaName().equals(ReadConstantFoldingTest.class.getName())) {
            SnippetReflectionProvider snippetReflection = getSnippetReflection();
            for (ConstantNode cn : graph.getNodes().filter(ConstantNode.class)) {
                JavaConstant javaConstant = (JavaConstant) cn.getValue();
                Object obj = snippetReflection.asObject(Object.class, javaConstant);
                if (StableArrays.contains(obj)) {
                    ConstantNode stableConstant = ConstantNode.forConstant(javaConstant, 1, cn.isDefaultStable(), getMetaAccess());
                    graph.unique(stableConstant);
                    cn.replace(graph, stableConstant);
                    break;
                }
            }
        }
    }

    private static boolean getBoolean(Object object, long offset) {
        return U.getBoolean(object, offset);
    }

    private static byte getByte(Object object, long offset) {
        return U.getByte(object, offset);
    }

    private static char getChar(Object object, long offset) {
        return U.getChar(object, offset);
    }

    private static short getShort(Object object, long offset) {
        return U.getShort(object, offset);
    }

    private static int getInt(Object object, long offset) {
        return U.getInt(object, offset);
    }

    private static float getFloat(Object object, long offset) {
        return U.getFloat(object, offset);
    }

    private static long getLong(Object object, long offset) {
        return U.getLong(object, offset);
    }

    private static double getDouble(Object object, long offset) {
        return U.getDouble(object, offset);
    }

    private static Object getObject(Object object, long offset) {
        return U.getObject(object, offset);
    }

    public boolean readBooleanFromBooleanCastBoolean() {
        return getBoolean(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromBooleanCastBoolean() {
        doTest("readBooleanFromBooleanCastBoolean");
    }

    public boolean readBooleanFromByteCastBoolean() {
        return getBoolean(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromByteCastBoolean() {
        doTest("readBooleanFromByteCastBoolean");
    }

    public boolean readBooleanFromShortCastBoolean() {
        return getBoolean(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromShortCastBoolean() {
        doTest("readBooleanFromShortCastBoolean");
    }

    public boolean readBooleanFromCharCastBoolean() {
        return getBoolean(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromCharCastBoolean() {
        doTest("readBooleanFromCharCastBoolean");
    }

    public boolean readBooleanFromIntCastBoolean() {
        return getBoolean(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromIntCastBoolean() {
        doTest("readBooleanFromIntCastBoolean");
    }

    public boolean readBooleanFromFloatCastBoolean() {
        return getBoolean(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromFloatCastBoolean() {
        doTest("readBooleanFromFloatCastBoolean");
    }

    public boolean readBooleanFromLongCastBoolean() {
        return getBoolean(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromLongCastBoolean() {
        doTest("readBooleanFromLongCastBoolean");
    }

    public boolean readBooleanFromDoubleCastBoolean() {
        return getBoolean(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromDoubleCastBoolean() {
        doTest("readBooleanFromDoubleCastBoolean");
    }

    public boolean readBooleanFromObjectCastBoolean() {
        return getBoolean(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testBooleanFromObjectCastBoolean() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readBooleanFromObjectCastBoolean");
    }

    public byte readByteFromBooleanCastByte() {
        return getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastByte() {
        doTest("readByteFromBooleanCastByte");
    }

    public byte readByteFromByteCastByte() {
        return getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastByte() {
        doTest("readByteFromByteCastByte");
    }

    public byte readByteFromShortCastByte() {
        return getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastByte() {
        doTest("readByteFromShortCastByte");
    }

    public byte readByteFromCharCastByte() {
        return getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastByte() {
        doTest("readByteFromCharCastByte");
    }

    public byte readByteFromIntCastByte() {
        return getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastByte() {
        doTest("readByteFromIntCastByte");
    }

    public byte readByteFromFloatCastByte() {
        return getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastByte() {
        doTest("readByteFromFloatCastByte");
    }

    public byte readByteFromLongCastByte() {
        return getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastByte() {
        doTest("readByteFromLongCastByte");
    }

    public byte readByteFromDoubleCastByte() {
        return getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastByte() {
        doTest("readByteFromDoubleCastByte");
    }

    public byte readByteFromObjectCastByte() {
        return getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastByte");
    }

    public byte readShortFromBooleanCastByte() {
        return (byte) getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastByte() {
        doTest("readShortFromBooleanCastByte");
    }

    public byte readShortFromByteCastByte() {
        return (byte) getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastByte() {
        doTest("readShortFromByteCastByte");
    }

    public byte readShortFromShortCastByte() {
        return (byte) getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastByte() {
        doTest("readShortFromShortCastByte");
    }

    public byte readShortFromCharCastByte() {
        return (byte) getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastByte() {
        doTest("readShortFromCharCastByte");
    }

    public byte readShortFromIntCastByte() {
        return (byte) getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastByte() {
        doTest("readShortFromIntCastByte");
    }

    public byte readShortFromFloatCastByte() {
        return (byte) getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastByte() {
        doTest("readShortFromFloatCastByte");
    }

    public byte readShortFromLongCastByte() {
        return (byte) getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastByte() {
        doTest("readShortFromLongCastByte");
    }

    public byte readShortFromDoubleCastByte() {
        return (byte) getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastByte() {
        doTest("readShortFromDoubleCastByte");
    }

    public byte readShortFromObjectCastByte() {
        return (byte) getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastByte");
    }

    public byte readCharFromBooleanCastByte() {
        return (byte) getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastByte() {
        doTest("readCharFromBooleanCastByte");
    }

    public byte readCharFromByteCastByte() {
        return (byte) getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastByte() {
        doTest("readCharFromByteCastByte");
    }

    public byte readCharFromShortCastByte() {
        return (byte) getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastByte() {
        doTest("readCharFromShortCastByte");
    }

    public byte readCharFromCharCastByte() {
        return (byte) getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastByte() {
        doTest("readCharFromCharCastByte");
    }

    public byte readCharFromIntCastByte() {
        return (byte) getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastByte() {
        doTest("readCharFromIntCastByte");
    }

    public byte readCharFromFloatCastByte() {
        return (byte) getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastByte() {
        doTest("readCharFromFloatCastByte");
    }

    public byte readCharFromLongCastByte() {
        return (byte) getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastByte() {
        doTest("readCharFromLongCastByte");
    }

    public byte readCharFromDoubleCastByte() {
        return (byte) getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastByte() {
        doTest("readCharFromDoubleCastByte");
    }

    public byte readCharFromObjectCastByte() {
        return (byte) getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastByte");
    }

    public byte readIntFromBooleanCastByte() {
        return (byte) getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastByte() {
        doTest("readIntFromBooleanCastByte");
    }

    public byte readIntFromByteCastByte() {
        return (byte) getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastByte() {
        doTest("readIntFromByteCastByte");
    }

    public byte readIntFromShortCastByte() {
        return (byte) getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastByte() {
        doTest("readIntFromShortCastByte");
    }

    public byte readIntFromCharCastByte() {
        return (byte) getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastByte() {
        doTest("readIntFromCharCastByte");
    }

    public byte readIntFromIntCastByte() {
        return (byte) getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastByte() {
        doTest("readIntFromIntCastByte");
    }

    public byte readIntFromFloatCastByte() {
        return (byte) getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastByte() {
        doTest("readIntFromFloatCastByte");
    }

    public byte readIntFromLongCastByte() {
        return (byte) getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastByte() {
        doTest("readIntFromLongCastByte");
    }

    public byte readIntFromDoubleCastByte() {
        return (byte) getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastByte() {
        doTest("readIntFromDoubleCastByte");
    }

    public byte readIntFromObjectCastByte() {
        return (byte) getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastByte");
    }

    public byte readFloatFromBooleanCastByte() {
        return (byte) getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastByte() {
        doTest("readFloatFromBooleanCastByte");
    }

    public byte readFloatFromByteCastByte() {
        return (byte) getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastByte() {
        doTest("readFloatFromByteCastByte");
    }

    public byte readFloatFromShortCastByte() {
        return (byte) getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastByte() {
        doTest("readFloatFromShortCastByte");
    }

    public byte readFloatFromCharCastByte() {
        return (byte) getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastByte() {
        doTest("readFloatFromCharCastByte");
    }

    public byte readFloatFromIntCastByte() {
        return (byte) getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastByte() {
        doTest("readFloatFromIntCastByte");
    }

    public byte readFloatFromFloatCastByte() {
        return (byte) getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastByte() {
        doTest("readFloatFromFloatCastByte");
    }

    public byte readFloatFromLongCastByte() {
        return (byte) getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastByte() {
        doTest("readFloatFromLongCastByte");
    }

    public byte readFloatFromDoubleCastByte() {
        return (byte) getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastByte() {
        doTest("readFloatFromDoubleCastByte");
    }

    public byte readFloatFromObjectCastByte() {
        return (byte) getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastByte");
    }

    public byte readLongFromBooleanCastByte() {
        return (byte) getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastByte() {
        doTest("readLongFromBooleanCastByte");
    }

    public byte readLongFromByteCastByte() {
        return (byte) getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastByte() {
        doTest("readLongFromByteCastByte");
    }

    public byte readLongFromShortCastByte() {
        return (byte) getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastByte() {
        doTest("readLongFromShortCastByte");
    }

    public byte readLongFromCharCastByte() {
        return (byte) getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastByte() {
        doTest("readLongFromCharCastByte");
    }

    public byte readLongFromIntCastByte() {
        return (byte) getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastByte() {
        doTest("readLongFromIntCastByte");
    }

    public byte readLongFromFloatCastByte() {
        return (byte) getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastByte() {
        doTest("readLongFromFloatCastByte");
    }

    public byte readLongFromLongCastByte() {
        return (byte) getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastByte() {
        doTest("readLongFromLongCastByte");
    }

    public byte readLongFromDoubleCastByte() {
        return (byte) getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastByte() {
        doTest("readLongFromDoubleCastByte");
    }

    public byte readLongFromObjectCastByte() {
        return (byte) getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastByte");
    }

    public byte readDoubleFromBooleanCastByte() {
        return (byte) getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastByte() {
        doTest("readDoubleFromBooleanCastByte");
    }

    public byte readDoubleFromByteCastByte() {
        return (byte) getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastByte() {
        doTest("readDoubleFromByteCastByte");
    }

    public byte readDoubleFromShortCastByte() {
        return (byte) getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastByte() {
        doTest("readDoubleFromShortCastByte");
    }

    public byte readDoubleFromCharCastByte() {
        return (byte) getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastByte() {
        doTest("readDoubleFromCharCastByte");
    }

    public byte readDoubleFromIntCastByte() {
        return (byte) getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastByte() {
        doTest("readDoubleFromIntCastByte");
    }

    public byte readDoubleFromFloatCastByte() {
        return (byte) getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastByte() {
        doTest("readDoubleFromFloatCastByte");
    }

    public byte readDoubleFromLongCastByte() {
        return (byte) getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastByte() {
        doTest("readDoubleFromLongCastByte");
    }

    public byte readDoubleFromDoubleCastByte() {
        return (byte) getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastByte() {
        doTest("readDoubleFromDoubleCastByte");
    }

    public byte readDoubleFromObjectCastByte() {
        return (byte) getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastByte() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastByte");
    }

    public short readByteFromBooleanCastShort() {
        return getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastShort() {
        doTest("readByteFromBooleanCastShort");
    }

    public short readByteFromByteCastShort() {
        return getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastShort() {
        doTest("readByteFromByteCastShort");
    }

    public short readByteFromShortCastShort() {
        return getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastShort() {
        doTest("readByteFromShortCastShort");
    }

    public short readByteFromCharCastShort() {
        return getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastShort() {
        doTest("readByteFromCharCastShort");
    }

    public short readByteFromIntCastShort() {
        return getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastShort() {
        doTest("readByteFromIntCastShort");
    }

    public short readByteFromFloatCastShort() {
        return getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastShort() {
        doTest("readByteFromFloatCastShort");
    }

    public short readByteFromLongCastShort() {
        return getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastShort() {
        doTest("readByteFromLongCastShort");
    }

    public short readByteFromDoubleCastShort() {
        return getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastShort() {
        doTest("readByteFromDoubleCastShort");
    }

    public short readByteFromObjectCastShort() {
        return getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastShort");
    }

    public short readShortFromBooleanCastShort() {
        return getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastShort() {
        doTest("readShortFromBooleanCastShort");
    }

    public short readShortFromByteCastShort() {
        return getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastShort() {
        doTest("readShortFromByteCastShort");
    }

    public short readShortFromShortCastShort() {
        return getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastShort() {
        doTest("readShortFromShortCastShort");
    }

    public short readShortFromCharCastShort() {
        return getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastShort() {
        doTest("readShortFromCharCastShort");
    }

    public short readShortFromIntCastShort() {
        return getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastShort() {
        doTest("readShortFromIntCastShort");
    }

    public short readShortFromFloatCastShort() {
        return getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastShort() {
        doTest("readShortFromFloatCastShort");
    }

    public short readShortFromLongCastShort() {
        return getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastShort() {
        doTest("readShortFromLongCastShort");
    }

    public short readShortFromDoubleCastShort() {
        return getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastShort() {
        doTest("readShortFromDoubleCastShort");
    }

    public short readShortFromObjectCastShort() {
        return getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastShort");
    }

    public short readCharFromBooleanCastShort() {
        return (short) getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastShort() {
        doTest("readCharFromBooleanCastShort");
    }

    public short readCharFromByteCastShort() {
        return (short) getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastShort() {
        doTest("readCharFromByteCastShort");
    }

    public short readCharFromShortCastShort() {
        return (short) getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastShort() {
        doTest("readCharFromShortCastShort");
    }

    public short readCharFromCharCastShort() {
        return (short) getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastShort() {
        doTest("readCharFromCharCastShort");
    }

    public short readCharFromIntCastShort() {
        return (short) getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastShort() {
        doTest("readCharFromIntCastShort");
    }

    public short readCharFromFloatCastShort() {
        return (short) getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastShort() {
        doTest("readCharFromFloatCastShort");
    }

    public short readCharFromLongCastShort() {
        return (short) getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastShort() {
        doTest("readCharFromLongCastShort");
    }

    public short readCharFromDoubleCastShort() {
        return (short) getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastShort() {
        doTest("readCharFromDoubleCastShort");
    }

    public short readCharFromObjectCastShort() {
        return (short) getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastShort");
    }

    public short readIntFromBooleanCastShort() {
        return (short) getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastShort() {
        doTest("readIntFromBooleanCastShort");
    }

    public short readIntFromByteCastShort() {
        return (short) getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastShort() {
        doTest("readIntFromByteCastShort");
    }

    public short readIntFromShortCastShort() {
        return (short) getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastShort() {
        doTest("readIntFromShortCastShort");
    }

    public short readIntFromCharCastShort() {
        return (short) getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastShort() {
        doTest("readIntFromCharCastShort");
    }

    public short readIntFromIntCastShort() {
        return (short) getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastShort() {
        doTest("readIntFromIntCastShort");
    }

    public short readIntFromFloatCastShort() {
        return (short) getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastShort() {
        doTest("readIntFromFloatCastShort");
    }

    public short readIntFromLongCastShort() {
        return (short) getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastShort() {
        doTest("readIntFromLongCastShort");
    }

    public short readIntFromDoubleCastShort() {
        return (short) getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastShort() {
        doTest("readIntFromDoubleCastShort");
    }

    public short readIntFromObjectCastShort() {
        return (short) getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastShort");
    }

    public short readFloatFromBooleanCastShort() {
        return (short) getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastShort() {
        doTest("readFloatFromBooleanCastShort");
    }

    public short readFloatFromByteCastShort() {
        return (short) getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastShort() {
        doTest("readFloatFromByteCastShort");
    }

    public short readFloatFromShortCastShort() {
        return (short) getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastShort() {
        doTest("readFloatFromShortCastShort");
    }

    public short readFloatFromCharCastShort() {
        return (short) getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastShort() {
        doTest("readFloatFromCharCastShort");
    }

    public short readFloatFromIntCastShort() {
        return (short) getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastShort() {
        doTest("readFloatFromIntCastShort");
    }

    public short readFloatFromFloatCastShort() {
        return (short) getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastShort() {
        doTest("readFloatFromFloatCastShort");
    }

    public short readFloatFromLongCastShort() {
        return (short) getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastShort() {
        doTest("readFloatFromLongCastShort");
    }

    public short readFloatFromDoubleCastShort() {
        return (short) getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastShort() {
        doTest("readFloatFromDoubleCastShort");
    }

    public short readFloatFromObjectCastShort() {
        return (short) getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastShort");
    }

    public short readLongFromBooleanCastShort() {
        return (short) getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastShort() {
        doTest("readLongFromBooleanCastShort");
    }

    public short readLongFromByteCastShort() {
        return (short) getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastShort() {
        doTest("readLongFromByteCastShort");
    }

    public short readLongFromShortCastShort() {
        return (short) getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastShort() {
        doTest("readLongFromShortCastShort");
    }

    public short readLongFromCharCastShort() {
        return (short) getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastShort() {
        doTest("readLongFromCharCastShort");
    }

    public short readLongFromIntCastShort() {
        return (short) getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastShort() {
        doTest("readLongFromIntCastShort");
    }

    public short readLongFromFloatCastShort() {
        return (short) getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastShort() {
        doTest("readLongFromFloatCastShort");
    }

    public short readLongFromLongCastShort() {
        return (short) getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastShort() {
        doTest("readLongFromLongCastShort");
    }

    public short readLongFromDoubleCastShort() {
        return (short) getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastShort() {
        doTest("readLongFromDoubleCastShort");
    }

    public short readLongFromObjectCastShort() {
        return (short) getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastShort");
    }

    public short readDoubleFromBooleanCastShort() {
        return (short) getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastShort() {
        doTest("readDoubleFromBooleanCastShort");
    }

    public short readDoubleFromByteCastShort() {
        return (short) getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastShort() {
        doTest("readDoubleFromByteCastShort");
    }

    public short readDoubleFromShortCastShort() {
        return (short) getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastShort() {
        doTest("readDoubleFromShortCastShort");
    }

    public short readDoubleFromCharCastShort() {
        return (short) getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastShort() {
        doTest("readDoubleFromCharCastShort");
    }

    public short readDoubleFromIntCastShort() {
        return (short) getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastShort() {
        doTest("readDoubleFromIntCastShort");
    }

    public short readDoubleFromFloatCastShort() {
        return (short) getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastShort() {
        doTest("readDoubleFromFloatCastShort");
    }

    public short readDoubleFromLongCastShort() {
        return (short) getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastShort() {
        doTest("readDoubleFromLongCastShort");
    }

    public short readDoubleFromDoubleCastShort() {
        return (short) getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastShort() {
        doTest("readDoubleFromDoubleCastShort");
    }

    public short readDoubleFromObjectCastShort() {
        return (short) getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastShort() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastShort");
    }

    public char readByteFromBooleanCastChar() {
        return (char) getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastChar() {
        doTest("readByteFromBooleanCastChar");
    }

    public char readByteFromByteCastChar() {
        return (char) getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastChar() {
        doTest("readByteFromByteCastChar");
    }

    public char readByteFromShortCastChar() {
        return (char) getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastChar() {
        doTest("readByteFromShortCastChar");
    }

    public char readByteFromCharCastChar() {
        return (char) getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastChar() {
        doTest("readByteFromCharCastChar");
    }

    public char readByteFromIntCastChar() {
        return (char) getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastChar() {
        doTest("readByteFromIntCastChar");
    }

    public char readByteFromFloatCastChar() {
        return (char) getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastChar() {
        doTest("readByteFromFloatCastChar");
    }

    public char readByteFromLongCastChar() {
        return (char) getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastChar() {
        doTest("readByteFromLongCastChar");
    }

    public char readByteFromDoubleCastChar() {
        return (char) getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastChar() {
        doTest("readByteFromDoubleCastChar");
    }

    public char readByteFromObjectCastChar() {
        return (char) getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastChar");
    }

    public char readShortFromBooleanCastChar() {
        return (char) getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastChar() {
        doTest("readShortFromBooleanCastChar");
    }

    public char readShortFromByteCastChar() {
        return (char) getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastChar() {
        doTest("readShortFromByteCastChar");
    }

    public char readShortFromShortCastChar() {
        return (char) getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastChar() {
        doTest("readShortFromShortCastChar");
    }

    public char readShortFromCharCastChar() {
        return (char) getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastChar() {
        doTest("readShortFromCharCastChar");
    }

    public char readShortFromIntCastChar() {
        return (char) getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastChar() {
        doTest("readShortFromIntCastChar");
    }

    public char readShortFromFloatCastChar() {
        return (char) getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastChar() {
        doTest("readShortFromFloatCastChar");
    }

    public char readShortFromLongCastChar() {
        return (char) getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastChar() {
        doTest("readShortFromLongCastChar");
    }

    public char readShortFromDoubleCastChar() {
        return (char) getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastChar() {
        doTest("readShortFromDoubleCastChar");
    }

    public char readShortFromObjectCastChar() {
        return (char) getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastChar");
    }

    public char readCharFromBooleanCastChar() {
        return getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastChar() {
        doTest("readCharFromBooleanCastChar");
    }

    public char readCharFromByteCastChar() {
        return getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastChar() {
        doTest("readCharFromByteCastChar");
    }

    public char readCharFromShortCastChar() {
        return getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastChar() {
        doTest("readCharFromShortCastChar");
    }

    public char readCharFromCharCastChar() {
        return getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastChar() {
        doTest("readCharFromCharCastChar");
    }

    public char readCharFromIntCastChar() {
        return getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastChar() {
        doTest("readCharFromIntCastChar");
    }

    public char readCharFromFloatCastChar() {
        return getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastChar() {
        doTest("readCharFromFloatCastChar");
    }

    public char readCharFromLongCastChar() {
        return getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastChar() {
        doTest("readCharFromLongCastChar");
    }

    public char readCharFromDoubleCastChar() {
        return getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastChar() {
        doTest("readCharFromDoubleCastChar");
    }

    public char readCharFromObjectCastChar() {
        return getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastChar");
    }

    public char readIntFromBooleanCastChar() {
        return (char) getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastChar() {
        doTest("readIntFromBooleanCastChar");
    }

    public char readIntFromByteCastChar() {
        return (char) getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastChar() {
        doTest("readIntFromByteCastChar");
    }

    public char readIntFromShortCastChar() {
        return (char) getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastChar() {
        doTest("readIntFromShortCastChar");
    }

    public char readIntFromCharCastChar() {
        return (char) getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastChar() {
        doTest("readIntFromCharCastChar");
    }

    public char readIntFromIntCastChar() {
        return (char) getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastChar() {
        doTest("readIntFromIntCastChar");
    }

    public char readIntFromFloatCastChar() {
        return (char) getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastChar() {
        doTest("readIntFromFloatCastChar");
    }

    public char readIntFromLongCastChar() {
        return (char) getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastChar() {
        doTest("readIntFromLongCastChar");
    }

    public char readIntFromDoubleCastChar() {
        return (char) getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastChar() {
        doTest("readIntFromDoubleCastChar");
    }

    public char readIntFromObjectCastChar() {
        return (char) getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastChar");
    }

    public char readFloatFromBooleanCastChar() {
        return (char) getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastChar() {
        doTest("readFloatFromBooleanCastChar");
    }

    public char readFloatFromByteCastChar() {
        return (char) getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastChar() {
        doTest("readFloatFromByteCastChar");
    }

    public char readFloatFromShortCastChar() {
        return (char) getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastChar() {
        doTest("readFloatFromShortCastChar");
    }

    public char readFloatFromCharCastChar() {
        return (char) getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastChar() {
        doTest("readFloatFromCharCastChar");
    }

    public char readFloatFromIntCastChar() {
        return (char) getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastChar() {
        doTest("readFloatFromIntCastChar");
    }

    public char readFloatFromFloatCastChar() {
        return (char) getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastChar() {
        doTest("readFloatFromFloatCastChar");
    }

    public char readFloatFromLongCastChar() {
        return (char) getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastChar() {
        doTest("readFloatFromLongCastChar");
    }

    public char readFloatFromDoubleCastChar() {
        return (char) getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastChar() {
        doTest("readFloatFromDoubleCastChar");
    }

    public char readFloatFromObjectCastChar() {
        return (char) getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastChar");
    }

    public char readLongFromBooleanCastChar() {
        return (char) getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastChar() {
        doTest("readLongFromBooleanCastChar");
    }

    public char readLongFromByteCastChar() {
        return (char) getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastChar() {
        doTest("readLongFromByteCastChar");
    }

    public char readLongFromShortCastChar() {
        return (char) getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastChar() {
        doTest("readLongFromShortCastChar");
    }

    public char readLongFromCharCastChar() {
        return (char) getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastChar() {
        doTest("readLongFromCharCastChar");
    }

    public char readLongFromIntCastChar() {
        return (char) getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastChar() {
        doTest("readLongFromIntCastChar");
    }

    public char readLongFromFloatCastChar() {
        return (char) getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastChar() {
        doTest("readLongFromFloatCastChar");
    }

    public char readLongFromLongCastChar() {
        return (char) getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastChar() {
        doTest("readLongFromLongCastChar");
    }

    public char readLongFromDoubleCastChar() {
        return (char) getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastChar() {
        doTest("readLongFromDoubleCastChar");
    }

    public char readLongFromObjectCastChar() {
        return (char) getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastChar");
    }

    public char readDoubleFromBooleanCastChar() {
        return (char) getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastChar() {
        doTest("readDoubleFromBooleanCastChar");
    }

    public char readDoubleFromByteCastChar() {
        return (char) getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastChar() {
        doTest("readDoubleFromByteCastChar");
    }

    public char readDoubleFromShortCastChar() {
        return (char) getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastChar() {
        doTest("readDoubleFromShortCastChar");
    }

    public char readDoubleFromCharCastChar() {
        return (char) getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastChar() {
        doTest("readDoubleFromCharCastChar");
    }

    public char readDoubleFromIntCastChar() {
        return (char) getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastChar() {
        doTest("readDoubleFromIntCastChar");
    }

    public char readDoubleFromFloatCastChar() {
        return (char) getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastChar() {
        doTest("readDoubleFromFloatCastChar");
    }

    public char readDoubleFromLongCastChar() {
        return (char) getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastChar() {
        doTest("readDoubleFromLongCastChar");
    }

    public char readDoubleFromDoubleCastChar() {
        return (char) getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastChar() {
        doTest("readDoubleFromDoubleCastChar");
    }

    public char readDoubleFromObjectCastChar() {
        return (char) getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastChar() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastChar");
    }

    public int readByteFromBooleanCastInt() {
        return getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastInt() {
        doTest("readByteFromBooleanCastInt");
    }

    public int readByteFromByteCastInt() {
        return getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastInt() {
        doTest("readByteFromByteCastInt");
    }

    public int readByteFromShortCastInt() {
        return getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastInt() {
        doTest("readByteFromShortCastInt");
    }

    public int readByteFromCharCastInt() {
        return getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastInt() {
        doTest("readByteFromCharCastInt");
    }

    public int readByteFromIntCastInt() {
        return getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastInt() {
        doTest("readByteFromIntCastInt");
    }

    public int readByteFromFloatCastInt() {
        return getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastInt() {
        doTest("readByteFromFloatCastInt");
    }

    public int readByteFromLongCastInt() {
        return getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastInt() {
        doTest("readByteFromLongCastInt");
    }

    public int readByteFromDoubleCastInt() {
        return getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastInt() {
        doTest("readByteFromDoubleCastInt");
    }

    public int readByteFromObjectCastInt() {
        return getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastInt");
    }

    public int readShortFromBooleanCastInt() {
        return getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastInt() {
        doTest("readShortFromBooleanCastInt");
    }

    public int readShortFromByteCastInt() {
        return getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastInt() {
        doTest("readShortFromByteCastInt");
    }

    public int readShortFromShortCastInt() {
        return getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastInt() {
        doTest("readShortFromShortCastInt");
    }

    public int readShortFromCharCastInt() {
        return getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastInt() {
        doTest("readShortFromCharCastInt");
    }

    public int readShortFromIntCastInt() {
        return getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastInt() {
        doTest("readShortFromIntCastInt");
    }

    public int readShortFromFloatCastInt() {
        return getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastInt() {
        doTest("readShortFromFloatCastInt");
    }

    public int readShortFromLongCastInt() {
        return getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastInt() {
        doTest("readShortFromLongCastInt");
    }

    public int readShortFromDoubleCastInt() {
        return getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastInt() {
        doTest("readShortFromDoubleCastInt");
    }

    public int readShortFromObjectCastInt() {
        return getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastInt");
    }

    public int readCharFromBooleanCastInt() {
        return getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastInt() {
        doTest("readCharFromBooleanCastInt");
    }

    public int readCharFromByteCastInt() {
        return getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastInt() {
        doTest("readCharFromByteCastInt");
    }

    public int readCharFromShortCastInt() {
        return getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastInt() {
        doTest("readCharFromShortCastInt");
    }

    public int readCharFromCharCastInt() {
        return getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastInt() {
        doTest("readCharFromCharCastInt");
    }

    public int readCharFromIntCastInt() {
        return getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastInt() {
        doTest("readCharFromIntCastInt");
    }

    public int readCharFromFloatCastInt() {
        return getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastInt() {
        doTest("readCharFromFloatCastInt");
    }

    public int readCharFromLongCastInt() {
        return getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastInt() {
        doTest("readCharFromLongCastInt");
    }

    public int readCharFromDoubleCastInt() {
        return getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastInt() {
        doTest("readCharFromDoubleCastInt");
    }

    public int readCharFromObjectCastInt() {
        return getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastInt");
    }

    public int readIntFromBooleanCastInt() {
        return getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastInt() {
        doTest("readIntFromBooleanCastInt");
    }

    public int readIntFromByteCastInt() {
        return getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastInt() {
        doTest("readIntFromByteCastInt");
    }

    public int readIntFromShortCastInt() {
        return getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastInt() {
        doTest("readIntFromShortCastInt");
    }

    public int readIntFromCharCastInt() {
        return getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastInt() {
        doTest("readIntFromCharCastInt");
    }

    public int readIntFromIntCastInt() {
        return getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastInt() {
        doTest("readIntFromIntCastInt");
    }

    public int readIntFromFloatCastInt() {
        return getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastInt() {
        doTest("readIntFromFloatCastInt");
    }

    public int readIntFromLongCastInt() {
        return getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastInt() {
        doTest("readIntFromLongCastInt");
    }

    public int readIntFromDoubleCastInt() {
        return getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastInt() {
        doTest("readIntFromDoubleCastInt");
    }

    public int readIntFromObjectCastInt() {
        return getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastInt");
    }

    public int readFloatFromBooleanCastInt() {
        return (int) getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastInt() {
        doTest("readFloatFromBooleanCastInt");
    }

    public int readFloatFromByteCastInt() {
        return (int) getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastInt() {
        doTest("readFloatFromByteCastInt");
    }

    public int readFloatFromShortCastInt() {
        return (int) getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastInt() {
        doTest("readFloatFromShortCastInt");
    }

    public int readFloatFromCharCastInt() {
        return (int) getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastInt() {
        doTest("readFloatFromCharCastInt");
    }

    public int readFloatFromIntCastInt() {
        return (int) getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastInt() {
        doTest("readFloatFromIntCastInt");
    }

    public int readFloatFromFloatCastInt() {
        return (int) getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastInt() {
        doTest("readFloatFromFloatCastInt");
    }

    public int readFloatFromLongCastInt() {
        return (int) getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastInt() {
        doTest("readFloatFromLongCastInt");
    }

    public int readFloatFromDoubleCastInt() {
        return (int) getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastInt() {
        doTest("readFloatFromDoubleCastInt");
    }

    public int readFloatFromObjectCastInt() {
        return (int) getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastInt");
    }

    public int readLongFromBooleanCastInt() {
        return (int) getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastInt() {
        doTest("readLongFromBooleanCastInt");
    }

    public int readLongFromByteCastInt() {
        return (int) getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastInt() {
        doTest("readLongFromByteCastInt");
    }

    public int readLongFromShortCastInt() {
        return (int) getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastInt() {
        doTest("readLongFromShortCastInt");
    }

    public int readLongFromCharCastInt() {
        return (int) getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastInt() {
        doTest("readLongFromCharCastInt");
    }

    public int readLongFromIntCastInt() {
        return (int) getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastInt() {
        doTest("readLongFromIntCastInt");
    }

    public int readLongFromFloatCastInt() {
        return (int) getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastInt() {
        doTest("readLongFromFloatCastInt");
    }

    public int readLongFromLongCastInt() {
        return (int) getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastInt() {
        doTest("readLongFromLongCastInt");
    }

    public int readLongFromDoubleCastInt() {
        return (int) getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastInt() {
        doTest("readLongFromDoubleCastInt");
    }

    public int readLongFromObjectCastInt() {
        return (int) getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastInt");
    }

    public int readDoubleFromBooleanCastInt() {
        return (int) getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastInt() {
        doTest("readDoubleFromBooleanCastInt");
    }

    public int readDoubleFromByteCastInt() {
        return (int) getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastInt() {
        doTest("readDoubleFromByteCastInt");
    }

    public int readDoubleFromShortCastInt() {
        return (int) getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastInt() {
        doTest("readDoubleFromShortCastInt");
    }

    public int readDoubleFromCharCastInt() {
        return (int) getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastInt() {
        doTest("readDoubleFromCharCastInt");
    }

    public int readDoubleFromIntCastInt() {
        return (int) getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastInt() {
        doTest("readDoubleFromIntCastInt");
    }

    public int readDoubleFromFloatCastInt() {
        return (int) getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastInt() {
        doTest("readDoubleFromFloatCastInt");
    }

    public int readDoubleFromLongCastInt() {
        return (int) getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastInt() {
        doTest("readDoubleFromLongCastInt");
    }

    public int readDoubleFromDoubleCastInt() {
        return (int) getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastInt() {
        doTest("readDoubleFromDoubleCastInt");
    }

    public int readDoubleFromObjectCastInt() {
        return (int) getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastInt() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastInt");
    }

    public float readByteFromBooleanCastFloat() {
        return getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastFloat() {
        doTest("readByteFromBooleanCastFloat");
    }

    public float readByteFromByteCastFloat() {
        return getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastFloat() {
        doTest("readByteFromByteCastFloat");
    }

    public float readByteFromShortCastFloat() {
        return getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastFloat() {
        doTest("readByteFromShortCastFloat");
    }

    public float readByteFromCharCastFloat() {
        return getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastFloat() {
        doTest("readByteFromCharCastFloat");
    }

    public float readByteFromIntCastFloat() {
        return getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastFloat() {
        doTest("readByteFromIntCastFloat");
    }

    public float readByteFromFloatCastFloat() {
        return getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastFloat() {
        doTest("readByteFromFloatCastFloat");
    }

    public float readByteFromLongCastFloat() {
        return getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastFloat() {
        doTest("readByteFromLongCastFloat");
    }

    public float readByteFromDoubleCastFloat() {
        return getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastFloat() {
        doTest("readByteFromDoubleCastFloat");
    }

    public float readByteFromObjectCastFloat() {
        return getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastFloat");
    }

    public float readShortFromBooleanCastFloat() {
        return getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastFloat() {
        doTest("readShortFromBooleanCastFloat");
    }

    public float readShortFromByteCastFloat() {
        return getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastFloat() {
        doTest("readShortFromByteCastFloat");
    }

    public float readShortFromShortCastFloat() {
        return getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastFloat() {
        doTest("readShortFromShortCastFloat");
    }

    public float readShortFromCharCastFloat() {
        return getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastFloat() {
        doTest("readShortFromCharCastFloat");
    }

    public float readShortFromIntCastFloat() {
        return getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastFloat() {
        doTest("readShortFromIntCastFloat");
    }

    public float readShortFromFloatCastFloat() {
        return getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastFloat() {
        doTest("readShortFromFloatCastFloat");
    }

    public float readShortFromLongCastFloat() {
        return getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastFloat() {
        doTest("readShortFromLongCastFloat");
    }

    public float readShortFromDoubleCastFloat() {
        return getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastFloat() {
        doTest("readShortFromDoubleCastFloat");
    }

    public float readShortFromObjectCastFloat() {
        return getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastFloat");
    }

    public float readCharFromBooleanCastFloat() {
        return getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastFloat() {
        doTest("readCharFromBooleanCastFloat");
    }

    public float readCharFromByteCastFloat() {
        return getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastFloat() {
        doTest("readCharFromByteCastFloat");
    }

    public float readCharFromShortCastFloat() {
        return getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastFloat() {
        doTest("readCharFromShortCastFloat");
    }

    public float readCharFromCharCastFloat() {
        return getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastFloat() {
        doTest("readCharFromCharCastFloat");
    }

    public float readCharFromIntCastFloat() {
        return getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastFloat() {
        doTest("readCharFromIntCastFloat");
    }

    public float readCharFromFloatCastFloat() {
        return getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastFloat() {
        doTest("readCharFromFloatCastFloat");
    }

    public float readCharFromLongCastFloat() {
        return getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastFloat() {
        doTest("readCharFromLongCastFloat");
    }

    public float readCharFromDoubleCastFloat() {
        return getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastFloat() {
        doTest("readCharFromDoubleCastFloat");
    }

    public float readCharFromObjectCastFloat() {
        return getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastFloat");
    }

    public float readIntFromBooleanCastFloat() {
        return getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastFloat() {
        doTest("readIntFromBooleanCastFloat");
    }

    public float readIntFromByteCastFloat() {
        return getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastFloat() {
        doTest("readIntFromByteCastFloat");
    }

    public float readIntFromShortCastFloat() {
        return getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastFloat() {
        doTest("readIntFromShortCastFloat");
    }

    public float readIntFromCharCastFloat() {
        return getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastFloat() {
        doTest("readIntFromCharCastFloat");
    }

    public float readIntFromIntCastFloat() {
        return getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastFloat() {
        doTest("readIntFromIntCastFloat");
    }

    public float readIntFromFloatCastFloat() {
        return getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastFloat() {
        doTest("readIntFromFloatCastFloat");
    }

    public float readIntFromLongCastFloat() {
        return getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastFloat() {
        doTest("readIntFromLongCastFloat");
    }

    public float readIntFromDoubleCastFloat() {
        return getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastFloat() {
        doTest("readIntFromDoubleCastFloat");
    }

    public float readIntFromObjectCastFloat() {
        return getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastFloat");
    }

    public float readFloatFromBooleanCastFloat() {
        return getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastFloat() {
        doTest("readFloatFromBooleanCastFloat");
    }

    public float readFloatFromByteCastFloat() {
        return getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastFloat() {
        doTest("readFloatFromByteCastFloat");
    }

    public float readFloatFromShortCastFloat() {
        return getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastFloat() {
        doTest("readFloatFromShortCastFloat");
    }

    public float readFloatFromCharCastFloat() {
        return getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastFloat() {
        doTest("readFloatFromCharCastFloat");
    }

    public float readFloatFromIntCastFloat() {
        return getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastFloat() {
        doTest("readFloatFromIntCastFloat");
    }

    public float readFloatFromFloatCastFloat() {
        return getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastFloat() {
        doTest("readFloatFromFloatCastFloat");
    }

    public float readFloatFromLongCastFloat() {
        return getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastFloat() {
        doTest("readFloatFromLongCastFloat");
    }

    public float readFloatFromDoubleCastFloat() {
        return getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastFloat() {
        doTest("readFloatFromDoubleCastFloat");
    }

    public float readFloatFromObjectCastFloat() {
        return getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastFloat");
    }

    public float readLongFromBooleanCastFloat() {
        return getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastFloat() {
        doTest("readLongFromBooleanCastFloat");
    }

    public float readLongFromByteCastFloat() {
        return getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastFloat() {
        doTest("readLongFromByteCastFloat");
    }

    public float readLongFromShortCastFloat() {
        return getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastFloat() {
        doTest("readLongFromShortCastFloat");
    }

    public float readLongFromCharCastFloat() {
        return getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastFloat() {
        doTest("readLongFromCharCastFloat");
    }

    public float readLongFromIntCastFloat() {
        return getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastFloat() {
        doTest("readLongFromIntCastFloat");
    }

    public float readLongFromFloatCastFloat() {
        return getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastFloat() {
        doTest("readLongFromFloatCastFloat");
    }

    public float readLongFromLongCastFloat() {
        return getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastFloat() {
        doTest("readLongFromLongCastFloat");
    }

    public float readLongFromDoubleCastFloat() {
        return getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastFloat() {
        doTest("readLongFromDoubleCastFloat");
    }

    public float readLongFromObjectCastFloat() {
        return getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastFloat");
    }

    public float readDoubleFromBooleanCastFloat() {
        return (float) getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastFloat() {
        doTest("readDoubleFromBooleanCastFloat");
    }

    public float readDoubleFromByteCastFloat() {
        return (float) getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastFloat() {
        doTest("readDoubleFromByteCastFloat");
    }

    public float readDoubleFromShortCastFloat() {
        return (float) getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastFloat() {
        doTest("readDoubleFromShortCastFloat");
    }

    public float readDoubleFromCharCastFloat() {
        return (float) getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastFloat() {
        doTest("readDoubleFromCharCastFloat");
    }

    public float readDoubleFromIntCastFloat() {
        return (float) getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastFloat() {
        doTest("readDoubleFromIntCastFloat");
    }

    public float readDoubleFromFloatCastFloat() {
        return (float) getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastFloat() {
        doTest("readDoubleFromFloatCastFloat");
    }

    public float readDoubleFromLongCastFloat() {
        return (float) getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastFloat() {
        doTest("readDoubleFromLongCastFloat");
    }

    public float readDoubleFromDoubleCastFloat() {
        return (float) getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastFloat() {
        doTest("readDoubleFromDoubleCastFloat");
    }

    public float readDoubleFromObjectCastFloat() {
        return (float) getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastFloat() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastFloat");
    }

    public long readByteFromBooleanCastLong() {
        return getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastLong() {
        doTest("readByteFromBooleanCastLong");
    }

    public long readByteFromByteCastLong() {
        return getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastLong() {
        doTest("readByteFromByteCastLong");
    }

    public long readByteFromShortCastLong() {
        return getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastLong() {
        doTest("readByteFromShortCastLong");
    }

    public long readByteFromCharCastLong() {
        return getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastLong() {
        doTest("readByteFromCharCastLong");
    }

    public long readByteFromIntCastLong() {
        return getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastLong() {
        doTest("readByteFromIntCastLong");
    }

    public long readByteFromFloatCastLong() {
        return getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastLong() {
        doTest("readByteFromFloatCastLong");
    }

    public long readByteFromLongCastLong() {
        return getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastLong() {
        doTest("readByteFromLongCastLong");
    }

    public long readByteFromDoubleCastLong() {
        return getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastLong() {
        doTest("readByteFromDoubleCastLong");
    }

    public long readByteFromObjectCastLong() {
        return getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastLong");
    }

    public long readShortFromBooleanCastLong() {
        return getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastLong() {
        doTest("readShortFromBooleanCastLong");
    }

    public long readShortFromByteCastLong() {
        return getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastLong() {
        doTest("readShortFromByteCastLong");
    }

    public long readShortFromShortCastLong() {
        return getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastLong() {
        doTest("readShortFromShortCastLong");
    }

    public long readShortFromCharCastLong() {
        return getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastLong() {
        doTest("readShortFromCharCastLong");
    }

    public long readShortFromIntCastLong() {
        return getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastLong() {
        doTest("readShortFromIntCastLong");
    }

    public long readShortFromFloatCastLong() {
        return getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastLong() {
        doTest("readShortFromFloatCastLong");
    }

    public long readShortFromLongCastLong() {
        return getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastLong() {
        doTest("readShortFromLongCastLong");
    }

    public long readShortFromDoubleCastLong() {
        return getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastLong() {
        doTest("readShortFromDoubleCastLong");
    }

    public long readShortFromObjectCastLong() {
        return getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastLong");
    }

    public long readCharFromBooleanCastLong() {
        return getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastLong() {
        doTest("readCharFromBooleanCastLong");
    }

    public long readCharFromByteCastLong() {
        return getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastLong() {
        doTest("readCharFromByteCastLong");
    }

    public long readCharFromShortCastLong() {
        return getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastLong() {
        doTest("readCharFromShortCastLong");
    }

    public long readCharFromCharCastLong() {
        return getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastLong() {
        doTest("readCharFromCharCastLong");
    }

    public long readCharFromIntCastLong() {
        return getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastLong() {
        doTest("readCharFromIntCastLong");
    }

    public long readCharFromFloatCastLong() {
        return getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastLong() {
        doTest("readCharFromFloatCastLong");
    }

    public long readCharFromLongCastLong() {
        return getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastLong() {
        doTest("readCharFromLongCastLong");
    }

    public long readCharFromDoubleCastLong() {
        return getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastLong() {
        doTest("readCharFromDoubleCastLong");
    }

    public long readCharFromObjectCastLong() {
        return getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastLong");
    }

    public long readIntFromBooleanCastLong() {
        return getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastLong() {
        doTest("readIntFromBooleanCastLong");
    }

    public long readIntFromByteCastLong() {
        return getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastLong() {
        doTest("readIntFromByteCastLong");
    }

    public long readIntFromShortCastLong() {
        return getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastLong() {
        doTest("readIntFromShortCastLong");
    }

    public long readIntFromCharCastLong() {
        return getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastLong() {
        doTest("readIntFromCharCastLong");
    }

    public long readIntFromIntCastLong() {
        return getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastLong() {
        doTest("readIntFromIntCastLong");
    }

    public long readIntFromFloatCastLong() {
        return getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastLong() {
        doTest("readIntFromFloatCastLong");
    }

    public long readIntFromLongCastLong() {
        return getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastLong() {
        doTest("readIntFromLongCastLong");
    }

    public long readIntFromDoubleCastLong() {
        return getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastLong() {
        doTest("readIntFromDoubleCastLong");
    }

    public long readIntFromObjectCastLong() {
        return getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastLong");
    }

    public long readFloatFromBooleanCastLong() {
        return (long) getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastLong() {
        doTest("readFloatFromBooleanCastLong");
    }

    public long readFloatFromByteCastLong() {
        return (long) getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastLong() {
        doTest("readFloatFromByteCastLong");
    }

    public long readFloatFromShortCastLong() {
        return (long) getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastLong() {
        doTest("readFloatFromShortCastLong");
    }

    public long readFloatFromCharCastLong() {
        return (long) getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastLong() {
        doTest("readFloatFromCharCastLong");
    }

    public long readFloatFromIntCastLong() {
        return (long) getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastLong() {
        doTest("readFloatFromIntCastLong");
    }

    public long readFloatFromFloatCastLong() {
        return (long) getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastLong() {
        doTest("readFloatFromFloatCastLong");
    }

    public long readFloatFromLongCastLong() {
        return (long) getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastLong() {
        doTest("readFloatFromLongCastLong");
    }

    public long readFloatFromDoubleCastLong() {
        return (long) getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastLong() {
        doTest("readFloatFromDoubleCastLong");
    }

    public long readFloatFromObjectCastLong() {
        return (long) getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastLong");
    }

    public long readLongFromBooleanCastLong() {
        return getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastLong() {
        doTest("readLongFromBooleanCastLong");
    }

    public long readLongFromByteCastLong() {
        return getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastLong() {
        doTest("readLongFromByteCastLong");
    }

    public long readLongFromShortCastLong() {
        return getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastLong() {
        doTest("readLongFromShortCastLong");
    }

    public long readLongFromCharCastLong() {
        return getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastLong() {
        doTest("readLongFromCharCastLong");
    }

    public long readLongFromIntCastLong() {
        return getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastLong() {
        doTest("readLongFromIntCastLong");
    }

    public long readLongFromFloatCastLong() {
        return getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastLong() {
        doTest("readLongFromFloatCastLong");
    }

    public long readLongFromLongCastLong() {
        return getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastLong() {
        doTest("readLongFromLongCastLong");
    }

    public long readLongFromDoubleCastLong() {
        return getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastLong() {
        doTest("readLongFromDoubleCastLong");
    }

    public long readLongFromObjectCastLong() {
        return getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastLong");
    }

    public long readDoubleFromBooleanCastLong() {
        return (long) getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastLong() {
        doTest("readDoubleFromBooleanCastLong");
    }

    public long readDoubleFromByteCastLong() {
        return (long) getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastLong() {
        doTest("readDoubleFromByteCastLong");
    }

    public long readDoubleFromShortCastLong() {
        return (long) getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastLong() {
        doTest("readDoubleFromShortCastLong");
    }

    public long readDoubleFromCharCastLong() {
        return (long) getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastLong() {
        doTest("readDoubleFromCharCastLong");
    }

    public long readDoubleFromIntCastLong() {
        return (long) getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastLong() {
        doTest("readDoubleFromIntCastLong");
    }

    public long readDoubleFromFloatCastLong() {
        return (long) getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastLong() {
        doTest("readDoubleFromFloatCastLong");
    }

    public long readDoubleFromLongCastLong() {
        return (long) getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastLong() {
        doTest("readDoubleFromLongCastLong");
    }

    public long readDoubleFromDoubleCastLong() {
        return (long) getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastLong() {
        doTest("readDoubleFromDoubleCastLong");
    }

    public long readDoubleFromObjectCastLong() {
        return (long) getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastLong() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastLong");
    }

    public double readByteFromBooleanCastDouble() {
        return getByte(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testByteFromBooleanCastDouble() {
        doTest("readByteFromBooleanCastDouble");
    }

    public double readByteFromByteCastDouble() {
        return getByte(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testByteFromByteCastDouble() {
        doTest("readByteFromByteCastDouble");
    }

    public double readByteFromShortCastDouble() {
        return getByte(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testByteFromShortCastDouble() {
        doTest("readByteFromShortCastDouble");
    }

    public double readByteFromCharCastDouble() {
        return getByte(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testByteFromCharCastDouble() {
        doTest("readByteFromCharCastDouble");
    }

    public double readByteFromIntCastDouble() {
        return getByte(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testByteFromIntCastDouble() {
        doTest("readByteFromIntCastDouble");
    }

    public double readByteFromFloatCastDouble() {
        return getByte(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testByteFromFloatCastDouble() {
        doTest("readByteFromFloatCastDouble");
    }

    public double readByteFromLongCastDouble() {
        return getByte(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testByteFromLongCastDouble() {
        doTest("readByteFromLongCastDouble");
    }

    public double readByteFromDoubleCastDouble() {
        return getByte(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testByteFromDoubleCastDouble() {
        doTest("readByteFromDoubleCastDouble");
    }

    public double readByteFromObjectCastDouble() {
        return getByte(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testByteFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readByteFromObjectCastDouble");
    }

    public double readShortFromBooleanCastDouble() {
        return getShort(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testShortFromBooleanCastDouble() {
        doTest("readShortFromBooleanCastDouble");
    }

    public double readShortFromByteCastDouble() {
        return getShort(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testShortFromByteCastDouble() {
        doTest("readShortFromByteCastDouble");
    }

    public double readShortFromShortCastDouble() {
        return getShort(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testShortFromShortCastDouble() {
        doTest("readShortFromShortCastDouble");
    }

    public double readShortFromCharCastDouble() {
        return getShort(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testShortFromCharCastDouble() {
        doTest("readShortFromCharCastDouble");
    }

    public double readShortFromIntCastDouble() {
        return getShort(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testShortFromIntCastDouble() {
        doTest("readShortFromIntCastDouble");
    }

    public double readShortFromFloatCastDouble() {
        return getShort(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testShortFromFloatCastDouble() {
        doTest("readShortFromFloatCastDouble");
    }

    public double readShortFromLongCastDouble() {
        return getShort(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testShortFromLongCastDouble() {
        doTest("readShortFromLongCastDouble");
    }

    public double readShortFromDoubleCastDouble() {
        return getShort(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testShortFromDoubleCastDouble() {
        doTest("readShortFromDoubleCastDouble");
    }

    public double readShortFromObjectCastDouble() {
        return getShort(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testShortFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readShortFromObjectCastDouble");
    }

    public double readCharFromBooleanCastDouble() {
        return getChar(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testCharFromBooleanCastDouble() {
        doTest("readCharFromBooleanCastDouble");
    }

    public double readCharFromByteCastDouble() {
        return getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testCharFromByteCastDouble() {
        doTest("readCharFromByteCastDouble");
    }

    public double readCharFromShortCastDouble() {
        return getChar(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testCharFromShortCastDouble() {
        doTest("readCharFromShortCastDouble");
    }

    public double readCharFromCharCastDouble() {
        return getChar(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testCharFromCharCastDouble() {
        doTest("readCharFromCharCastDouble");
    }

    public double readCharFromIntCastDouble() {
        return getChar(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testCharFromIntCastDouble() {
        doTest("readCharFromIntCastDouble");
    }

    public double readCharFromFloatCastDouble() {
        return getChar(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testCharFromFloatCastDouble() {
        doTest("readCharFromFloatCastDouble");
    }

    public double readCharFromLongCastDouble() {
        return getChar(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testCharFromLongCastDouble() {
        doTest("readCharFromLongCastDouble");
    }

    public double readCharFromDoubleCastDouble() {
        return getChar(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testCharFromDoubleCastDouble() {
        doTest("readCharFromDoubleCastDouble");
    }

    public double readCharFromObjectCastDouble() {
        return getChar(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testCharFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readCharFromObjectCastDouble");
    }

    public double readIntFromBooleanCastDouble() {
        return getInt(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testIntFromBooleanCastDouble() {
        doTest("readIntFromBooleanCastDouble");
    }

    public double readIntFromByteCastDouble() {
        return getInt(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testIntFromByteCastDouble() {
        doTest("readIntFromByteCastDouble");
    }

    public double readIntFromShortCastDouble() {
        return getInt(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testIntFromShortCastDouble() {
        doTest("readIntFromShortCastDouble");
    }

    public double readIntFromCharCastDouble() {
        return getInt(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testIntFromCharCastDouble() {
        doTest("readIntFromCharCastDouble");
    }

    public double readIntFromIntCastDouble() {
        return getInt(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testIntFromIntCastDouble() {
        doTest("readIntFromIntCastDouble");
    }

    public double readIntFromFloatCastDouble() {
        return getInt(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testIntFromFloatCastDouble() {
        doTest("readIntFromFloatCastDouble");
    }

    public double readIntFromLongCastDouble() {
        return getInt(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testIntFromLongCastDouble() {
        doTest("readIntFromLongCastDouble");
    }

    public double readIntFromDoubleCastDouble() {
        return getInt(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testIntFromDoubleCastDouble() {
        doTest("readIntFromDoubleCastDouble");
    }

    public double readIntFromObjectCastDouble() {
        return getInt(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testIntFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readIntFromObjectCastDouble");
    }

    public double readFloatFromBooleanCastDouble() {
        return getFloat(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testFloatFromBooleanCastDouble() {
        doTest("readFloatFromBooleanCastDouble");
    }

    public double readFloatFromByteCastDouble() {
        return getFloat(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromByteCastDouble() {
        doTest("readFloatFromByteCastDouble");
    }

    public double readFloatFromShortCastDouble() {
        return getFloat(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromShortCastDouble() {
        doTest("readFloatFromShortCastDouble");
    }

    public double readFloatFromCharCastDouble() {
        return getFloat(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testFloatFromCharCastDouble() {
        doTest("readFloatFromCharCastDouble");
    }

    public double readFloatFromIntCastDouble() {
        return getFloat(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromIntCastDouble() {
        doTest("readFloatFromIntCastDouble");
    }

    public double readFloatFromFloatCastDouble() {
        return getFloat(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromFloatCastDouble() {
        doTest("readFloatFromFloatCastDouble");
    }

    public double readFloatFromLongCastDouble() {
        return getFloat(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testFloatFromLongCastDouble() {
        doTest("readFloatFromLongCastDouble");
    }

    public double readFloatFromDoubleCastDouble() {
        return getFloat(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testFloatFromDoubleCastDouble() {
        doTest("readFloatFromDoubleCastDouble");
    }

    public double readFloatFromObjectCastDouble() {
        return getFloat(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testFloatFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readFloatFromObjectCastDouble");
    }

    public double readLongFromBooleanCastDouble() {
        return getLong(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testLongFromBooleanCastDouble() {
        doTest("readLongFromBooleanCastDouble");
    }

    public double readLongFromByteCastDouble() {
        return getLong(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testLongFromByteCastDouble() {
        doTest("readLongFromByteCastDouble");
    }

    public double readLongFromShortCastDouble() {
        return getLong(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testLongFromShortCastDouble() {
        doTest("readLongFromShortCastDouble");
    }

    public double readLongFromCharCastDouble() {
        return getLong(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testLongFromCharCastDouble() {
        doTest("readLongFromCharCastDouble");
    }

    public double readLongFromIntCastDouble() {
        return getLong(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testLongFromIntCastDouble() {
        doTest("readLongFromIntCastDouble");
    }

    public double readLongFromFloatCastDouble() {
        return getLong(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testLongFromFloatCastDouble() {
        doTest("readLongFromFloatCastDouble");
    }

    public double readLongFromLongCastDouble() {
        return getLong(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testLongFromLongCastDouble() {
        doTest("readLongFromLongCastDouble");
    }

    public double readLongFromDoubleCastDouble() {
        return getLong(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testLongFromDoubleCastDouble() {
        doTest("readLongFromDoubleCastDouble");
    }

    public double readLongFromObjectCastDouble() {
        return getLong(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testLongFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readLongFromObjectCastDouble");
    }

    public double readDoubleFromBooleanCastDouble() {
        return getDouble(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromBooleanCastDouble() {
        doTest("readDoubleFromBooleanCastDouble");
    }

    public double readDoubleFromByteCastDouble() {
        return getDouble(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromByteCastDouble() {
        doTest("readDoubleFromByteCastDouble");
    }

    public double readDoubleFromShortCastDouble() {
        return getDouble(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromShortCastDouble() {
        doTest("readDoubleFromShortCastDouble");
    }

    public double readDoubleFromCharCastDouble() {
        return getDouble(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromCharCastDouble() {
        doTest("readDoubleFromCharCastDouble");
    }

    public double readDoubleFromIntCastDouble() {
        return getDouble(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromIntCastDouble() {
        doTest("readDoubleFromIntCastDouble");
    }

    public double readDoubleFromFloatCastDouble() {
        return getDouble(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromFloatCastDouble() {
        doTest("readDoubleFromFloatCastDouble");
    }

    public double readDoubleFromLongCastDouble() {
        return getDouble(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromLongCastDouble() {
        doTest("readDoubleFromLongCastDouble");
    }

    public double readDoubleFromDoubleCastDouble() {
        return getDouble(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromDoubleCastDouble() {
        doTest("readDoubleFromDoubleCastDouble");
    }

    public double readDoubleFromObjectCastDouble() {
        return getDouble(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testDoubleFromObjectCastDouble() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readDoubleFromObjectCastDouble");
    }

    public Object readObjectFromBooleanCastObject() {
        return getObject(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET);
    }

    @Test
    public void testObjectFromBooleanCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromBooleanCastObject");
    }

    public Object readObjectFromByteCastObject() {
        return getObject(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testObjectFromByteCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromByteCastObject");
    }

    public Object readObjectFromShortCastObject() {
        return getObject(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET);
    }

    @Test
    public void testObjectFromShortCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromShortCastObject");
    }

    public Object readObjectFromCharCastObject() {
        return getObject(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET);
    }

    @Test
    public void testObjectFromCharCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromCharCastObject");
    }

    public Object readObjectFromIntCastObject() {
        return getObject(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET);
    }

    @Test
    public void testObjectFromIntCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromIntCastObject");
    }

    public Object readObjectFromFloatCastObject() {
        return getObject(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET);
    }

    @Test
    public void testObjectFromFloatCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromFloatCastObject");
    }

    public Object readObjectFromLongCastObject() {
        return getObject(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testObjectFromLongCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromLongCastObject");
    }

    public Object readObjectFromDoubleCastObject() {
        return getObject(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET);
    }

    @Test
    public void testObjectFromDoubleCastObject() {
        // Mixing Object and primitive produces unstable results and crashes
        // so just compile these patterns to exercise the folding paths.
        shouldFold = false;
        doParse("readObjectFromDoubleCastObject");
    }

    public Object readObjectFromObjectCastObject() {
        return getObject(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET);
    }

    @Test
    public void testObjectFromObjectCastObject() {
        doTest("readObjectFromObjectCastObject");
    }

}
