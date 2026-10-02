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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.MATERIALIZED;
import static jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.VIRTUAL;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument.Field;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ArbitraryValueNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.phases.util.BytecodeHandlerStubHelper;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BytecodeHandlerFieldValidityTest extends GraalCompilerTest {
    static class State {
        int first;
        int second;
        long cached;
        double floating;
        long unused;
        long ordinary;
        final long immutable = 42;
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = {
                    @Field(name = "first", templateVariable = 2), @Field(name = "second", templateVariable = 3),
                    @Field(name = "cached", validWhen = "second", valid = {1, 2}), @Field(name = "unused", validWhen = "first"),
                    @Field(name = "immutable", validWhen = "first", valid = {1}), @Field(name = "floating", validWhen = "second", valid = {2})
    }))
    public static void valid(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = {
                    @Field(name = "first", templateVariable = 2), @Field(name = "second", templateVariable = 3)
    }))
    public static void unrestricted(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = @Field(name = "cached", valid = 0)))
    public static void missingController(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = @Field(name = "cached", validWhen = "missing", valid = 0)))
    public static void unknownController(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = {
                    @Field(name = "first", templateVariable = 2), @Field(name = "cached", validWhen = "first", valid = 2)
    }))
    public static void outOfRange(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = {
                    @Field(name = "first", templateVariable = 2), @Field(name = "cached", validWhen = "first", valid = -1)
    }))
    public static void negative(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = {
                    @Field(name = "first", templateVariable = 2), @Field(name = "second", validWhen = "first", valid = 0)
    }))
    public static void wrongKind(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, fields = @Field(name = "first", templateVariable = 2, validWhen = "first", valid = 0)))
    public static void templateField(State state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = MATERIALIZED, fields = @Field(name = "cached", validWhen = "first", valid = 0)))
    public static void materialized(State state) {
    }

    private BytecodeHandlerConfig config(String name, boolean enabled) {
        BytecodeInterpreterAnnotations.registerCompilerDirectives(getMetaAccess());
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        return BytecodeHandlerConfig.getHandlerConfig(method, method, enabled);
    }

    @Test
    public void testIndependentTemplateValues() {
        BytecodeHandlerConfig config = config("valid", true);
        Assert.assertEquals(6, config.getTemplatesLength());
        for (int index = 0; index < 6; index++) {
            int remaining = index;
            int second = -1;
            int first = -1;
            for (ArgumentInfo argument : config.getAllArgumentInfos()) {
                if (argument.isTemplateVariable()) {
                    if (argument.field().getName().equals("first")) {
                        first = remaining % argument.templateVariants();
                    }
                    if (argument.field().getName().equals("second")) {
                        second = remaining % argument.templateVariants();
                    }
                    remaining /= argument.templateVariants();
                }
            }
            for (ArgumentInfo argument : config.getAllArgumentInfos()) {
                boolean expected = switch (argument.field().getName()) {
                    case "cached" -> second != 0;
                    case "floating" -> second == 2;
                    case "unused" -> false;
                    case "immutable" -> first == 1;
                    default -> true;
                };
                Assert.assertEquals(expected, config.isFieldValid(argument, index));
            }
        }
    }

    @Test
    public void testDisabledTemplateMode() {
        BytecodeHandlerConfig config = config("valid", false);
        Assert.assertEquals(1, config.getTemplatesLength());
        for (ArgumentInfo argument : config.getAllArgumentInfos()) {
            Assert.assertTrue(config.isFieldValid(argument, 0));
        }
    }

    @Test
    public void testConfigIdentity() {
        Assert.assertNotEquals(config("valid", true), config("unrestricted", true));
        Assert.assertEquals(config("valid", false), config("unrestricted", false));
    }

    @Test
    public void testStubFieldInitialization() {
        BytecodeHandlerConfig config = config("valid", true);
        ResolvedJavaMethod handler = getResolvedJavaMethod("valid");
        for (int index = 0; index < config.getTemplatesLength(); index++) {
            GraphKit kit = new GraphKit(getDebugContext(), handler, getProviders(), getDefaultGraphBuilderPlugins(), CompilationIdentifier.INVALID_COMPILATION_ID,
                            "valid", false, false) {
            };
            StructuredGraph graph = BytecodeHandlerStubHelper.createStub(kit, handler, 0, false, null, null, config, handler, index, null);
            CommitAllocationNode commit = graph.getNodes().filter(CommitAllocationNode.class).first();
            int fieldIndex = 0;
            for (ArgumentInfo argument : config.getAllArgumentInfos()) {
                ValueNode value = commit.getValues().get(fieldIndex++);
                if (!config.isFieldValid(argument, index)) {
                    Assert.assertTrue(value instanceof ArbitraryValueNode);
                    Assert.assertEquals(argument.type().getJavaKind(), value.getStackKind());
                    Assert.assertTrue(graph.getParameter(argument.index()).hasNoUsages());
                } else if (!argument.isTemplateVariable()) {
                    Assert.assertSame(graph.getParameter(argument.index()), value);
                }
            }
        }
    }

    @Test
    public void testInvalidConfigurations() {
        for (String name : new String[]{"missingController", "unknownController", "outOfRange", "negative", "wrongKind", "templateField", "materialized"}) {
            Assert.assertThrows(name, GraalError.class, () -> config(name, true));
        }
    }
}
