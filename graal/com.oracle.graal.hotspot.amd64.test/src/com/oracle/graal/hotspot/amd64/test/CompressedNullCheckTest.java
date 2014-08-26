/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64.test;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Ensures that frame omission works in cases where it is expected to.
 */
public class CompressedNullCheckTest extends GraalCompilerTest {

    private static final class Container {
        Integer i = new Integer(1);
    }

    public static void testSnippet(Container c) {
        c.i.intValue();
    }

    @Test
    public void test() {
        test("testSnippet", new Container());
    }

    @Override
    protected boolean checkMidTierGraph(StructuredGraph graph) {
        int count = 0;
        for (IsNullNode isNull : graph.getNodes().filter(IsNullNode.class).snapshot()) {
            ValueNode value = isNull.getValue();
            if (value instanceof CompressionNode) {
                count++;
                isNull.replaceFirstInput(value, ((CompressionNode) value).getValue());
            }
        }
        Assert.assertEquals("graph should contain exactly one IsNullNode", 1, count);
        return super.checkMidTierGraph(graph);
    }
}
