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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

public class PullThroughSwitchTest extends GraalCompilerTest {

    static int sideEffect;
    static int sideEffect1;
    static int sideEffect2;
    static int sideEffect3;

    public static int switchReducePattern(int a) {
        int result = 0;
        switch (a) {
            case 1:
                result = sideEffect;
                break;
            case 2:
                result = sideEffect;
                break;
            case 3:
                result = sideEffect;
                break;
            default:
                result = sideEffect;
                break;

        }
        return result;
    }

    @Test
    public void test01() {
        highTierSwitches = 0;
        test("switchReducePattern", 12);
        highTierSwitches = -1;
    }

    public static int switchNotReducePattern(int a) {
        int result = 0;
        switch (a) {
            case 1:
                result = sideEffect;
                break;
            case 2:
                result = sideEffect1;
                break;
            case 3:
                result = sideEffect2;
                break;
            default:
                result = sideEffect3;
                break;

        }
        return result;
    }

    @Test
    public void test02() {
        highTierSwitches = 1;
        test("switchNotReducePattern", 12);
        highTierSwitches = -1;
    }

    public static int switchReduceOnlyOneNodePattern(int a) {
        int result = 0;
        int result1 = 0;
        switch (a) {
            case 1:
                result = sideEffect;
                result1 = sideEffect1;
                break;
            case 2:
                result = sideEffect;
                result1 = sideEffect2;
                break;
            case 3:
                result = sideEffect;
                result1 = sideEffect3;
                break;
            default:
                result = sideEffect;
                result1 = sideEffect1;
                break;

        }
        return result + result1;
    }

    @Test
    public void test03() {
        highTierSwitches = 1;
        // we only deduplicated exactly 1 node
        fixedNodesBeforeSwitch = 1;
        test("switchReduceOnlyOneNodePattern", 12);
        highTierSwitches = -1;
        fixedNodesBeforeSwitch = -1;
    }

    public static int switchReduce3NodesPattern(int a) {
        int result = 0;
        int result1 = 0;
        int result2 = 0;
        int result3 = 0;
        switch (a) {
            case 1:
                result = sideEffect;
                result1 = sideEffect1;
                result2 = sideEffect2;
                result3 = sideEffect3;
                GraalDirectives.sideEffect(1);
                break;
            case 2:
                result = sideEffect;
                result1 = sideEffect1;
                result2 = sideEffect2;
                result3 = sideEffect3;
                GraalDirectives.sideEffect(2);
                break;
            case 3:
                result = sideEffect;
                result1 = sideEffect1;
                result2 = sideEffect2;
                result3 = sideEffect3;
                GraalDirectives.sideEffect(3);
                break;
            default:
                result = sideEffect;
                result1 = sideEffect1;
                result2 = sideEffect2;
                result3 = sideEffect3;
                GraalDirectives.sideEffect(4);
                break;

        }
        return result + result1 + result2 + result3;
    }

    @Test
    public void test04() {
        highTierSwitches = 1;
        // we only deduplicated exactly 1 node
        fixedNodesBeforeSwitch = 4;
        test("switchReduce3NodesPattern", 12);
        highTierSwitches = -1;
        fixedNodesBeforeSwitch = -1;
    }

    // default value is -1
    private int highTierSwitches = -1;

    // nodes before the switch until start, excluding start, only checked if >=0
    private int fixedNodesBeforeSwitch = -1;

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals("Must have that many switches left", graph.getNodes().filter(SwitchNode.class).count(), highTierSwitches);
        if (fixedNodesBeforeSwitch >= 0) {
            checkBeforeNodes: {
                SwitchNode sw = graph.getNodes().filter(SwitchNode.class).first();
                int fixedNodes = 0;
                for (FixedNode f : GraphUtil.predecessorIterable((FixedNode) sw.predecessor())) {
                    if (f == graph.start()) {
                        Assert.assertEquals("Must have exactly that many fixed nodes left before switch", fixedNodesBeforeSwitch, fixedNodes);
                        break checkBeforeNodes;
                    }
                    fixedNodes++;
                }
                throw GraalError.shouldNotReachHere("Must find start node, no other basic block before");
            }
        }
    }

}
