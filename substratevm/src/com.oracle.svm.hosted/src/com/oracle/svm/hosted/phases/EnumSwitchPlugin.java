/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.svm.hosted.analysis.NativeImageStaticAnalysisEngine;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.snippets.IntrinsificationPluginRegistry;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The Eclipse Java compiler generates methods that lazily initializes tables for Enum switches,
 * using a known {@link #METHOD_NAME_PREFIX method name prefix}. The first invocation fills the
 * table, subsequent invocations reuse the table. We call the method during image generation, so
 * that the table gets built, and intrinsify the result of the invocation. This ensures that Enum
 * switches are allocation-free at run time. All classes involved must be safe for initialization at
 * image build time.
 */
final class EnumSwitchPlugin implements NodePlugin {

    private static final String METHOD_NAME_PREFIX = "$SWITCH_TABLE$";

    private final SnippetReflectionProvider snippetReflection;
    private final ParsingReason reason;

    EnumSwitchPlugin(SnippetReflectionProvider snippetReflection, ParsingReason reason) {
        this.snippetReflection = snippetReflection;
        this.reason = reason;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!method.getName().startsWith(METHOD_NAME_PREFIX) || !method.isStatic() || method.getSignature().getParameterCount(false) != 0) {
            return false;
        }

        if (reason == ParsingReason.PointsToAnalysis) {
            if (!method.getDeclaringClass().isInitialized()) {
                /*
                 * Declaring class is initialized at run time. Even if the enum itself is
                 * initialized at image build time, we cannot invoke the switch-table method because
                 * it is declared in the class that contains the switch.
                 */
                return false;
            }

            /*
             * There is no easy link from the method to the enum that it is actually used for. But
             * we need to ensure that invoking the method does not trigger any class initialization.
             * Parsing the method is the easiest way to find out what the method is going to do. If
             * any class initialization happens inside method, we must not invoke it. Note that we
             * do not check for transitive callees, because we trust that the Eclipse compiler only
             * emits calls that end up in the same class or in the JDK.
             */
            AnalysisMethod aMethod = (AnalysisMethod) method;
            EnumSwitchFeature feature = ImageSingletons.lookup(EnumSwitchFeature.class);
            aMethod.ensureGraphParsed(feature.analysis);
            Boolean methodSafeForExecution = feature.methodsSafeForExecution.get(aMethod);
            assert methodSafeForExecution != null : "after-parsing hook not executed for method " + aMethod.format("%H.%n(%p)");
            if (!methodSafeForExecution.booleanValue()) {
                return false;

            }
            try {
                Method switchTableMethod = ReflectionUtil.lookupMethod(aMethod.getDeclaringClass().getJavaClass(), method.getName());
                Object switchTable = switchTableMethod.invoke(null);
                if (switchTable instanceof int[]) {
                    ImageSingletons.lookup(ReflectionPlugins.ReflectionPluginRegistry.class).add(b.getMethod(), b.bci(), switchTable);
                }
            } catch (ReflectiveOperationException ex) {
                throw GraalError.shouldNotReachHere(ex);
            }
        }

        Object switchTable = ImageSingletons.lookup(ReflectionPlugins.ReflectionPluginRegistry.class).get(b.getMethod(), b.bci());
        if (switchTable != null) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReflection.forObject(switchTable), 1, true, b.getMetaAccess()));
            return true;
        }
        return false;
    }
}

final class EnumSwitchPluginRegistry extends IntrinsificationPluginRegistry {
}

@AutomaticFeature
final class EnumSwitchFeature implements GraalFeature {

    NativeImageStaticAnalysisEngine analysis;

    final ConcurrentMap<AnalysisMethod, Boolean> methodsSafeForExecution = new ConcurrentHashMap<>();

    @Override
    public void duringSetup(DuringSetupAccess a) {
        ImageSingletons.add(EnumSwitchPluginRegistry.class, new EnumSwitchPluginRegistry());
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        analysis = access.getStaticAnalysisEngine();
        access.getHostVM().addMethodAfterParsingHook(this::onMethodParsed);
    }

    private void onMethodParsed(AnalysisMethod method, StructuredGraph graph) {
        boolean methodSafeForExecution = graph.getNodes().filter(node -> node instanceof EnsureClassInitializedNode).isEmpty();

        Boolean existingValue = methodsSafeForExecution.put(method, methodSafeForExecution);
        assert existingValue == null : "Method parsed twice: " + method.format("%H.%n(%p)");
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        analysis = null;
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        plugins.appendNodePlugin(new EnumSwitchPlugin(providers.getSnippetReflection(), reason));
    }
}
