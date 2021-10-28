/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class IntegerBoxEqualsTest extends GraalCompilerTest {

    class Cell {
        private final Integer value;

        Cell(Integer value) {
            this.value = value;
        }

        public String check(Integer i) {
            b: {
                if (GraalDirectives.injectBranchProbability(0.01, value == i)) {
                    break b;
                }
                if (value.equals(i)) {
                    break b;
                }
                return "nope";
            }
            return "";
        }
    }

    public static boolean cellGet(Cell cell, Integer value) {
        return cell.check(value).length() == 0;
    }

    public void cellTest() {
        final Integer value = 1911;
        final Cell cell = new Cell(value);
        ResolvedJavaMethod get = getResolvedJavaMethod(IntegerBoxEqualsTest.class, "cellGet");
        for (int i = 0; i < 2000; i++) {
            for (int k = 0; k < 20; k++) {
                cellGet(cell, k);
            }
            cellGet(cell, value);
        }
        test(get, null, cell, value);
        final int equalsCount = lastCompiledGraph.getNodes().filter(ObjectEqualsNode.class).count();
        final int ifCount = lastCompiledGraph.getNodes().filter(IfNode.class).count();
        if (!(equalsCount == 0 || ifCount > 1)) {
            lastCompiledGraph.getDebug().forceDump(lastCompiledGraph, "There must be no reference comparisons in the graph, or everything reachable in equals.");
        }
        assertTrue(equalsCount == 0 || ifCount > 1, "There must be no reference comparisons in the graph, or everything reachable in equals.");
    }

    @Test
    public void testBoxEqualsSimplification() {
        cellTest();
    }

}
