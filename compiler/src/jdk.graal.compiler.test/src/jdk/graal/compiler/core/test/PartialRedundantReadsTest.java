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
package jdk.graal.compiler.core.test;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;

public class PartialRedundantReadsTest extends GraalCompilerTest {

    static class A {
        int a = 0;

        A foo() {
            return this;
        }
    }

    static A staticField = null;

    /**
     * Assert that we should not split the read on `staticField` at the last return statement,
     * because it will introduce a re-read in the control flow graph, or result in null check
     * elimination based on a non-null pi with a guarded phi to be invalid. It is possible to split
     * at the two foo() invocations (optimal schedule). We currently assert that the split is
     * totally rejected.
     */
    public static int snippet0(boolean a, boolean b) {
        A r = staticField;
        A r1;

        if (GraalDirectives.injectBranchProbability(0.1d, a)) {
            if (GraalDirectives.injectBranchProbability(0.5D, r == null)) {
                return 'a';
            } else {
                GraalDirectives.controlFlowAnchor();
                // the following invocation is for constructing a PiNode with non-null stamp
                r1 = r.foo();
            }
        } else {
            if (GraalDirectives.injectBranchProbability(0.1D, b)) {
                if (GraalDirectives.injectBranchProbability(0.5D, r == null)) {
                    return 'b';
                } else {
                    GraalDirectives.controlFlowAnchor();
                    // the following invocation is for constructing a PiNode with non-null stamp
                    r1 = r.foo();
                }
            } else {
                return 'c';
            }
        }
        GraalDirectives.controlFlowAnchor();
        // we should not duplicate the read on staticField without a subsequent null check here
        return r1.a;
    }

    @Test
    public void testSnippet0() throws NoSuchFieldException {
        Field field = PartialRedundantReadsTest.class.getDeclaredField("staticField");
        FieldLocationIdentity staticFieldLocation = new FieldLocationIdentity(getMetaAccess().lookupJavaField(field));
        StructuredGraph graph = getFinalGraph("snippet0");
        Assert.assertEquals(1, graph.getNodes().filter(n -> n instanceof ReadNode && staticFieldLocation.equals(((ReadNode) n).getLocationIdentity())).count());
    }

    /**
     * Assert that we should not split the read on `staticField` at the second `r == null` check,
     * because it will introduce re-read in the control flow graph.
     */
    public static int snippet1(boolean a, boolean b) {
        A r = staticField;

        if (GraalDirectives.injectBranchProbability(0.1d, a)) {
            if (GraalDirectives.injectBranchProbability(0.5D, r == null)) {
                return 'a';
            }
        }

        if (GraalDirectives.injectBranchProbability(0.1D, b)) {
            if (GraalDirectives.injectBranchProbability(0.5D, r == null)) {
                return 'b';
            }
        }

        GraalDirectives.controlFlowAnchor();
        return 'c';
    }

    @Test
    public void testSnippet1() throws NoSuchFieldException {
        Field field = PartialRedundantReadsTest.class.getDeclaredField("staticField");
        FieldLocationIdentity staticFieldLocation = new FieldLocationIdentity(getMetaAccess().lookupJavaField(field));
        StructuredGraph graph = getFinalGraph("snippet1");
        Assert.assertEquals(1, graph.getNodes().filter(n -> n instanceof ReadNode && staticFieldLocation.equals(((ReadNode) n).getLocationIdentity())).count());
    }
}
