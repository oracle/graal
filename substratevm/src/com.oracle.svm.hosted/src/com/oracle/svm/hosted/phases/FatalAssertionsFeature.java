/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.FatalAssertions;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Code that must be allocation free cannot throw new {@link AssertionError}. We still want to print
 * as much information as possible, including a stack trace. Therefore we treat such assertion
 * failures as fatal errors that abort the VM and print a diagnostic output with stack traces.
 */
final class FatalAssertionsNodePlugin implements NodePlugin {

    private final MetaAccessProvider metaAccess;
    private final ResolvedJavaType assertionErrorType;
    private final HashMap<ResolvedJavaMethod, SubstrateForeignCallDescriptor> assertionConstructorReplacements;

    FatalAssertionsNodePlugin(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;

        assertionErrorType = metaAccess.lookupJavaType(AssertionError.class);
        assertionConstructorReplacements = new HashMap<>();
        for (Map.Entry<Executable, SubstrateForeignCallDescriptor> entry : FatalAssertions.FOREIGN_CALLS.entrySet()) {
            assertionConstructorReplacements.put(metaAccess.lookupJavaMethod(entry.getKey()), entry.getValue());
        }
    }

    /**
     * The singleton {@link AssertionError} to use when an assert fails in code that must not
     * allocate. Note that this instance is never actually thrown because assertions are reported as
     * fatal errors.
     */
    private static final AssertionError CACHED_ASSERTION_ERROR = new AssertionError();

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        if (type.equals(assertionErrorType) && !b.parsingIntrinsic() && methodMustNotAllocate(b)) {
            /*
             * We need to remove the allocation, because assertions in GC code must be allocation
             * free. The object is never used, but we cannot use null because then we get a
             * NullPointerException when calling the constructor. So we just use a cached
             * AssertionError object that already exists.
             */
            b.push(JavaKind.Object, ConstantNode.forConstant(SubstrateObjectConstant.forObject(CACHED_ASSERTION_ERROR), metaAccess, b.getGraph()));
            return true;
        }
        return false;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        SubstrateForeignCallDescriptor descriptor = assertionConstructorReplacements.get(method);
        if (descriptor != null && !b.parsingIntrinsic() && methodMustNotAllocate(b)) {
            b.add(new ForeignCallNode(descriptor, args));
            b.add(new DeadEndNode());
            return true;
        }

        return false;
    }

    private static boolean methodMustNotAllocate(GraphBuilderContext b) {
        return ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(b.getMethod());
    }
}

@AutomaticFeature
final class FatalAssertionsFeature implements GraalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        for (SubstrateForeignCallDescriptor descriptor : FatalAssertions.FOREIGN_CALLS.values()) {
            access.getBigBang().addRootMethod((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()));
        }
    }

    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection, SubstrateForeignCallsProvider foreignCalls, boolean hosted) {
        for (SubstrateForeignCallDescriptor descriptor : FatalAssertions.FOREIGN_CALLS.values()) {
            foreignCalls.register(providers, descriptor);
        }
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, boolean analysis, boolean hosted) {
        plugins.appendNodePlugin(new FatalAssertionsNodePlugin(providers.getMetaAccess()));
    }
}
