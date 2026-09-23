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
package jdk.graal.compiler.core.test.ea;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument.Field;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.phases.util.BytecodeHandlerStubHelper;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BytecodeHandlerReloadTest extends GraalCompilerTest {

    static final class State {
        final Object[] references;
        int mutable;

        State(Object[] references) {
            this.references = references;
        }
    }

    static final class VirtualState {
        final int value;

        VirtualState(int value) {
            this.value = value;
        }
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 1, arguments = {
                    @Argument(expand = ExpansionKind.MATERIALIZED, fields = {@Field(name = "references"), @Field(name = "mutable")}),
                    @Argument(expand = ExpansionKind.VIRTUAL)
    })
    public static int interpreter(State state, VirtualState virtualState) {
        return state.mutable + virtualState.value;
    }

    @BytecodeInterpreterHandler(0)
    public static int preserveHandler(State state, VirtualState virtualState) {
        return interpreter(state, virtualState);
    }

    @Test
    public void testPreserveAtBothExits() {
        checkExits("preserveHandler", true);
    }

    @Test
    public void testDefaultPreservesIncomingImmutableValues() {
        checkExits("preserveHandler", false);
    }

    private void checkExits(String handlerName, boolean refreshImmutableFields) {
        BytecodeInterpreterAnnotations.registerCompilerDirectives(getMetaAccess());
        ResolvedJavaMethod handler = getResolvedJavaMethod(handlerName);
        BytecodeHandlerConfig config = BytecodeHandlerConfig.getHandlerConfig(getResolvedJavaMethod("interpreter"), handler);
        GraphKit kit = new GraphKit(getDebugContext(), handler, getProviders(), getDefaultGraphBuilderPlugins(), CompilationIdentifier.INVALID_COMPILATION_ID, handlerName, false, false) {
        };
        ValueNode[][] exceptionArguments = new ValueNode[1][];
        StructuredGraph graph;
        if (refreshImmutableFields) {
            graph = BytecodeHandlerStubHelper.createStub(kit, handler, 0, false, null, null, config, handler,
                            (ignoredConfig, ignoredKit, arguments) -> exceptionArguments[0] = arguments, true);
        } else {
            graph = BytecodeHandlerStubHelper.createStub(kit, handler, 0, false, null, null, config, handler,
                            (ignoredConfig, ignoredKit, arguments) -> exceptionArguments[0] = arguments);
        }
        MultiReturnNode result = (MultiReturnNode) graph.getNodes(ReturnNode.TYPE).first().result();
        Assert.assertNotNull(exceptionArguments[0]);
        for (ArgumentInfo argument : config.getArgumentInfos()) {
            ValueNode normalValue = result.getAdditionalReturnResults().get(argument.index());
            ValueNode exceptionalValue = exceptionArguments[0][argument.index()];
            if (argument.isExpanded() && (!argument.isImmutable() || refreshImmutableFields)) {
                Assert.assertTrue(normalValue instanceof LoadFieldNode);
                Assert.assertTrue(exceptionalValue instanceof LoadFieldNode);
                Assert.assertNotSame(normalValue, exceptionalValue);
                if (argument.isImmutable()) {
                    if (argument.field().getJavaKind().isObject()) {
                        Assert.assertTrue(((AbstractObjectStamp) graph.getParameter(argument.index()).stamp(NodeView.DEFAULT)).nonNull());
                    }
                    Assert.assertEquals(graph.getParameter(argument.index()).stamp(NodeView.DEFAULT), normalValue.stamp(NodeView.DEFAULT));
                    Assert.assertEquals(graph.getParameter(argument.index()).stamp(NodeView.DEFAULT), exceptionalValue.stamp(NodeView.DEFAULT));
                }
            } else {
                Assert.assertSame(graph.getParameter(argument.index()), normalValue);
                Assert.assertSame(normalValue, exceptionalValue);
            }
        }
        Assert.assertEquals(refreshImmutableFields ? 1 : 0, graph.getNodes().filter(FieldAliasNode.class).filter(node -> ((FieldAliasNode) node).getLocationIdentity().isImmutable()).count());
    }
}
