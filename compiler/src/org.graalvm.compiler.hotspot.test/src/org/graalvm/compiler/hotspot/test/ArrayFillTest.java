/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.replacements.arraycopy.HotSpotArrayFillSnippets;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ArrayFillTest extends GraalCompilerTest {
    private static boolean isFillCallDescriptor(ForeignCallDescriptor descriptor) {
        return descriptor == HotSpotArrayFillSnippets.ARRAYOF_JBYTE_FILL_CALL ||
                descriptor == HotSpotArrayFillSnippets.ARRAYOF_JINT_FILL_CALL ||
                descriptor == HotSpotArrayFillSnippets.ARRAYOF_JSHORT_FILL_CALL ||
                descriptor == HotSpotArrayFillSnippets.JINT_FILL_CALL ||
                descriptor == HotSpotArrayFillSnippets.JBYTE_FILL_CALL ||
                descriptor == HotSpotArrayFillSnippets.JSHORT_FILL_CALL;
    }

    public int[] int_fill1() {
        int[] arr = new int[300];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 1024 || arr[arr.length - 1] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public int[] int_fill2() {
        int[] arr = new int[300];
        Arrays.fill(arr, 1234);
        if (arr[0] != 1024 || arr[arr.length - 1] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public int[] int_fill3() {
        int[] arr = new int[300];
        for (int i = 0; i < 100; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 1024 || arr[100] != 0) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public int[] int_fill4() {
        int[] arr = new int[300];
        Arrays.fill(arr, arr.length);
        if (arr[0] != 1024 || arr[arr.length - 1] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public int[] int_fill5() {
        int[] arr = new int[300];
        for (int i = 3; i < arr.length; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 0 || arr[1] != 0 || arr[2] != 0 || arr[3] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public int[] int_fill6() {
        int[] arr = new int[300];
        for (int i = 3; i < 100; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 0 || arr[1] != 0 || arr[2] != 0 || arr[3] != 1024 || arr[99] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public int[] no_int_fill1() {
        int[] arr = new int[300];
        for (int i = 0; i < arr.length; i++) {
            if (i % 2 == 0) {
                // interference...
                i++;
            }
            arr[i] = 1024;
        }
        return arr;
    }

    public int[] no_int_fill2() {
        int[] arr = new int[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = 1024;
        }
        return arr;
    }

    public int[] no_int_fill3() {
        int[] arr = new int[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = i * 2;
        }
        return arr;
    }

    public int[] no_int_fill4() {
        int[] arr = new int[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += 2) {
            arr[k] = 1;
            k++;
        }
        return arr;
    }

    public int[] no_int_fill5() {
        int[] arr = new int[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += arr.length) {
            arr[k] = 1;
        }
        return arr;
    }

    public short[] short_fill1() {
        short[] arr = new short[300];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 1024 || arr[arr.length - 1] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public short[] short_fill2() {
        short[] arr = new short[300];
        Arrays.fill(arr, (short) 1234);
        if (arr[0] != 1024 || arr[arr.length - 1] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public short[] short_fill3() {
        short[] arr = new short[300];
        for (int i = 0; i < 100; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 1024 || arr[100] != 0) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public short[] short_fill4() {
        short[] arr = new short[300];
        Arrays.fill(arr, (short) arr.length);
        if (arr[0] != 1024 || arr[arr.length - 1] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public short[] short_fill5() {
        short[] arr = new short[300];
        for (int i = 3; i < arr.length; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 0 || arr[1] != 0 || arr[2] != 0 || arr[3] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public short[] short_fill6() {
        short[] arr = new short[300];
        for (int i = 3; i < 100; i++) {
            arr[i] = 1024;
        }
        if (arr[0] != 0 || arr[1] != 0 || arr[2] != 0 || arr[3] != 1024 || arr[99] != 1024) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public short[] no_short_fill1() {
        short[] arr = new short[300];
        for (int i = 0; i < arr.length; i++) {
            if (i % 2 == 0) {
                // interference...
                i++;
            }
            arr[i] = 1024;
        }
        return arr;
    }

    public short[] no_short_fill2() {
        short[] arr = new short[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = 1024;
        }
        return arr;
    }

    public short[] no_short_fill3() {
        short[] arr = new short[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = (short) (i * 2);
        }
        return arr;
    }

    public short[] no_short_fill4() {
        short[] arr = new short[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += 2) {
            arr[k] = 1;
            k++;
        }
        return arr;
    }

    public short[] no_short_fill5() {
        short[] arr = new short[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += arr.length) {
            arr[k] = 1;
        }
        return arr;
    }

    public byte[] byte_fill1() {
        byte[] arr = new byte[300];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = 120;
        }
        if (arr[0] != 120 || arr[arr.length - 1] != 120) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public byte[] byte_fill2() {
        byte[] arr = new byte[300];
        Arrays.fill(arr, (byte) 120);
        if (arr[0] != 120 || arr[arr.length - 1] != 120) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public byte[] byte_fill3() {
        byte[] arr = new byte[300];
        for (int i = 0; i < 100; i++) {
            arr[i] = 120;
        }
        if (arr[0] != 120 || arr[100] != 0) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public byte[] byte_fill4() {
        byte[] arr = new byte[300];
        Arrays.fill(arr, (byte) arr.length);
        if (arr[0] != 120 || arr[arr.length - 1] != 120) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public byte[] byte_fill5() {
        byte[] arr = new byte[300];
        for (int i = 3; i < arr.length; i++) {
            arr[i] = 120;
        }
        if (arr[0] != 0 || arr[1] != 0 || arr[2] != 0 || arr[3] != 120) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public byte[] byte_fill6() {
        byte[] arr = new byte[300];
        for (int i = 3; i < 100; i++) {
            arr[i] = 120;
        }
        if (arr[0] != 0 || arr[1] != 0 || arr[2] != 0 || arr[3] != 120 || arr[99] != 120) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public byte[] no_byte_fill1() {
        byte[] arr = new byte[300];
        for (int i = 0; i < arr.length; i++) {
            if (i % 2 == 0) {
                // interference...
                i++;
            }
            arr[i] = 120;
        }
        return arr;
    }

    public byte[] no_byte_fill2() {
        byte[] arr = new byte[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = 120;
        }
        return arr;
    }

    public byte[] no_byte_fill3() {
        byte[] arr = new byte[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = (byte) (i * 2);
        }
        return arr;
    }

    public byte[] no_byte_fill4() {
        byte[] arr = new byte[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += 2) {
            arr[k] = 1;
            k++;
        }
        return arr;
    }

    public byte[] no_byte_fill5() {
        byte[] arr = new byte[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += arr.length) {
            arr[k] = 1;
        }
        return arr;
    }

    public boolean[] bool_fill1() {
        boolean[] arr = new boolean[300];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = true;
        }
        if (arr[0] != true || arr[arr.length - 1] != true) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public boolean[] bool_fill2() {
        boolean[] arr = new boolean[300];
        Arrays.fill(arr, true);
        if (arr[0] != true || arr[arr.length - 1] != true) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public boolean[] bool_fill3() {
        boolean[] arr = new boolean[300];
        for (int i = 0; i < 100; i++) {
            arr[i] = true;
        }
        if (arr[0] != true || arr[100] != false) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public boolean[] bool_fill4() {
        boolean[] arr = new boolean[300];
        Arrays.fill(arr, true);
        if (arr[0] != true || arr[arr.length - 1] != true) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public boolean[] bool_fill5() {
        boolean[] arr = new boolean[300];
        for (int i = 3; i < arr.length; i++) {
            arr[i] = true;
        }
        if (arr[0] != false || arr[1] != false || arr[2] != false || arr[3] != true) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public boolean[] bool_fill6() {
        boolean[] arr = new boolean[300];
        for (int i = 3; i < 100; i++) {
            arr[i] = true;
        }
        if (arr[0] != false || arr[1] != false || arr[2] != false || arr[3] != true || arr[99] != true) {
            throw new RuntimeException("Wrong optimization");
        }
        return arr;
    }

    public boolean[] no_bool_fill1() {
        boolean[] arr = new boolean[300];
        for (int i = 0; i < arr.length; i++) {
            if (i % 2 == 0) {
                // interference...
                i++;
            }
            arr[i] = true;
        }
        return arr;
    }

    public boolean[] no_bool_fill2() {
        boolean[] arr = new boolean[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = true;
        }
        return arr;
    }

    public boolean[] no_bool_fill3() {
        boolean[] arr = new boolean[300];
        for (int i = 0; i < arr.length; i += 2) {
            arr[i] = (i % 2 == 0);
        }
        return arr;
    }

    public boolean[] no_bool_fill4() {
        boolean[] arr = new boolean[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += 2) {
            arr[k] = true;
            k++;
        }
        return arr;
    }

    public boolean[] no_bool_fill5() {
        boolean[] arr = new boolean[300];
        int k = 0;
        for (int i = 0; i < arr.length; i += arr.length) {
            arr[k] = true;
        }
        return arr;
    }

    private boolean verifyReplaced(String method) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(method), getInitialOptions());
        final StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        final List<Loop<Block>> loops = schedule.getCFG().getLoops();
        final NodeIterable<ForeignCallNode> calls = graph.getNodes().filter(ForeignCallNode.class);
        boolean loopEliminated = (loops.size() == 1); // fill memory loop only
        boolean found = false;
        for (ForeignCallNode call : calls) {
            found |= isFillCallDescriptor(call.getDescriptor());
        }
        return loopEliminated && found;
    }

    @Test
    public void testArrayFillReplaced() {
        Assert.assertTrue(verifyReplaced("int_fill1"));
        Assert.assertTrue(verifyReplaced("int_fill2"));
        Assert.assertTrue(verifyReplaced("int_fill3"));
        Assert.assertTrue(verifyReplaced("int_fill4"));
        Assert.assertTrue(verifyReplaced("int_fill5"));
        Assert.assertTrue(verifyReplaced("int_fill6"));
        Assert.assertFalse(verifyReplaced("no_int_fill1"));
        Assert.assertFalse(verifyReplaced("no_int_fill2"));
        Assert.assertFalse(verifyReplaced("no_int_fill3"));
        Assert.assertFalse(verifyReplaced("no_int_fill4"));
        Assert.assertFalse(verifyReplaced("no_int_fill5"));
        Assert.assertTrue(verifyReplaced("short_fill1"));
        Assert.assertTrue(verifyReplaced("short_fill2"));
        Assert.assertTrue(verifyReplaced("short_fill3"));
        Assert.assertTrue(verifyReplaced("short_fill4"));
        Assert.assertTrue(verifyReplaced("short_fill5"));
        Assert.assertTrue(verifyReplaced("short_fill6"));
        Assert.assertFalse(verifyReplaced("no_short_fill1"));
        Assert.assertFalse(verifyReplaced("no_short_fill2"));
        Assert.assertFalse(verifyReplaced("no_short_fill3"));
        Assert.assertFalse(verifyReplaced("no_short_fill4"));
        Assert.assertFalse(verifyReplaced("no_short_fill5"));
        Assert.assertTrue(verifyReplaced("byte_fill1"));
        Assert.assertTrue(verifyReplaced("byte_fill2"));
        Assert.assertTrue(verifyReplaced("byte_fill3"));
        Assert.assertTrue(verifyReplaced("byte_fill4"));
        Assert.assertTrue(verifyReplaced("byte_fill5"));
        Assert.assertTrue(verifyReplaced("byte_fill6"));
        Assert.assertFalse(verifyReplaced("no_byte_fill1"));
        Assert.assertFalse(verifyReplaced("no_byte_fill2"));
        Assert.assertFalse(verifyReplaced("no_byte_fill3"));
        Assert.assertFalse(verifyReplaced("no_byte_fill4"));
        Assert.assertFalse(verifyReplaced("no_byte_fill5"));
        Assert.assertTrue(verifyReplaced("bool_fill1"));
        Assert.assertTrue(verifyReplaced("bool_fill2"));
        Assert.assertTrue(verifyReplaced("bool_fill3"));
        Assert.assertTrue(verifyReplaced("bool_fill4"));
        Assert.assertTrue(verifyReplaced("bool_fill5"));
        Assert.assertTrue(verifyReplaced("bool_fill6"));
        Assert.assertFalse(verifyReplaced("no_bool_fill1"));
        Assert.assertFalse(verifyReplaced("no_bool_fill2"));
        Assert.assertFalse(verifyReplaced("no_bool_fill3"));
        Assert.assertFalse(verifyReplaced("no_bool_fill4"));
        Assert.assertFalse(verifyReplaced("no_bool_fill5"));
    }
}