/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AddNode;
import org.junit.Assert;
import org.junit.Test;

public class AddNodeTest extends GraalCompilerTest {
    @Test
    public void checkTemplateAndName() {
        AddNode add = new AddNode(ConstantNode.forInt(30), ConstantNode.forInt(12));
        NodeClass<? extends Node> addClass = add.getNodeClass();
        Assert.assertEquals("+", addClass.shortName());
        Assert.assertEquals("Using short name as template", "+", addClass.getNameTemplate());
    }

    public int addNotLeftSnippet(int x) {
        return ~x + x;
    }

    public int addNotRightSnippet(int x) {
        return x + ~x;
    }

    @SuppressWarnings("unused")
    public int addNotReferenceSnippet(int x) {
        return -1;
    }

    @Test
    public void addNot() {
        testAgainstReference("addNotReferenceSnippet", "addNotLeftSnippet");
        testAgainstReference("addNotReferenceSnippet", "addNotRightSnippet");
        test("addNotLeftSnippet", 42);
        test("addNotLeftSnippet", Integer.MAX_VALUE);
        test("addNotLeftSnippet", Integer.MIN_VALUE);
    }

    public static int iteratedAddSnippet(int start, int addend) {
        int sum = start;

        sum += addend;
        sum += addend;
        sum += addend;
        sum += addend;
        sum += addend;
        sum += addend;
        sum += addend;
        sum += addend;

        return sum;
    }

    public static int iteratedAddLeftAssocSnippet(int start, int addend) {
        return ((((((((start + addend) + addend) + addend) + addend) + addend) + addend) + addend) + addend);
    }

    public static int iteratedAddRightAssocSnippet(int start, int addend) {
        return start + (addend + (addend + (addend + (addend + (addend + (addend + (addend + addend)))))));
    }

    public static int iteratedAddReferenceSnippet(int start, int addend) {
        return start + (addend << 3);
    }

    @Test
    public void iteratedAdd() {
        testAgainstReference("iteratedAddReferenceSnippet", "iteratedAddSnippet");
        testAgainstReference("iteratedAddReferenceSnippet", "iteratedAddLeftAssocSnippet");
        testAgainstReference("iteratedAddReferenceSnippet", "iteratedAddRightAssocSnippet");
    }

    private void testAgainstReference(String referenceSnippet, String testSnippet) {
        StructuredGraph referenceGraph = parseForCompile(getResolvedJavaMethod(referenceSnippet));
        StructuredGraph testGraph = parseForCompile(getResolvedJavaMethod(testSnippet));
        createCanonicalizerPhase().apply(testGraph, getDefaultHighTierContext());
        assertEquals(referenceGraph, testGraph, true, false);
    }
}
