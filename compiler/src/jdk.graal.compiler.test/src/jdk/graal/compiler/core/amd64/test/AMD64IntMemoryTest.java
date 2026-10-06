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
public class AMD64IntMemoryTest extends MatchRuleTest {
    @Parameter public long mask;

    @Parameters(name = "mask={0}")
    public static Object[] data() {
        return new Object[]{0L, 1L, 3L, 0x8000_0001L, 0xffff_ffffL, 0x1_0000_0000L, 0x1_0000_0001L, Long.MIN_VALUE, Long.MIN_VALUE | 1, -1L};
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
        public int flags;
        public int set = 42;
        public int clear = 24;
    }

    public static int signedBranch(Holder holder, long mask) {
        if (((long) holder.flags & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int unsignedBranch(Holder holder, long mask) {
        if ((Integer.toUnsignedLong(holder.flags) & mask) != 0) {
            return holder.set;
        }
        return holder.clear;
    }

    public static int signedConditional(Holder holder, long mask) {
        return ((long) holder.flags & mask) != 0 ? 42 : 24;
    }

    public static int unsignedConditional(Holder holder, long mask) {
        return (Integer.toUnsignedLong(holder.flags) & mask) != 0 ? 42 : 24;
    }

    public static boolean signedBoolean(Holder holder, long mask) {
        return ((long) holder.flags & mask) != 0;
    }

    public static boolean unsignedBoolean(Holder holder, long mask) {
        return (Integer.toUnsignedLong(holder.flags) & mask) != 0;
    }

    public static int sharedRead(Holder holder, long mask) {
        int flags = holder.flags;
        return ((long) flags & mask) != 0 ? flags + 42 : flags + 24;
    }

    public static long longSelection(Holder holder, long mask) {
        return ((long) holder.flags & mask) != 0 ? Long.MIN_VALUE : 0L;
    }

    public static double doubleSelection(Holder holder, long mask) {
        return ((long) holder.flags & mask) != 0 ? 1.5 : -3.5;
    }

    public static Object referenceSelection(Holder holder, long mask) {
        return ((long) holder.flags & mask) != 0 ? Holder.class : null;
    }

    public static int interveningWrite(Holder holder, long mask) {
        int flags = holder.flags;
        holder.flags = 0;
        return ((long) flags & mask) != 0 ? 42 : 24;
    }

    private void checkValues(String method, boolean unsigned, boolean booleanResult, boolean addFlags, boolean writesFlags) throws Exception {
        InstalledCode code = getCode(getResolvedJavaMethod(method));
        Holder holder = new Holder();
        int[] values = new int[132];
        values[0] = Integer.MIN_VALUE;
        values[1] = Integer.MAX_VALUE;
        values[2] = 0;
        values[3] = -1;
        for (int bit = 0; bit < Integer.SIZE; bit++) {
            values[4 + bit * 4] = 1 << bit;
            values[5 + bit * 4] = ~(1 << bit);
            values[6 + bit * 4] = (1 << bit) - 1;
            values[7 + bit * 4] = (1 << bit) + 1;
        }
        for (int value : values) {
            holder.flags = value;
            long extended = unsigned ? Integer.toUnsignedLong(value) : value;
            boolean set = (extended & mask) != 0;
            if (booleanResult) {
                Assert.assertEquals(set, code.executeVarargs(holder, mask));
            } else {
                int expected = set ? 42 : 24;
                if (addFlags) {
                    expected += value;
                }
                Assert.assertEquals(expected, code.executeVarargs(holder, mask));
            }
            Assert.assertEquals(writesFlags ? 0 : value, holder.flags);
        }
        test(method, null, mask);
    }

    private boolean checkFolding() {
        return mask == 3 || mask == 0x8000_0001L || mask == 0x1_0000_0001L || mask == (Long.MIN_VALUE | 1);
    }

    @Test
    public void testSignedBranch() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("signedBranch", op -> op instanceof TestConstBranchOp, 1);
            checkLIRforAll("signedBranch", op -> op instanceof AMD64Unary.MemoryOp && op.name().equals("MOVSXD"), 0);
        }
        checkValues("signedBranch", false, false, false, false);
    }

    @Test
    public void testUnsignedBranch() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("unsignedBranch", op -> op instanceof TestConstBranchOp, 1);
        }
        checkValues("unsignedBranch", true, false, false, false);
    }

    @Test
    public void testSignedConditional() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("signedConditional", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
            checkLIRforAll("signedConditional", op -> op instanceof AMD64Unary.MemoryOp && op.name().equals("MOVSXD"), 0);
        }
        checkValues("signedConditional", false, false, false, false);
    }

    @Test
    public void testUnsignedConditional() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("unsignedConditional", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
        }
        checkValues("unsignedConditional", true, false, false, false);
    }

    @Test
    public void testSignedBoolean() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("signedBoolean", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
        }
        checkValues("signedBoolean", false, true, false, false);
    }

    @Test
    public void testUnsignedBoolean() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("unsignedBoolean", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 1);
        }
        checkValues("unsignedBoolean", true, true, false, false);
    }

    @Test
    public void testSelectionKinds() {
        Holder holder = new Holder();
        for (int value : new int[]{0, 3, Integer.MIN_VALUE, -1}) {
            holder.flags = value;
            test("longSelection", holder, mask);
            test("doubleSelection", holder, mask);
            test("referenceSelection", holder, mask);
        }
        test("longSelection", null, mask);
        test("doubleSelection", null, mask);
        test("referenceSelection", null, mask);
    }

    @Test
    public void testSharedRead() throws Exception {
        if (checkFolding()) {
            checkLIRforAll("sharedRead", op -> op instanceof AMD64BinaryConsumer.MemoryConstOp && op.name().equals("TEST"), 0);
        }
        checkValues("sharedRead", false, false, true, false);
    }

    @Test
    public void testInterveningWrite() throws Exception {
        checkValues("interveningWrite", false, false, false, true);
    }
}
