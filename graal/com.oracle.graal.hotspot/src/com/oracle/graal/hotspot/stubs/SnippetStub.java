/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.graphbuilderconf.IntrinsicContext.CompilationContext.*;

import java.lang.reflect.*;

import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.debug.Debug.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;

/**
 * Base class for a stub defined by a snippet.
 */
public abstract class SnippetStub extends Stub implements Snippets {

    protected final ResolvedJavaMethod method;

    /**
     * Creates a new snippet stub.
     *
     * @param snippetMethodName name of the single {@link Snippet} annotated method in the class of
     *            this object
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(String snippetMethodName, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        this(null, snippetMethodName, providers, linkage);
    }

    /**
     * Creates a new snippet stub.
     *
     * @param snippetDeclaringClass this class in which the {@link Snippet} annotated method is
     *            declared. If {@code null}, this the class of this object is used.
     * @param snippetMethodName name of the single {@link Snippet} annotated method in
     *            {@code snippetDeclaringClass}
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(Class<? extends Snippets> snippetDeclaringClass, String snippetMethodName, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(providers, linkage);
        Method javaMethod = SnippetTemplate.AbstractTemplates.findMethod(snippetDeclaringClass == null ? getClass() : snippetDeclaringClass, snippetMethodName, null);
        this.method = providers.getMetaAccess().lookupJavaMethod(javaMethod);
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public static final ThreadLocal<StructuredGraph> SnippetGraphUnderConstruction = assertionsEnabled() ? new ThreadLocal<>() : null;

    @Override
    protected StructuredGraph getGraph() {
        Plugins defaultPlugins = providers.getGraphBuilderPlugins();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(defaultPlugins);
        plugins.prependParameterPlugin(new ConstantBindingParameterPlugin(makeConstArgs(), metaAccess, providers.getSnippetReflection()));
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);

        // Stubs cannot have optimistic assumptions since they have
        // to be valid for the entire run of the VM. Nor can they be
        // evolved or have breakpoints.
        final StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
        graph.disableInlinedMethodRecording();

        if (SnippetGraphUnderConstruction != null) {
            assert SnippetGraphUnderConstruction.get() == null : SnippetGraphUnderConstruction.get().toString() + " " + graph;
            SnippetGraphUnderConstruction.set(graph);
        }

        try {
            IntrinsicContext initialIntrinsicContext = new IntrinsicContext(method, method, INLINE_AFTER_PARSING);
            new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(), providers.getConstantReflection(), config, OptimisticOptimizations.NONE, initialIntrinsicContext).apply(graph);

        } finally {
            if (SnippetGraphUnderConstruction != null) {
                SnippetGraphUnderConstruction.set(null);
            }
        }

        graph.setGuardsStage(GuardsStage.FLOATING_GUARDS);
        try (Scope s = Debug.scope("LoweringStub", graph)) {
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, new PhaseContext(providers));
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        return graph;
    }

    protected boolean checkConstArg(int index, String expectedName) {
        assert method.getParameterAnnotation(ConstantParameter.class, index) != null : String.format("parameter %d of %s is expected to be constant", index, method.format("%H.%n(%p)"));
        LocalVariableTable lvt = method.getLocalVariableTable();
        if (lvt != null) {
            Local local = lvt.getLocal(index, 0);
            assert local != null;
            String actualName = local.getName();
            assert actualName.equals(expectedName) : String.format("parameter %d of %s is expected to be named %s, not %s", index, method.format("%H.%n(%p)"), expectedName, actualName);
        }
        return true;
    }

    protected Object[] makeConstArgs() {
        int count = method.getSignature().getParameterCount(false);
        Object[] args = new Object[count];
        for (int i = 0; i < args.length; i++) {
            if (method.getParameterAnnotation(ConstantParameter.class, i) != null) {
                args[i] = getConstantParameterValue(i, null);
            }
        }
        return args;
    }

    protected Object getConstantParameterValue(int index, String name) {
        throw new JVMCIError("%s must override getConstantParameterValue() to provide a value for parameter %d%s", getClass().getName(), index, name == null ? "" : " (" + name + ")");
    }

    @Override
    protected Object debugScopeContext() {
        return getInstalledCodeOwner();
    }

    @Override
    public ResolvedJavaMethod getInstalledCodeOwner() {
        return method;
    }

    @Override
    public String toString() {
        return "Stub<" + getInstalledCodeOwner().format("%h.%n") + ">";
    }
}
