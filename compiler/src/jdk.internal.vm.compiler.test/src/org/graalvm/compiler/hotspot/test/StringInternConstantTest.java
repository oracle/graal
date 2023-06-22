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
package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.junit.Assert;

/**
 * Tests constant folding of {@link String#intern()}.
 */
public class StringInternConstantTest extends GraalCompilerTest {

    private static final String A_CONSTANT_STRING = "a constant string";

    @Test
    public void test1() {
        ResolvedJavaMethod method = getResolvedJavaMethod("constantIntern");
        StructuredGraph graph = parseForCompile(method);

        FixedNode firstFixed = graph.start().next();
        Assert.assertThat(firstFixed, instanceOf(ReturnNode.class));

        ReturnNode ret = (ReturnNode) firstFixed;
        if (ret.result() instanceof ConstantNode) {
            String expected = A_CONSTANT_STRING.intern();
            Constant constant = ((ConstantNode) ret.result()).getValue();
            if (constant instanceof HotSpotObjectConstant) {
                String returnedString = ((HotSpotObjectConstant) constant).asObject(String.class);
                Assert.assertSame("result", expected, returnedString);
            } else {
                Assert.fail("expected HotSpotObjectConstant, got: " + constant.getClass());
            }
        } else {
            Assert.fail("result not constant: " + ret.result());
        }
    }

    public static String constantIntern() {
        return A_CONSTANT_STRING.intern();
    }
}
