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
package jdk.graal.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.core.test.MatchRuleTest;
import jdk.graal.compiler.lir.amd64.AMD64BinaryConsumer;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.TestConstBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64Unary;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;

@RunWith(Parameterized.class)
public class AMD64WordMemoryTest extends MatchRuleTest {
    @Parameter(0) public boolean unsigned;
    @Parameter(1) public int mask;

    @Parameters(name = "unsigned={0}, mask={1}")
    public static List<Object[]> data() {
        List<Object[]> data = new ArrayList<>();
        for (boolean unsigned : new boolean[]{false, true}) {
            for (int mask : new int[]{1, 2, 3, 0x80, 0x81, 0xff, 0x100, 0x101, 0x8000, 0x8001, 0xffff, 0x10000, 0x10001, -1}) {
                data.add(new Object[]{unsigned, mask});
            }
        }
        return data;
    }

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    @Override
    protected Object[] getArgumentToBind() {
        return new Object[]{NO_BIND, mask};
    }

    public static class Holder {
        public short shortFlags;
        public char charFlags;
        public short[] shortArray = new short[1];
        public char[] charArray = new char[1];
        public int set = 42;
        public int clear = 24;
    }

    public static int shortTest(Holder holder, int mask) {
        if ((holder.shortFlags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int charTest(Holder holder, int mask) {
        if ((holder.charFlags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int shortLongTest(Holder holder, int mask) {
        if (((long) holder.shortFlags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int charLongTest(Holder holder, int mask) {
        if (((long) holder.charFlags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int shortSharedRead(Holder holder, int mask) {
        int flags = holder.shortFlags;
        if ((flags & mask) != 0) {
            return flags + holder.set;
        }
        return flags + holder.clear;
    }

    public static int charSharedRead(Holder holder, int mask) {
        int flags = holder.charFlags;
        if ((flags & mask) != 0) {
            return flags + holder.set;
        }
        return flags + holder.clear;
    }

    public static int shortInterveningWrite(Holder holder, int mask) {
        int flags = holder.shortFlags;
        holder.shortFlags = 0;
        if ((flags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int charInterveningWrite(Holder holder, int mask) {
        int flags = holder.charFlags;
        holder.charFlags = 0;
        if ((flags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int shortArrayTest(Holder holder, int mask) {
        if ((holder.shortArray[0] & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int charArrayTest(Holder holder, int mask) {
        if ((holder.charArray[0] & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    private String methodName(String suffix) {
        return (unsigned ? "char" : "short") + suffix;
    }

    public static int shortConditional(Holder holder, int mask) {
        return (holder.shortFlags & mask) != 0 ? 42 : 24;
    }

    public static int charConditional(Holder holder, int mask) {
        return (holder.charFlags & mask) != 0 ? 42 : 24;
    }

    public static boolean shortBooleanResult(Holder holder, int mask) {
        return (holder.shortFlags & mask) != 0;
    }

    public static boolean charBooleanResult(Holder holder, int mask) {
        return (holder.charFlags & mask) != 0;
    }

    private String loadName() {
        return unsigned ? "MOVZX" : "MOVSX";
    }

    private void checkValues(String method, boolean addFlags, boolean writesFlags) throws Exception {
        InstalledCode code = getCode(getResolvedJavaMethod(method));
        Holder holder = new Holder();
        for (int bits = Character.MIN_VALUE; bits <= Character.MAX_VALUE; bits++) {
            holder.shortFlags = (short) bits;
            holder.charFlags = (char) bits;
            holder.shortArray[0] = (short) bits;
            holder.charArray[0] = (char) bits;
            int value = unsigned ? bits : (short) bits;
            int expected = (value & mask) != 0 ? holder.set : holder.clear;
            if (addFlags) {
                expected += value;
            }
            Assert.assertEquals(expected, code.executeVarargs(holder, mask));
            Assert.assertEquals(writesFlags ? 0 : value, unsigned ? holder.charFlags : holder.shortFlags);
        }
        test(method, null, mask);
    }

    private void checkFolded(String method) {
        // These masks retain an IntegerTest rather than simplifying to an equality or sign test.
        if (mask == 1 || mask == 2 || mask == 3 || mask == 0x81 || mask == 0x101 || mask == 0x8001 || mask == 0x10001) {
            checkLIRforAll(method, op -> op instanceof TestConstBranchOp, 1);
            checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals(loadName()), 0);
        }
    }

    @Test
    public void testWord() throws Exception {
        String method = methodName("Test");
        checkFolded(method);
        checkValues(method, false, false);
    }

    @Test
    public void testLong() throws Exception {
        String method = methodName("LongTest");
        checkFolded(method);
        checkValues(method, false, false);
    }

    @Test
    public void testConditional() throws Exception {
        String method = methodName("Conditional");
        if (mask == 3 || mask == 0x8001 || mask == 0x10001) {
            checkLIRforAll(method, op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
            checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals(loadName()), 0);
        }
        checkValues(method, false, false);
    }

    @Test
    public void testBooleanResult() throws Exception {
        String method = methodName("BooleanResult");
        if (mask == 3 || mask == 0x8001 || mask == 0x10001) {
            checkLIRforAll(method, op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
        }
        InstalledCode code = getCode(getResolvedJavaMethod(method));
        Holder holder = new Holder();
        for (int bits = Character.MIN_VALUE; bits <= Character.MAX_VALUE; bits++) {
            holder.shortFlags = (short) bits;
            holder.charFlags = (char) bits;
            int value = unsigned ? bits : (short) bits;
            Assert.assertEquals((value & mask) != 0, code.executeVarargs(holder, mask));
        }
        test(method, null, mask);
    }

    @Test
    public void testSharedRead() throws Exception {
        String method = methodName("SharedRead");
        checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals(loadName()), 1);
        checkValues(method, true, false);
    }

    @Test
    public void testInterveningWrite() throws Exception {
        String method = methodName("InterveningWrite");
        if (!unsigned || (mask & 0xffff) != 0) {
            checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals(loadName()), 1);
        }
        checkValues(method, false, true);
    }

    @Test
    public void testArray() throws Exception {
        String method = methodName("ArrayTest");
        checkFolded(method);
        checkValues(method, false, false);
        Holder holder = new Holder();
        holder.shortArray = new short[0];
        holder.charArray = new char[0];
        test(method, holder, mask);
        holder.shortArray = null;
        holder.charArray = null;
        test(method, holder, mask);
    }
}
