/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class Narrow_byte05 extends JTTTest {

    static byte[] a = {(byte) 0xFF};

    public static boolean lowestByteEquals(int x, int y) {
        return ((byte) x) == ((byte) y);
    }

    public static boolean test(int i) {
        int v = a[i];
        return lowestByteEquals(v, -1);
    }

    @Test
    public void run0() {
        runTest("test", 0);
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        super.registerInvocationPlugins(invocationPlugins);
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                ValueNode xNarrowed = b.add(NarrowNode.create(x, 8, NodeView.DEFAULT));
                ValueNode yNarrowed = b.add(NarrowNode.create(y, 8, NodeView.DEFAULT));
                LogicNode cmp = b.add(IntegerEqualsNode.create(xNarrowed, yNarrowed, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(cmp, NodeView.DEFAULT));
                return true;
            }
        }, Narrow_byte05.class, "lowestByteEquals", int.class, int.class);
    }
}
