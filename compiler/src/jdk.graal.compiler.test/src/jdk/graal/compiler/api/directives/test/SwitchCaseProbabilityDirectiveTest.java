/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.api.directives.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchCaseProbabilityNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SwitchCaseProbabilityDirectiveTest extends GraalCompilerTest {
    /**
     * Called before a test is compiled.
     */
    @Override
    protected void before(ResolvedJavaMethod method) {
        // don't let -Xcomp pollute profile
        method.reprofile();
    }

    public static int switchProbabilitySnippet1(int x) {
        switch (x) {
            case 0:
                GraalDirectives.injectSwitchCaseProbability(0.5);
                return 1;
            case 1:
                GraalDirectives.injectSwitchCaseProbability(0.3);
                return 3;
            case 2:
                GraalDirectives.injectSwitchCaseProbability(0.1);
                return x * 3;
            default:
                GraalDirectives.injectSwitchCaseProbability(0.1);
                return x + 5;
        }
    }

    public static int keyHoleSwitchSnippet(int x) {
        switch (x) {
            case 3:
                GraalDirectives.injectSwitchCaseProbability(0.2);
                return 10;
            case 4:
                GraalDirectives.injectSwitchCaseProbability(0.2);
                return 20;
            case 6:
                GraalDirectives.injectSwitchCaseProbability(0.2);
                return 30;
            case 7:
                GraalDirectives.injectSwitchCaseProbability(0.2);
                return 40;
            default:
                GraalDirectives.injectSwitchCaseProbability(0.2);
                return 42;
        }
    }

    @Test
    public void testSwitch() {
        test("switchProbabilitySnippet1", 1);
        test("keyHoleSwitchSnippet", 4);
    }

    public static int missingProbability(int x) {
        switch (x) {
            case 1:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 10;
            case 2:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 20;
            case 3:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 3;
            case 4:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 15;
            default:
                /*
                 * No probability usage here, which is an error, even though the other branches'
                 * probability adds up to 1
                 */
                return 42;
        }
    }

    public static int incorrectTotalProbability(int x) {
        /*
         * Total probability across branches should add up to 1, and if it doesn't an error should
         * be thrown.
         */
        switch (x) {
            case 1:
                GraalDirectives.injectSwitchCaseProbability(0.20);
                return 10;
            case 2:
                GraalDirectives.injectSwitchCaseProbability(0.20);
                return 20;
            case 3:
                GraalDirectives.injectSwitchCaseProbability(0.20);
                return 3;
            default:
                GraalDirectives.injectSwitchCaseProbability(0.20);
                return 42;
        }
    }

    public static int zeroProbabilityDeoptimize(int x) {
        /*
         * Total probability across branches should add up to 1, and if it doesn't an error should
         * be thrown.
         */
        switch (x) {
            case 100:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 10;
            case 200:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 20;
            case 300:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 30;
            case 400:
                GraalDirectives.injectSwitchCaseProbability(0.25);
                return 40;
            default: {
                GraalDirectives.injectSwitchCaseProbability(0);
                GraalDirectives.deoptimize();
                throw GraalError.shouldNotReachHere("Deoptimize");
            }
        }
    }

    @Test(expected = GraalError.class)
    public void testMissingProbability() {
        OptionValues optionValues = new OptionValues(getInitialOptions(), DebugOptions.DumpOnError, false);
        test(optionValues, "missingProbability", 1);
    }

    @Test(expected = GraalError.class)
    public void testIncorrectTotalProbability() {
        OptionValues optionValues = new OptionValues(getInitialOptions(), DebugOptions.DumpOnError, false);
        test(optionValues, "incorrectTotalProbability", 3);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        NodeIterable<SwitchCaseProbabilityNode> switchProbabilityInjectionNodes = graph.getNodes().filter(SwitchCaseProbabilityNode.class);
        Assert.assertEquals("SwitchCaseProbabilityNode count", 0, switchProbabilityInjectionNodes.count());

        NodeIterable<IntegerSwitchNode> switchNodes = graph.getNodes().filter(IntegerSwitchNode.class);
        Assert.assertEquals("IntegerSwitchNode count", 1, switchNodes.count());
        IntegerSwitchNode switchNode = switchNodes.first();
        double defaultProbability = 0.1;
        for (int i = 0; i < switchNode.keyCount(); ++i) {
            double expectedProbability = Double.NaN;
            switch (switchNode.intKeyAt(i)) {
                case 0:
                    expectedProbability = 0.5;
                    break;
                case 1:
                    expectedProbability = 0.3;
                    break;
                case 2:
                    expectedProbability = 0.1;
                    break;
                case 3:
                    expectedProbability = 0.2;
                    break;
                case 4:
                    expectedProbability = 0.2;
                    break;
                case 5:
                    // Default probability should fill the hole and be divided in two
                    expectedProbability = 0.1;
                    break;
                case 6:
                    expectedProbability = 0.2;
                    break;
                case 7:
                    expectedProbability = 0.2;
                    break;
                case 100:
                case 200:
                case 300:
                case 400:
                    expectedProbability = 0.25;
                    defaultProbability = 0.0;
                    break;
                default:
                    GraalError.shouldNotReachHereUnexpectedValue(switchNode.intKeyAt(i));
            }
            Assert.assertEquals(expectedProbability, switchNode.keyProbability(i), 0.001);
        }
        Assert.assertEquals(defaultProbability, switchNode.defaultProbability(), 0.001);
    }

    @Test
    public void testZeroProbability() {
        test("zeroProbabilityDeoptimize", 200);
        test("zeroProbabilityDeoptimize", 99);
    }
}
