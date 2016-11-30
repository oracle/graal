/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements.test;

import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import jdk.vm.ci.meta.JavaConstant;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.junit.Assert;

/**
 * Tests constant folding of {@link String#hashCode()}.
 */
public class StringHashConstantTest extends GraalCompilerTest {

    private ValueNode asConstant(StructuredGraph graph, String str) {
        return ConstantNode.forConstant(getSnippetReflection().forObject(str), getMetaAccess(), graph);
    }

    @Test
    public void test1() {
        ResolvedJavaMethod method = getResolvedJavaMethod("constantHashCode");
        StructuredGraph graph = parseForCompile(method);

        String s = "some string";
        int expected = s.hashCode();
        graph.getParameter(0).replaceAndDelete(asConstant(graph, s));

        compile(method, graph);

        FixedNode firstFixed = graph.start().next();
        Assert.assertThat(firstFixed, instanceOf(ReturnNode.class));

        ReturnNode ret = (ReturnNode) firstFixed;
        JavaConstant result = ret.result().asJavaConstant();
        if (result == null) {
            Assert.fail("result not constant: " + ret.result());
        } else {
            Assert.assertEquals("result", expected, result.asInt());
        }
    }

    public static int constantHashCode(String value) {
        return value.hashCode();
    }
}
