/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64.test;

import static org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase.Options.UseTrappingNullChecks;
import static org.junit.Assume.assumeTrue;

import java.util.function.Predicate;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.test.MatchRuleTest;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer.MemoryConstOp;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer.MemoryMROp;
import org.graalvm.compiler.lir.amd64.AMD64UnaryConsumer.MemoryOp;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

public class AMD64ReadAfterWriteMatchTest extends MatchRuleTest {

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
        ((HotSpotResolvedJavaMethod) getResolvedJavaMethod("reset")).setNotInlinableOrCompilable();
        ((HotSpotResolvedJavaMethod) getResolvedJavaMethod("getValue")).setNotInlinableOrCompilable();
    }

    static int staticIntField = 0xBAADCAFE;

    public static class A {
        int intField = 0xBAADCAFE;
    }

    @BytecodeParserNeverInline
    static void reset() {
        staticIntField = 0xBAADCAFE;
    }

    @BytecodeParserNeverInline
    static int getValue() {
        return staticIntField;
    }

    @BytecodeParserNeverInline
    static void resetInstance(A a) {
        a.intField = 0xBAADCAFE;
    }

    @BytecodeParserNeverInline
    static int getInstanceValue(A a) {
        return a.intField;
    }

    public int compileAndCount(String method, OptionValues options, Predicate<LIRInstruction> filter) {
        compile(getResolvedJavaMethod(method), null, options != null ? options : getInitialOptions());
        LIR lir = getLIR();
        int count = 0;
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            for (LIRInstruction ins : lir.getLIRforBlock(block)) {
                if (filter.test(ins)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int snippetAdd1() {
        reset();
        staticIntField++;
        return getValue();
    }

    @Test
    public void testSnippetAdd1() {
        int cnt = compileAndCount("snippetAdd1", null, ins -> ins instanceof MemoryOp && "INC".equals(((MemoryOp) ins).getOpcode().toString()));
        assumeTrue("INC is expected once in the LIR", cnt == 1);
        test("snippetAdd1");
    }

    public static int snippetAddLargeConstant() {
        reset();
        staticIntField += 0xDEADBEEF;
        return getValue();
    }

    @Test
    public void testSnippetAddLargeConstant() {
        int cnt = compileAndCount("snippetAddLargeConstant", null, ins -> ins instanceof MemoryConstOp && "ADD".equals(((MemoryConstOp) ins).getOpcode().toString()));
        assumeTrue("ADD is expected once in the LIR", cnt == 1);
        test("snippetAddLargeConstant");
    }

    public static int snippetAddVariable(int i) {
        reset();
        staticIntField += i;
        return getValue();
    }

    @Test
    public void testSnippetAddVariable() {
        int cnt = compileAndCount("snippetAddVariable", null, ins -> ins instanceof MemoryMROp && "ADD".equals(((MemoryMROp) ins).getOpcode().toString()));
        assumeTrue("ADD is expected once in the LIR", cnt == 1);
        test("snippetAddVariable", 1);
        test("snippetAddVariable", 0xDEADBEEF);
    }

    public static int snippetInstanceFieldAdd1(A a) {
        resetInstance(a);
        a.intField++;
        return getInstanceValue(a);
    }

    @Test
    public void testSnippetInstanceFieldAdd1() {
        OptionValues options = getInitialOptions();
        assumeTrue("UseTrappingNullChecks must be on", UseTrappingNullChecks.getValue(options));
        Predicate<LIRInstruction> predicate = ins -> ins instanceof MemoryOp && "INC".equals(((MemoryOp) ins).getOpcode().toString());
        int cnt = compileAndCount("snippetInstanceFieldAdd1", null, predicate);
        assumeTrue("INC is not expected in the LIR", cnt == 0);
        OptionValues disableUseTrappingNullChecks = new OptionValues(options, UseTrappingNullChecks, false);
        cnt = compileAndCount("snippetInstanceFieldAdd1", disableUseTrappingNullChecks, predicate);
        assumeTrue("INC is expected once in the LIR", cnt == 1);
        test("snippetInstanceFieldAdd1", disableUseTrappingNullChecks, new A());
    }

    public static int snippetSub1() {
        reset();
        staticIntField--;
        return getValue();
    }

    @Test
    public void testSnippetSub1() {
        int cnt = compileAndCount("snippetSub1", null, ins -> ins instanceof MemoryOp && "DEC".equals(((MemoryOp) ins).getOpcode().toString()));
        assumeTrue("DEC is expected once in the LIR", cnt == 1);
        test("snippetSub1");
    }

    public static int snippetSubLargeConstant() {
        reset();
        staticIntField -= 0xDEADBEEF;
        return getValue();
    }

    @Test
    public void testSnippetSubLargeConstant() {
        int cnt = compileAndCount("snippetSubLargeConstant", null, ins -> ins instanceof MemoryConstOp && "SUB".equals(((MemoryConstOp) ins).getOpcode().toString()));
        assumeTrue("SUB is expected once in the LIR", cnt == 1);
        test("snippetSubLargeConstant");
    }

    public static int snippetSubVariable(int i) {
        reset();
        staticIntField -= i;
        return getValue();
    }

    @Test
    public void testSnippetSubVariable() {
        int cnt = compileAndCount("snippetSubVariable", null, ins -> ins instanceof MemoryMROp && "SUB".equals(((MemoryMROp) ins).getOpcode().toString()));
        assumeTrue("SUB is expected once in the LIR", cnt == 1);
        test("snippetSubVariable", 1);
        test("snippetSubVariable", 0xDEADBEEF);
    }

    public static int snippetInstanceFieldSub1(A a) {
        resetInstance(a);
        a.intField--;
        return getInstanceValue(a);
    }

    @Test
    public void testSnippetInstanceFieldSub1() {
        OptionValues options = getInitialOptions();
        assumeTrue("UseTrappingNullChecks must be on", UseTrappingNullChecks.getValue(options));
        Predicate<LIRInstruction> predicate = ins -> ins instanceof MemoryOp && "DEC".equals(((MemoryOp) ins).getOpcode().toString());
        int cnt = compileAndCount("snippetInstanceFieldSub1", null, predicate);
        assumeTrue("DEC is not expected in the LIR", cnt == 0);
        OptionValues disableUseTrappingNullChecks = new OptionValues(options, UseTrappingNullChecks, false);
        cnt = compileAndCount("snippetInstanceFieldSub1", disableUseTrappingNullChecks, predicate);
        assumeTrue("DEC is expected once in the LIR", cnt == 1);
        test("snippetInstanceFieldSub1", disableUseTrappingNullChecks, new A());
    }

    public static int snippetOr1() {
        reset();
        staticIntField |= 1;
        return getValue();
    }

    public static int snippetOrLargeConstant() {
        reset();
        staticIntField |= 0xDEADBEEF;
        return getValue();
    }

    @Test
    public void testSnippetOrConstant() {
        Predicate<LIRInstruction> predicate = ins -> ins instanceof MemoryConstOp && "OR".equals(((MemoryConstOp) ins).getOpcode().toString());
        int cnt = compileAndCount("snippetOr1", null, predicate);
        assumeTrue("OR is expected once in the LIR", cnt == 1);
        test("snippetOr1");

        cnt = compileAndCount("snippetOrLargeConstant", null, predicate);
        assumeTrue("OR is expected once in the LIR", cnt == 1);
        test("snippetOrLargeConstant");
    }

    public static int snippetOrVariable(int i) {
        reset();
        staticIntField |= i;
        return getValue();
    }

    @Test
    public void testSnippetOrVariable() {
        int cnt = compileAndCount("snippetOrVariable", null, ins -> ins instanceof MemoryMROp && "OR".equals(((MemoryMROp) ins).getOpcode().toString()));
        assumeTrue("OR is expected once in the LIR", cnt == 1);
        test("snippetOrVariable", 1);
        test("snippetOrVariable", 0xDEADBEEF);
    }

    public static int snippetInstanceFieldOr(A a) {
        resetInstance(a);
        a.intField |= 0xDEADBEEF;
        return getInstanceValue(a);
    }

    @Test
    public void testSnippetInstanceFieldOr() {
        OptionValues options = getInitialOptions();
        assumeTrue("UseTrappingNullChecks must be on", UseTrappingNullChecks.getValue(options));
        Predicate<LIRInstruction> predicate = ins -> ins instanceof MemoryConstOp && "OR".equals(((MemoryConstOp) ins).getOpcode().toString());
        int cnt = compileAndCount("snippetInstanceFieldOr", null, predicate);
        assumeTrue("OR is not expected in the LIR", cnt == 0);
        OptionValues disableUseTrappingNullChecks = new OptionValues(options, UseTrappingNullChecks, false);
        cnt = compileAndCount("snippetInstanceFieldOr", disableUseTrappingNullChecks, predicate);
        assumeTrue("OR is expected once in the LIR", cnt == 1);
        test("snippetInstanceFieldOr", disableUseTrappingNullChecks, new A());
    }

    public static int snippetXor1() {
        reset();
        staticIntField ^= 1;
        return getValue();
    }

    public static int snippetXorLargeConstant() {
        reset();
        staticIntField ^= 0xDEADBEEF;
        return getValue();
    }

    @Test
    public void testSnippetXorConstant() {
        Predicate<LIRInstruction> predicate = ins -> ins instanceof MemoryConstOp && "XOR".equals(((MemoryConstOp) ins).getOpcode().toString());
        int cnt = compileAndCount("snippetXor1", null, predicate);
        assumeTrue("XOR is expected once in the LIR", cnt == 1);
        cnt = compileAndCount("snippetXorLargeConstant", null, predicate);
        assumeTrue("XOR is expected once in the LIR", cnt == 1);
        test("snippetXor1");
        test("snippetXorLargeConstant");
    }

    public static int snippetXorVariable(int i) {
        reset();
        staticIntField ^= i;
        return getValue();
    }

    @Test
    public void testSnippetXorVariable() {
        int cnt = compileAndCount("snippetXorVariable", null, ins -> ins instanceof MemoryMROp && "XOR".equals(((MemoryMROp) ins).getOpcode().toString()));
        assumeTrue("XOR is expected once in the LIR", cnt == 1);
        test("snippetXorVariable", 1);
        test("snippetXorVariable", 0xDEADBEEF);
    }

    public static int snippetInstanceFieldXor(A a) {
        resetInstance(a);
        a.intField ^= 0xDEADBEEF;
        return getInstanceValue(a);
    }

    @Test
    public void testSnippetInstanceFieldXor() {
        OptionValues options = getInitialOptions();
        assumeTrue("UseTrappingNullChecks must be on", UseTrappingNullChecks.getValue(options));
        Predicate<LIRInstruction> predicate = ins -> ins instanceof MemoryConstOp && "XOR".equals(((MemoryConstOp) ins).getOpcode().toString());
        int cnt = compileAndCount("snippetInstanceFieldXor", null, predicate);
        assumeTrue("OR is not expected in the LIR", cnt == 0);
        OptionValues disableUseTrappingNullChecks = new OptionValues(options, UseTrappingNullChecks, false);
        cnt = compileAndCount("snippetInstanceFieldXor", disableUseTrappingNullChecks, predicate);
        assumeTrue("OR is expected once in the LIR", cnt == 1);
        test("snippetInstanceFieldXor", disableUseTrappingNullChecks, new A());
    }
}
