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
package com.oracle.graal.nodes.test;

import java.util.function.Function;

import org.junit.Test;

import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ShortCircuitOrNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ShortCircuitOrNodeTest extends GraalCompilerTest {
    static boolean shortCircuitOr(boolean b1, boolean b2) {
        return b1 || b2;
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(plugins.getInvocationPlugins(), ShortCircuitOrNodeTest.class);
        r.register2("shortCircuitOr", boolean.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode b1, ValueNode b2) {
                LogicNode x = b.add(new IntegerEqualsNode(b1, b.add(ConstantNode.forInt(1))));
                LogicNode y = b.add(new IntegerEqualsNode(b2, b.add(ConstantNode.forInt(1))));
                ShortCircuitOrNode compare = b.add(new ShortCircuitOrNode(x, false, y, false, 0.5));
                b.addPush(JavaKind.Boolean, new ConditionalNode(compare, b.add(ConstantNode.forBoolean(true)), b.add(ConstantNode.forBoolean(false))));
                return true;
            }
        });

        return plugins;
    }

    public static int testSharedConditionSnippet(Object o) {
        boolean b2 = o != null;
        boolean b1 = o instanceof Function;
        if (b1) {
            if (shortCircuitOr(b1, b2)) {
                return 4;
            } else {
                return 3;
            }
        }
        return 1;
    }

    @Test
    public void testSharedCondition() {
        test("testSharedConditionSnippet", "String");
    }

}
