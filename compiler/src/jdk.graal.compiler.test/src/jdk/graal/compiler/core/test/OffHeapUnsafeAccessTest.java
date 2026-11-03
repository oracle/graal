/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.memory.ReadNode;
import org.junit.Assert;
import org.junit.Test;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Tests that ordinary writes to native memory permit elimination of Java heap reads, while atomic
 * operations on native memory or the Java heap prevent it.
 */
public class OffHeapUnsafeAccessTest extends GraalCompilerTest {

    public byte unboxByteAndStore(long memory, byte[] box) {
        byte val = box[0];
        UNSAFE.putByte(memory, val);
        UNSAFE.putByte(null, memory, val);
        return box[0];
    }

    public char unboxCharAndStore(long memory, char[] box) {
        char val = box[0];
        UNSAFE.putChar(memory, val);
        UNSAFE.putChar(null, memory, val);
        return box[0];
    }

    public int unboxIntAndStore(long memory, int[] box) {
        int val = box[0];
        UNSAFE.putInt(memory, val);
        UNSAFE.putInt(null, memory, val);
        return box[0];
    }

    public long unboxLongAndStore(long memory, long[] box) {
        long val = box[0];
        UNSAFE.putLong(memory, val);
        UNSAFE.putLong(null, memory, val);
        UNSAFE.putAddress(memory, val);
        return box[0];
    }

    public float unboxFloatAndStore(long memory, float[] box) {
        float val = box[0];
        UNSAFE.putFloat(memory, val);
        UNSAFE.putFloat(null, memory, val);
        return box[0];
    }

    public double unboxDoubleAndStore(long memory, double[] box) {
        double val = box[0];
        UNSAFE.putDouble(memory, val);
        UNSAFE.putDouble(null, memory, val);
        return box[0];
    }

    private void assertArrayLoadCount(JavaKind elementKind, int expected) {
        assertLoadCount(NamedLocationIdentity.getArrayLocation(elementKind), expected);
    }

    private void assertLoadCount(LocationIdentity location, int expected) {
        int total = 0;
        for (ReadNode read : lastCompiledGraph.getNodes().filter(ReadNode.class)) {
            if (read.getLocationIdentity().equals(location)) {
                total++;
            }
        }
        Assert.assertEquals(expected, total);
    }

    @Test
    public void testGet() {
        long buf = allocBuf();
        if (buf != 0) {
            try {
                test("unboxByteAndStore", buf, new byte[]{40});
                assertArrayLoadCount(JavaKind.Byte, 1);

                test("unboxCharAndStore", buf, new char[]{41});
                assertArrayLoadCount(JavaKind.Char, 1);

                test("unboxIntAndStore", buf, new int[]{42});
                assertArrayLoadCount(JavaKind.Int, 1);

                test("unboxLongAndStore", buf, new long[]{43});
                assertArrayLoadCount(JavaKind.Long, 1);

                test("unboxFloatAndStore", buf, new float[]{44.0F});
                assertArrayLoadCount(JavaKind.Float, 1);

                test("unboxDoubleAndStore", buf, new double[]{45.0D});
                assertArrayLoadCount(JavaKind.Double, 1);
            } finally {
                UNSAFE.freeMemory(buf);
            }
        }
    }

    protected long allocBuf() {
        try {
            return UNSAFE.allocateMemory(16);
        } catch (OutOfMemoryError e) {
            return 0L;
        }
    }

    /** Java field whose reads must be separated by the atomic operation. */
    public static class FieldBox {
        public long longValue = 43;
    }

    /** Uses a distinct class so that the atomic target cannot alias the field being read. */
    public static class AtomicCell {
        public int intValue;
        public long longValue;
    }

    private static final long INT_OFFSET;
    private static final long LONG_OFFSET;

