/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.asm.amd64.test;

import java.util.Arrays;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * Regression tests for checking that
 * {@link jdk.graal.compiler.core.common.GraalOptions#OptimizeLongJumps} replaces {@code jmp/jcc}
 * instructions with {@code jmpb/jccb}.
 */
public class OptimizeLongJumpsTest extends GraalCompilerTest {

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    public static int sideeffect = 0;

    public static int snippet01(int bound) {
        for (int i = 0; i < bound; i++) {
            sideeffect += i;
        }
        sideeffect = 0;
        return sideeffect;
    }

    public static int snippet02(boolean b, int x1, int x2) {
        if (b) {
            return x1;
        }
        return x2;
    }

    @Test
    public void test01() throws InvalidInstalledCodeException {
        OptionValues options = new OptionValues(getInitialOptions(), AMD64Assembler.Options.UseBranchesWithin32ByteBoundary, true, CompilerConfigurationFactory.Options.CompilerConfiguration,
                        "economy");
        testOptimizeLongJumps("snippet01", options, 42);
    }

    @Test
    public void test02() throws InvalidInstalledCodeException {
        OptionValues options = new OptionValues(getInitialOptions(), AMD64Assembler.Options.UseBranchesWithin32ByteBoundary, true, CompilerConfigurationFactory.Options.CompilerConfiguration,
                        "economy");
        testOptimizeLongJumps("snippet02", options, true, 1, 2);
    }

    private void testOptimizeLongJumps(String method, OptionValues opts, Object... params) throws InvalidInstalledCodeException {
        OptionValues optionsDefault = new OptionValues(opts, GraalOptions.OptimizeLongJumps, false);
        StructuredGraph graphDefault = parseEager(method, AllowAssumptions.NO, optionsDefault);
        InstalledCode codeDefault = null;

        OptionValues optionsOptimized = new OptionValues(opts, GraalOptions.OptimizeLongJumps, true);
        StructuredGraph graphOptimized = parseEager(method, AllowAssumptions.NO, optionsOptimized);
        InstalledCode codeOptimized = null;

        for (int i = 0; i < 3; i++) {

            /*
             * Why using a loop: The optimization is considered successful, if there are fewer
             * jmp/jcc instructions compared to the unoptimized code. To assert this condition,
             * checkCode counts long jump / jcc opcodes in the raw code byte arrays. Thus, under
             * rare circumstances, bytes from constants, displacements, etc. can "look" like the
             * opcodes we are searching for which can lead to false counts. If the success condition
             * does not hold, we redo the code emits trying to rule out false positives and only
             * fail if the success condition does not hold repeatedly.
             */

            codeDefault = getCode(graphDefault.method(), graphDefault, true, true, optionsDefault);
            Object resultDefault = codeDefault.executeVarargs(params);

            codeOptimized = getCode(graphOptimized.method(), graphOptimized, true, true, optionsOptimized);
            Object resultOptimized = codeOptimized.executeVarargs(params);

            assertTrue(String.format("Optimized code should behave identically! Result (default): %s | Result (optimized): %s", resultDefault, resultOptimized), resultDefault.equals(resultOptimized));
            if (checkCode(codeDefault, codeOptimized)) {
                return;
            }

            graphDefault = parseEager(method, AllowAssumptions.NO, optionsDefault);
            graphOptimized = parseEager(method, AllowAssumptions.NO, optionsOptimized);
        }
        fail(String.format("Optimized code should have fewer long jumps!\n\tDefault code: %s\n\tOptimized code: %s", byteArrayToHexArray(codeDefault.getCode()),
                        byteArrayToHexArray(codeOptimized.getCode())));
    }

    private static boolean checkCode(InstalledCode codeDefault, InstalledCode codeOptimized) {
        byte[] bytesDefault = codeDefault.getCode();
        byte[] bytesOptimized = codeOptimized.getCode();

        if (bytesDefault.length > bytesOptimized.length) {
            // code size reduction, so optimization must have worked
            return true;
        }

        return countLongJumpsHeuristically(bytesDefault) > countLongJumpsHeuristically(bytesOptimized);
    }

    private static int countLongJumpsHeuristically(byte[] code) {
        /*
         * Counts opcodes for jmp and jcc in a raw code byte array. If a non-opcode byte looks like
         * a jmp / jcc opcode, this would lead to false counts.
         */
        int longJumps = 0;
        for (int i = 0; i < code.length - 1; i++) {
            if (isLongJmp(code[i]) || isLongJcc(code[i], code[i + 1])) {
                longJumps++;
            }
        }
        return longJumps;
    }

    public static boolean isLongJmp(byte b) {
        return b == 0xE9;
    }

    public static boolean isLongJcc(byte b0, byte b1) {
        return b0 == 0x0F && (b1 & 0xF0) == 0x80;
    }

    private static String byteArrayToHexArray(byte[] code) {
        String[] hex = new String[code.length];

        for (int i = 0; i < code.length; i++) {
            hex[i] = byteToHex(code[i]);
        }

        return Arrays.toString(hex);
    }

    private static String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }
}
