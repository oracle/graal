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

import java.util.LinkedHashMap;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.phases.util.BytecodeHandlerCallSite;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerStubHelper;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.graal.compiler.truffle.hotspot.HotSpotOutlineBytecodeHandlerPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BytecodeHandlerExpandedTypeTest extends GraalCompilerTest {
    static class Base {
        int state;
    }

    static class Cached extends Base {
        long cached;
    }

    abstract static class AbstractCached extends Base {
    }

    static final class FurtherCached extends Cached {
        long extra;
    }

    static class Marker {
    }

    static class InheritedOnly extends Base {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = Marker.class))
    public static void emptyOwner(Object state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL))
    public static void emptyDefault(Marker state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = InheritedOnly.class))
    public static void inheritedOnly(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = Cached.class))
    public static void selected(Base state) {
    }

    @HostCompilerDirectives.BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument(expand = ExpansionKind.VIRTUAL, expandedType = Cached.class))
    public static void truffleSelected(Base state) {
    }

    @Test
    public void testTruffleConfigurationMatchesCompiler() {
        BytecodeInterpreterAnnotations.registerAnnotationTypes(getMetaAccess().lookupJavaType(HostCompilerDirectives.BytecodeInterpreterHandler.class),
                        getMetaAccess().lookupJavaType(HostCompilerDirectives.BytecodeInterpreterHandlerConfig.class),
                        getMetaAccess().lookupJavaType(HostCompilerDirectives.BytecodeInterpreterFetchOpcode.class));
        Assert.assertEquals(config("selected"), config("truffleSelected"));
    }

    @BytecodeInterpreterHandler(0)
    public static void handler(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL))
    public static void defaultType(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = MATERIALIZED, expandedType = Cached.class))
    public static void materialized(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expandedType = Cached.class))
    public static void unexpanded(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = String.class))
    public static void unrelated(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = AbstractCached.class))
    public static void abstractType(Base state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = Base[].class))
    public static void arrayType(Object state) {
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 0, arguments = @Argument(expand = VIRTUAL, expandedType = int.class))
    public static void primitiveType(Base state) {
    }

    private BytecodeHandlerConfig config(String name) {
        BytecodeInterpreterAnnotations.registerCompilerDirectives(getMetaAccess());
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        return BytecodeHandlerConfig.getHandlerConfig(method, method, false);
    }

    @Test
    public void testRejectEmptyOwners() {
        for (String name : new String[]{"emptyOwner", "emptyDefault"}) {
            GraalError error = Assert.assertThrows(GraalError.class, () -> config(name));
            Assert.assertTrue(error.getMessage().contains("must have at least one instance field"));
        }
        Assert.assertEquals(1, config("inheritedOnly").getCalleeParameterInfos().size());
    }

    @Test
    public void testHotSpotIgnoresExpandedType() {
        config("selected");
        BytecodeHandlerCallSite callsite = new HotSpotOutlineBytecodeHandlerPhase() {
            BytecodeHandlerCallSite createCallSite() {
                return getBytecodeHandlerCallSite(getResolvedJavaMethod("selected"), 0, getResolvedJavaMethod("handler"), getMetaAccess());
            }
        }.createCallSite();
        Assert.assertEquals(1, callsite.getCalleeParameterInfos().size());
        Assert.assertEquals(getMetaAccess().lookupJavaType(Base.class), callsite.getCalleeParameterInfos().getFirst().ownerType());
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        ParameterNode owner = graph.addOrUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.objectNonNull(TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(Base.class))))));
        ReturnNode end = graph.add(new ReturnNode(null));
        graph.start().setNext(end);
        Assert.assertEquals(1, callsite.createCallerArguments(new ValueNode[]{owner}, end, Function.identity(), Function.identity()).length);
    }

    @Test
    public void testInheritedLayoutAndReconstruction() {
        BytecodeHandlerConfig config = config("selected");
        Assert.assertEquals(2, config.getCalleeParameterInfos().size());
        for (var field : config.getAllArgumentInfos()) {
            Assert.assertEquals(getMetaAccess().lookupJavaType(Cached.class), field.ownerType());
        }
        Assert.assertEquals(1, config("defaultType").getCalleeParameterInfos().size());
        ResolvedJavaMethod method = getResolvedJavaMethod("handler");
        GraphKit kit = new GraphKit(getDebugContext(), method, getProviders(), getDefaultGraphBuilderPlugins(), CompilationIdentifier.INVALID_COMPILATION_ID,
                        "selected", false, false) {
        };
        StructuredGraph graph = BytecodeHandlerStubHelper.createStub(kit, method, 0, false, null, null, config, method, 0, null);
        CommitAllocationNode commit = graph.getNodes().filter(CommitAllocationNode.class).first();
        Assert.assertEquals(getMetaAccess().lookupJavaType(Cached.class), commit.getVirtualObjects().getFirst().type());
        Assert.assertEquals(2, commit.getValues().size());
    }

    @Test
    public void testCallerExactType() {
        config("selected");
        checkCaller(Cached.class, true, true);
        checkCaller(Base.class, true, false);
        checkCaller(FurtherCached.class, true, false);
        checkCaller(Cached.class, false, false);
    }

    private void checkCaller(Class<?> type, boolean exact, boolean accepted) {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        TypeReference reference = exact ? TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(type))
                        : TypeReference.createTrustedWithoutAssumptions(getMetaAccess().lookupJavaType(type));
        ParameterNode owner = graph.addOrUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.objectNonNull(reference))));
        ReturnNode end = graph.add(new ReturnNode(null));
        graph.start().setNext(end);
        BytecodeHandlerCallSite site = new BytecodeHandlerCallSite(getResolvedJavaMethod("selected"), 0, getResolvedJavaMethod("handler"), false);
        Runnable expand = () -> Assert.assertEquals(2, site.createCallerArguments(new ValueNode[]{owner}, end, f -> f, t -> t).length);
        if (accepted) {
            expand.run();
        } else {
            Assert.assertThrows(GraalError.class, expand::run);
        }
    }

    @Test
    public void testInvalidConfigurations() {
        for (String name : new String[]{"materialized", "unexpanded", "unrelated", "abstractType", "arrayType", "primitiveType"}) {
            Assert.assertThrows(name, GraalError.class, () -> config(name));
        }
    }

    @Test
    public void testAnnotationWithoutExpandedType() {
        config("defaultType");
        AnnotationValue argument = BytecodeInterpreterAnnotations.getBytecodeInterpreterHandlerConfig(getResolvedJavaMethod("defaultType")).getList("arguments", AnnotationValue.class).getFirst();
        var elements = new LinkedHashMap<>(argument.getElements());
        elements.remove("expandedType");
        AnnotationValue legacy = new AnnotationValue(argument.getAnnotationType(), elements);
        var declaredType = getMetaAccess().lookupJavaType(Base.class);
        Assert.assertNull(BytecodeHandlerConfig.getExplicitExpandedType(legacy));
        Assert.assertEquals(declaredType, BytecodeHandlerConfig.getExpandedType(legacy, declaredType, Function.identity()));
    }
}
