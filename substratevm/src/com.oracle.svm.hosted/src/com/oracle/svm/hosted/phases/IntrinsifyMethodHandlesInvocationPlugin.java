/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.phases.PointsToMethodHandlePlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.snippets.IntrinsificationPluginRegistry;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class IntrinsifyMethodHandlesInvocationPlugin extends PointsToMethodHandlePlugin {

    public static class IntrinsificationRegistry extends IntrinsificationPluginRegistry {
    }

    private final ParsingReason reason;
    private final HostedUniverse hUniverse;
    private final IntrinsificationRegistry intrinsificationRegistry;

    public IntrinsifyMethodHandlesInvocationPlugin(ParsingReason reason, SnippetReflectionProvider snippetReflection, HostedProviders providers, AnalysisUniverse aUniverse, HostedUniverse hUniverse) {
        super(providers, snippetReflection, aUniverse, new Providers(GraalAccess.getOriginalProviders()), new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()),
                        VMError::shouldNotReachHere);
        parsingProviders = parsingProviders.copyWith(new MethodHandlesMetaAccessExtensionProvider());
        this.reason = reason;
        this.hUniverse = hUniverse;

        if (reason == ParsingReason.PointsToAnalysis) {
            intrinsificationRegistry = new IntrinsificationRegistry();
            ImageSingletons.add(IntrinsificationRegistry.class, intrinsificationRegistry);
        } else {
            intrinsificationRegistry = ImageSingletons.lookup(IntrinsificationRegistry.class);
        }
    }

    @Override
    protected Object asObject(JavaConstant javaConstant) {
        return SubstrateObjectConstant.asObject(javaConstant);
    }

    @Override
    protected void appendWordTypeRewriting(GraphBuilderConfiguration.Plugins graphBuilderPlugins) {
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        ConstantReflectionProvider originalConstantReflection = GraalAccess.getOriginalProviders().getConstantReflection();
        WordOperationPlugin wordOperationPlugin = new WordOperationPlugin(originalSnippetReflection, originalConstantReflection,
                        new SubstrateWordTypes(parsingProviders.getMetaAccess(), FrameAccess.getWordKind()),
                        parsingProviders.getPlatformConfigurationProvider().getBarrierSet());
        appendWordOpPlugins(graphBuilderPlugins, wordOperationPlugin, new TrustedInterfaceTypePlugin());
    }

    @Override
    protected void afterTransplanter(GraphBuilderContext b) {
        if (reason.duringAnalysis()) {
            /*
             * Successfully intrinsified during analysis, remember that we can intrinsify when
             * parsing for compilation.
             */
            intrinsificationRegistry.add(b.getMethod(), b.bci(), Boolean.TRUE);
        }
    }

    class MethodHandlesMetaAccessExtensionProvider implements MetaAccessExtensionProvider {
        @Override
        public JavaKind getStorageKind(JavaType type) {
            throw VMError.shouldNotReachHere("storage kind information is only needed for optimization phases not used by the method handle intrinsification");
        }

        @Override
        public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
            if (hUniverse == null) {
                /*
                 * During static analysis, every type can be constant folded and the static analysis
                 * will see the real allocation.
                 */
                return true;
            } else {
                ResolvedJavaType convertedType = optionalLookup(type);
                return convertedType != null && ((HostedType) convertedType).isInstantiated();
            }
        }

        @Override
        public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public boolean canVirtualize(ResolvedJavaType instanceType) {
            return true;
        }
    }

    @SuppressWarnings("try")
    @Override
    protected boolean processInvokeWithMethodHandle(GraphBuilderContext b, Replacements replacements, ResolvedJavaMethod methodHandleMethod, ValueNode[] methodHandleArguments) {
        /*
         * When parsing for compilation, we must not intrinsify method handles that were not
         * intrinsified during analysis. Otherwise new code that was not seen as reachable by the
         * static analysis would be compiled.
         */
        if (!reason.duringAnalysis() && intrinsificationRegistry.get(b.getMethod(), b.bci()) != Boolean.TRUE) {
            return false;
        }
        return super.processInvokeWithMethodHandle(b, replacements, methodHandleMethod, methodHandleArguments);
    }

    @Override
    protected ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        ResolvedJavaMethod result = super.lookup(method);
        if (hUniverse != null) {
            result = hUniverse.lookup(result);
        }
        return result;
    }

    @Override
    protected ResolvedJavaField lookup(ResolvedJavaField field) {
        ResolvedJavaField result = super.lookup(field);
        if (hUniverse != null) {
            result = hUniverse.lookup(result);
        }
        return result;
    }

    @Override
    protected ResolvedJavaType lookup(ResolvedJavaType type) {
        ResolvedJavaType result = super.lookup(type);
        if (hUniverse != null) {
            result = hUniverse.lookup(result);
        }
        return result;
    }

    @Override
    protected ResolvedJavaType optionalLookup(ResolvedJavaType type) {
        ResolvedJavaType result = super.optionalLookup(type);
        if (result != null && hUniverse != null) {
            result = hUniverse.optionalLookup(result);
        }
        return result;
    }

    @Override
    protected ResolvedJavaType toOriginalWithResolve(ResolvedJavaType type) {
        if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrappedWithResolve();
        } else {
            return super.toOriginalWithResolve(type);
        }
    }

    @Override
    protected ResolvedJavaMethod toOriginal(ResolvedJavaMethod method) {
        if (method instanceof HostedMethod) {
            return ((HostedMethod) method).wrapped.wrapped;
        } else {
            return super.toOriginal(method);
        }
    }
}