    static {
        try {
            INT_OFFSET = UNSAFE.objectFieldOffset(AtomicCell.class.getDeclaredField("intValue"));
            LONG_OFFSET = UNSAFE.objectFieldOffset(AtomicCell.class.getDeclaredField("longValue"));
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Both Java heap reads must survive because the atomic operation can acquire a Java heap update
     * published by another thread.
     */
    public int unboxIntAndGetAndAddLong(long memory, int[] box) {
        int before = box[0];
        UNSAFE.getAndAddLong(null, memory, 1L);
        return before + box[0];
    }

    public long fieldLongAndGetAndSetInt(long memory, FieldBox box) {
        long before = box.longValue;
        UNSAFE.getAndSetInt(null, memory, 1);
        return before + box.longValue;
    }

    public int unboxIntAndHeapGetAndSetLong(AtomicCell target, int[] box) {
        int before = box[0];
        UNSAFE.getAndSetLong(target, LONG_OFFSET, 1L);
        return before + box[0];
    }

    public long fieldLongAndHeapGetAndAddInt(AtomicCell target, FieldBox box) {
        long before = box.longValue;
        UNSAFE.getAndAddInt(target, INT_OFFSET, 1);
        return before + box.longValue;
    }

    public int unboxIntAndArrayGetAndAddLong(long[] target, int[] box) {
        int before = box[0];
        UNSAFE.getAndAddLong(target, Unsafe.ARRAY_LONG_BASE_OFFSET, 1L);
        return before + box[0];
    }

    public long fieldLongAndArrayGetAndSetInt(int[] target, FieldBox box) {
        long before = box.longValue;
        UNSAFE.getAndSetInt(target, Unsafe.ARRAY_INT_BASE_OFFSET, 1);
        return before + box.longValue;
    }

    /**
     * Compiles an atomic access and checks that neither Java heap read was eliminated. No concurrent
     * execution is needed to check this compiler constraint.
     */
    private void testAtomicAccess(String method, JavaKind elementKind, Object box) {
        long memory = UNSAFE.allocateMemory(Long.BYTES);
        try {
            UNSAFE.putLong(memory, 0L);
            test(method, memory, box);
            assertAtomicReadCount(elementKind, box);
        } finally {
            UNSAFE.freeMemory(memory);
        }
    }

    /** Checks reads of either the array element or the field used by an atomic access snippet. */
    private void assertAtomicReadCount(JavaKind kind, Object box) {
        if (box instanceof FieldBox) {
            for (ResolvedJavaField field : getMetaAccess().lookupJavaType(FieldBox.class).getInstanceFields(false)) {
                if (field.getJavaKind() == kind) {
                    assertLoadCount(new FieldLocationIdentity(field), 2);
                    return;
                }
            }
            Assert.fail("Missing field for " + kind);
        } else {
            assertArrayLoadCount(kind, 2);
        }
    }

    private void testHeapAtomicAccess(String method, JavaKind kind, Object box) {
        test(method, new AtomicCell(), box);
        assertAtomicReadCount(kind, box);
    }

    private void testArrayAtomicAccess(String method, JavaKind readKind, JavaKind atomicKind, Object box) {
        Object target = atomicKind == JavaKind.Int ? new int[1] : new long[1];
        test(method, target, box);
        assertAtomicReadCount(readKind, box);
    }

    @Test
    public void testIntArrayReadNativeGetAndAddLong() {
        testAtomicAccess("unboxIntAndGetAndAddLong", JavaKind.Int, new int[]{42});
    }

    @Test
    public void testLongFieldReadNativeGetAndSetInt() {
        testAtomicAccess("fieldLongAndGetAndSetInt", JavaKind.Long, new FieldBox());
    }

    @Test
    public void testIntArrayReadHeapGetAndSetLong() {
        testHeapAtomicAccess("unboxIntAndHeapGetAndSetLong", JavaKind.Int, new int[]{42});
    }

    @Test
    public void testLongFieldReadHeapGetAndAddInt() {
        testHeapAtomicAccess("fieldLongAndHeapGetAndAddInt", JavaKind.Long, new FieldBox());
    }

    @Test
    public void testIntArrayReadArrayGetAndAddLong() {
        testArrayAtomicAccess("unboxIntAndArrayGetAndAddLong", JavaKind.Int, JavaKind.Long, new int[]{42});
    }

    @Test
    public void testLongFieldReadArrayGetAndSetInt() {
        testArrayAtomicAccess("fieldLongAndArrayGetAndSetInt", JavaKind.Long, JavaKind.Int, new FieldBox());
    }

}
