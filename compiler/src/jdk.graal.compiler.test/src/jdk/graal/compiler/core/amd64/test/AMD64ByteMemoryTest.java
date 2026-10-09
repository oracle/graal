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
public class AMD64ByteMemoryTest extends MatchRuleTest {
    @Parameter public int mask;

    @Parameters(name = "mask={0}")
    public static Object[] data() {
        return new Object[]{1, 2, 4, 3, 0x80, 0x81, 0xff, 0x100, 0x101, -1};
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
        public byte flags;
        public int set = 42;
        public int clear = 24;
    }

    public static int byteTest(Holder holder, int mask) {
        if ((holder.flags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int longTest(Holder holder, int mask) {
        if (((long) holder.flags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int sharedRead(Holder holder, int mask) {
        int flags = holder.flags;
        if ((flags & mask) != 0) {
            return flags + holder.set;
        }
        return flags + holder.clear;
    }

    public static int conditional(Holder holder, int mask) {
        return (holder.flags & mask) != 0 ? 42 : 24;
    }

    public static boolean booleanResult(Holder holder, int mask) {
        return (holder.flags & mask) != 0;
    }

    public static int conditionalInterveningWrite(Holder holder, int mask) {
        int flags = holder.flags;
        holder.flags = 0;
        return (flags & mask) != 0 ? 42 : 24;
    }

    public static int interveningWrite(Holder holder, int mask) {
        int flags = holder.flags;
        holder.flags = 0;
        if ((flags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    private void checkValues(String method, boolean addFlags, boolean writesFlags) throws Exception {
        InstalledCode code = getCode(getResolvedJavaMethod(method));
        Holder holder = new Holder();
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; value++) {
            holder.flags = (byte) value;
            int expected = (value & mask) != 0 ? holder.set : holder.clear;
            if (addFlags) {
                expected += value;
            }
            Assert.assertEquals(expected, code.executeVarargs(holder, mask));
            Assert.assertEquals(writesFlags ? 0 : value, holder.flags);
        }
        test(method, null, mask);
    }

    private void checkFolded(String method) {
        // These masks retain an IntegerTest rather than simplifying to an equality or sign test.
        if (mask == 1 || mask == 2 || mask == 4 || mask == 3 || mask == 0x81 || mask == 0x101) {
            checkLIRforAll(method, op -> op instanceof TestConstBranchOp, 1);
            checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals("MOVSXB"), 0);
        }
    }

    @Test
    public void testByte() throws Exception {
        checkFolded("byteTest");
        checkValues("byteTest", false, false);
    }

    @Test
    public void testLong() throws Exception {
        checkFolded("longTest");
        checkValues("longTest", false, false);
    }

    @Test
    public void testConditional() throws Exception {
        if (mask == 3 || mask == 0x81 || mask == 0x101) {
            checkLIRforAll("conditional", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
            checkLIRforAll("conditional", op -> op instanceof AMD64Unary.MemoryOp && op.name().equals("MOVSXB"), 0);
        }
        checkValues("conditional", false, false);
    }

    @Test
    public void testBooleanResult() throws Exception {
        if (mask == 3 || mask == 0x81 || mask == 0x101) {
            checkLIRforAll("booleanResult", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
        }
        InstalledCode code = getCode(getResolvedJavaMethod("booleanResult"));
        Holder holder = new Holder();
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; value++) {
            holder.flags = (byte) value;
            Assert.assertEquals((value & mask) != 0, code.executeVarargs(holder, mask));
        }
        test("booleanResult", null, mask);
    }

    @Test
    public void testConditionalInterveningWrite() throws Exception {
        checkValues("conditionalInterveningWrite", false, true);
    }

    @Test
    public void testSharedRead() throws Exception {
        checkLIRforAll("sharedRead", op -> op instanceof AMD64Unary.MemoryOp && op.name().equals("MOVSXB"), 1);
        checkValues("sharedRead", true, false);
    }

    @Test
    public void testInterveningWrite() throws Exception {
        checkLIRforAll("interveningWrite", op -> op instanceof AMD64Unary.MemoryOp && op.name().equals("MOVSXB"), 1);
        checkValues("interveningWrite", false, true);
    }
}
