/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.Objects;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Static fields are represented as two arrays in the native image heap: one for Object fields and
 * one for all primitive fields. The byte-offset into these arrays is stored in
 * {@link SharedField#getLocation}.
 * <p>
 * Implementation notes: The arrays are created after static analysis, but before compilation. We
 * need to know how many static fields are reachable in order to compute the appropriate size for
 * the arrays, which is only available after static analysis.
 * 
 * When bytecode is parsed before static analysis, the arrays are not available yet. Therefore, the
 * accessor functions {@link #getStaticObjectFields()}} and {@link #getStaticPrimitiveFields()} are
 * intrinsified to a {@link StaticFieldBaseNode}, which is then during compilation lowered to the
 * constant arrays. This also solves memory graph problems in the Graal compiler: Direct
 * loads/stores using the arrays, for example via Unsafe or VarHandle, alias with static field
 * loads/stores that have dedicated {@link LocationIdentity}. If the arrays are already exposed in
 * the high-level optimization phases of Graal, the compiler would miss the alias since the location
 * identities for arrays are considered non-aliasing with location identities for fields. Replacing
 * the {@link StaticFieldBaseNode} with a {@link ConstantNode} only in the low tier of the compiler
 * solves this problem.
 */
public final class StaticFieldsSupport {

    @Platforms(Platform.HOSTED_ONLY.class) //
    private Object[] staticObjectFields;
    @Platforms(Platform.HOSTED_ONLY.class) //
    private byte[] staticPrimitiveFields;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected StaticFieldsSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setData(Object[] staticObjectFields, byte[] staticPrimitiveFields) {
        StaticFieldsSupport support = ImageSingletons.lookup(StaticFieldsSupport.class);
        support.staticObjectFields = Objects.requireNonNull(staticObjectFields);
        support.staticPrimitiveFields = Objects.requireNonNull(staticPrimitiveFields);
    }

    /* Intrinsified by the graph builder plugin below. */
    public static Object getStaticObjectFields() {
        Object[] result = ImageSingletons.lookup(StaticFieldsSupport.class).staticObjectFields;
        VMError.guarantee(result != null, "arrays that hold static fields are only available after static analysis");
        return result;
    }

    /* Intrinsified by the graph builder plugin below. */
    public static Object getStaticPrimitiveFields() {
        byte[] result = ImageSingletons.lookup(StaticFieldsSupport.class).staticPrimitiveFields;
        VMError.guarantee(result != null, "arrays that hold static fields are only available after static analysis");
        return result;
    }

    public static FloatingNode createStaticFieldBaseNode(boolean primitive) {
        return new StaticFieldBaseNode(primitive);
    }
}

@AutomaticFeature
final class StaticFieldsFeature implements GraalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(StaticFieldsSupport.class, new StaticFieldsSupport());
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        Registration r = new Registration(plugins.getInvocationPlugins(), StaticFieldsSupport.class);
        r.register0("getStaticObjectFields", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused) {
                b.addPush(JavaKind.Object, new StaticFieldBaseNode(false));
                return true;
            }
        });
        r.register0("getStaticPrimitiveFields", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused) {
                b.addPush(JavaKind.Object, new StaticFieldBaseNode(true));
                return true;
            }
        });
    }
}

@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
final class StaticFieldBaseNode extends FloatingNode implements Lowerable {
    public static final NodeClass<StaticFieldBaseNode> TYPE = NodeClass.create(StaticFieldBaseNode.class);

    private final boolean primitive;

    /**
     * We must not expose that the stamp will eventually be an array, to avoid memory graph
     * problems. See the comment on {@link StaticFieldsSupport}.
     */
    protected StaticFieldBaseNode(boolean primitive) {
        super(TYPE, StampFactory.objectNonNull());
        this.primitive = primitive;
    }

    /**
     * At first glance, this method looks like a circular dependency:
     * {@link StaticFieldsSupport#getStaticPrimitiveFields} is intrinsified to a
     * {@link StaticFieldBaseNode}, and {@link StaticFieldBaseNode} is lowered by calling
     * {@link StaticFieldsSupport#getStaticPrimitiveFields}. So why does this code work?
     * 
     * The intrinsification to the {@link StaticFieldBaseNode} is only effective for code executed
     * at image run time. So when executed during AOT compilation, {@link StaticFieldBaseNode#lower}
     * really invokes {@link StaticFieldsSupport#getStaticPrimitiveFields}, which returns the proper
     * result.
     * 
     * For an image that uses Graal as a JIT compiler, {@link StaticFieldBaseNode#lower} is
     * reachable at run time. But it is AOT compiled, and lowering during that AOT compilation again
     * invokes {@link StaticFieldsSupport#getStaticPrimitiveFields}.
     * 
     * So in summary, this code works because there is proper "bootstrapping" during AOT compilation
     * where the intrinsification is not applied.
     */
    @Override
    public void lower(LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            /*
             * Lowering to a ConstantNode must only happen after the memory graph has been built,
             * i.e., when the information that static fields are stored in an array is no longer
             * misleading alias analysis.
             */
            return;
        }

        JavaConstant constant = SubstrateObjectConstant.forObject(primitive ? StaticFieldsSupport.getStaticPrimitiveFields() : StaticFieldsSupport.getStaticObjectFields());
        assert constant.isNonNull();
        replaceAndDelete(ConstantNode.forConstant(constant, tool.getMetaAccess(), graph()));
    }
}
