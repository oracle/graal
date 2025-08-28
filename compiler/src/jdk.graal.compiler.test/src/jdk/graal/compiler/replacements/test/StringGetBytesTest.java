/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class StringGetBytesTest extends MethodSubstitutionTest {

    private static final String BAD_STRING = "\u8020\000\000\020";

    @Test
    public void getBytesNonConst() {
        test("getBytesNonConstSnippet", BAD_STRING);
    }

    public static int getBytesNonConstSnippet(String s) {
        byte[] arr = s.getBytes(StandardCharsets.ISO_8859_1);
        return arr[2]; // expected 0
    }

    @Test
    public void getBytesConst() {
        test("getBytesConstSnippet");
    }

    public static int getBytesConstSnippet() {
        String s = BAD_STRING;
        byte[] arr = s.getBytes(StandardCharsets.ISO_8859_1);
        return arr[2]; // expected 0
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof EncodeArrayNode) {
                return;
            }
        }
        Assert.fail("intrinsic not found in graph!");
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        String className = method.getDeclaringClass().getUnqualifiedName();
        if ((className.equals("String") && !method.getName().equals("implEncodeISOArray")) || className.equals("StringCoding")) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }
}
