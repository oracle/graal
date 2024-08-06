/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.replacements.InlineDuringParsingPlugin;
import jdk.graal.compiler.replacements.nodes.MacroNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test that MethodHandle invokes of invokes that have been replaced by a MacroNode but not inlined
 * are emitted correctly.
 */
public class MethodHandleMacroTest extends GraalCompilerTest {

    @NodeInfo
    static class TestMacroNode extends MacroNode {
        public static final NodeClass<TestMacroNode> TYPE = NodeClass.create(TestMacroNode.class);

        protected TestMacroNode(MacroParams p) {
            super(TYPE, p);
        }

        @Override
        public Invoke replaceWithInvoke() {
            Invoke invoke = super.replaceWithInvoke();
            // Disallow inlining to ensure an Invoke is emitted.
            invoke.setUseForInlining(false);
            return invoke;
        }
    }

    static class TestClass {
        @SuppressWarnings("static-method")
        public final int empty() {
            return 0;
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        super.registerInvocationPlugins(invocationPlugins);
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, TestClass.class);
        r.register(new InvocationPlugin("empty", InvocationPlugin.Receiver.class) {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
                b.addPush(JavaKind.Int, new TestMacroNode(MacroNode.MacroParams.of(b, CallTargetNode.InvokeKind.Special, targetMethod, receiver.get(true))));
                return true;
            }
        });
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        conf.getPlugins().appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        return conf;
    }

    static MethodHandle getHandle() {
        try {
            MethodHandle handle = MethodHandles.lookup().findVirtual(TestClass.class, "empty", MethodType.methodType(int.class));
            return handle.bindTo(new TestClass());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static final MethodHandle staticHandle = getHandle();

    @Override
    protected Object[] getArgumentToBind() {
        return new Object[]{staticHandle};
    }

    public int testSnippet(MethodHandle handle) throws Throwable {
        return ((int) handle.invokeExact());
    }

    @Test
    public void test() {
        test("testSnippet", getHandle());
    }

    @SuppressWarnings("unused")
    public int testStaticSnippet(MethodHandle handle) throws Throwable {
        return ((int) staticHandle.invokeExact());
    }

    @Test
    public void test2() {
        test("testStaticSnippet", getHandle());
    }
}
