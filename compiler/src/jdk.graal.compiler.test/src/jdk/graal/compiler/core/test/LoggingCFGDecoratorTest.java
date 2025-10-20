/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

public class LoggingCFGDecoratorTest extends GraalCompilerTest {

    public static int foo(int a) {
        int result = 0;
        if (GraalDirectives.injectBranchProbability(0.99, a == 0)) {
            result = 1;
        } else {
            result = 2;
        }
        GraalDirectives.controlFlowAnchor();
        return result;
    }

    @Test
    public void test01() {
        StructuredGraph g = parseEager("foo", StructuredGraph.AllowAssumptions.NO);
        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(g);
        ControlFlowGraph.RecursiveVisitor<Integer> visitor = new ControlFlowGraph.RecursiveVisitor<Integer>() {
            int number;

            @Override
            public Integer enter(HIRBlock b) {
                number++;
                // pushed elements
                return 1;
            }

            @Override
            public void exit(HIRBlock b, Integer value) {
                number -= value;
            }

            @Override
            public String toString() {
                return "TestIterator";
            }
        };
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream o = new PrintStream(baos);
        ControlFlowGraph.LoggingCFGDecorator<?> dec = new ControlFlowGraph.LoggingCFGDecorator<>(o, visitor, cfg);
        cfg.visitDominatorTreeDefault(dec);
        String result = baos.toString();
        Assert.assertTrue(assertStringContains(result, ExpectedOutput));
    }

    private static boolean assertStringContains(String set, String key) {
        if (!set.contains(key)) {
            throw new AssertionError(String.format("String %s does not contain %s", set, key));
        }
        return true;
    }

    private static final String ExpectedOutput = "B0 [dom null, post dom null]" + System.lineSeparator() +
                    "\tB1 [dom B0, post dom null]" + System.lineSeparator() +
                    "\tB2 [dom B0, post dom null]" + System.lineSeparator() +
                    "\tB3 [dom B0, post dom null]" + System.lineSeparator() +
                    "Enter block B0 for TestIterator" + System.lineSeparator() +
                    "\tEnter block B1 for TestIterator" + System.lineSeparator() +
                    "\tExit block B1 with value 1 for TestIterator" + System.lineSeparator() +
                    "\tEnter block B2 for TestIterator" + System.lineSeparator() +
                    "\tExit block B2 with value 1 for TestIterator" + System.lineSeparator() +
                    "\tEnter block B3 for TestIterator" + System.lineSeparator() +
                    "\tExit block B3 with value 1 for TestIterator" + System.lineSeparator() +
                    "Exit block B0 with value 1 for TestIterator";

}
