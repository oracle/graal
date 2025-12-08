/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.lang.invoke.MethodHandle;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;

/**
 * Regression test for host interop call partial evaluation (HostExecuteNode).
 */
@RunWith(Parameterized.class)
public class GR69684Test extends PartialEvaluationTest {

    @Parameter(0) public String methodName;

    @Parameters(name = "{0}")
    public static List<String> data() {
        return List.of("directMethod", "overloadedMethod");
    }

    @Before
    public void setup() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompilationFailureAction", "Throw").option("engine.BackgroundCompilation", "false").option(
                        "compiler.TreatPerformanceWarningsAsErrors", "all").build());
        getContext().initialize(ProxyLanguage.ID);
    }

    @HostAccess.Export
    public Object directMethod(@SuppressWarnings("unused") String a, final Object b) {
        return b;
    }

    @HostAccess.Export
    public Object overloadedMethod(@SuppressWarnings("unused") String a, final Object b) {
        return b;
    }

    @HostAccess.Export
    public Object overloadedMethod(@SuppressWarnings("unused") Object a, final Object b) {
        // not supposed to be called
        throw new AssertionError();
    }

    @Test
    public void test() {
        var rootNode = new RootNode(ProxyLanguage.get(null)) {
            @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    Object receiver = ProxyLanguage.LanguageContext.get(this).getEnv().asGuestValue(GR69684Test.this);
                    return interop.invokeMember(receiver, methodName, "a", "b");
                } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
        };

        StructuredGraph graph = partialEval(rootNode);

        // After PE, only a single invoke taking a constant MethodHandle is expected.
        var invokedMethods = graph.getNodes(MethodCallTargetNode.TYPE).stream().filter(methodCall -> {
            return !methodCall.arguments().stream().anyMatch(argument -> argument.isConstant() &&
                            getMetaAccess().lookupJavaType(MethodHandle.class).isAssignableFrom(
                                            argument.stamp(NodeView.DEFAULT).javaType(getMetaAccess())));
        }).map(MethodCallTargetNode::targetMethod).toList();
        Assert.assertEquals("Unexpected invokes: " + invokedMethods, 0, invokedMethods.size());
    }
}
