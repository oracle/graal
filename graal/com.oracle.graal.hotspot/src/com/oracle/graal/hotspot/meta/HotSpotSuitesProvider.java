/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.replacements.NodeIntrinsificationPhase.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderConfiguration.DebugInfoMode;
import com.oracle.graal.java.GraphBuilderPlugin.AnnotatedInvocationPlugin;
import com.oracle.graal.java.GraphBuilderPlugin.InlineInvokePlugin;
import com.oracle.graal.java.GraphBuilderPlugin.LoadFieldPlugin;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.DerivedOptionValue.OptionSupplier;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;

/**
 * HotSpot implementation of {@link SuitesProvider}.
 */
public class HotSpotSuitesProvider implements SuitesProvider {

    protected final DerivedOptionValue<Suites> defaultSuites;
    protected final PhaseSuite<HighTierContext> defaultGraphBuilderSuite;
    private final DerivedOptionValue<LIRSuites> defaultLIRSuites;
    protected final HotSpotGraalRuntimeProvider runtime;

    private class SuitesSupplier implements OptionSupplier<Suites> {

        private static final long serialVersionUID = -3444304453553320390L;

        public Suites get() {
            return createSuites();
        }

    }

    private class LIRSuitesSupplier implements OptionSupplier<LIRSuites> {

        private static final long serialVersionUID = -1558586374095874299L;

        public LIRSuites get() {
            return createLIRSuites();
        }

    }

    public HotSpotSuitesProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, Replacements replacements) {
        this.runtime = runtime;
        this.defaultGraphBuilderSuite = createGraphBuilderSuite(metaAccess, constantReflection, replacements);
        this.defaultSuites = new DerivedOptionValue<>(new SuitesSupplier());
        this.defaultLIRSuites = new DerivedOptionValue<>(new LIRSuitesSupplier());
    }

    public Suites getDefaultSuites() {
        return defaultSuites.getValue();
    }

    public PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        return defaultGraphBuilderSuite;
    }

    public Suites createSuites() {
        Suites ret = Suites.createDefaultSuites();

        if (ImmutableCode.getValue()) {
            // lowering introduces class constants, therefore it must be after lowering
            ret.getHighTier().appendPhase(new LoadJavaMirrorWithKlassPhase(runtime.getConfig().classMirrorOffset, runtime.getConfig().getOopEncoding()));
            if (VerifyPhases.getValue()) {
                ret.getHighTier().appendPhase(new AheadOfTimeVerificationPhase());
            }
        }

        ret.getMidTier().appendPhase(new WriteBarrierAdditionPhase(runtime.getConfig()));
        if (VerifyPhases.getValue()) {
            ret.getMidTier().appendPhase(new WriteBarrierVerificationPhase());
        }

        return ret;
    }

    NodeIntrinsificationPhase intrinsifier;

    NodeIntrinsificationPhase getIntrinsifier() {
        if (intrinsifier == null) {
            HotSpotProviders providers = runtime.getHostProviders();
            intrinsifier = new NodeIntrinsificationPhase(providers, providers.getSnippetReflection());
        }
        return intrinsifier;
    }

    MetaAccessProvider getMetaAccess() {
        return runtime.getHostProviders().getMetaAccess();
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, Replacements replacements) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault();
        config.setLoadFieldPlugin(new LoadFieldPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode receiver, ResolvedJavaField field) {
                if (InlineDuringParsing.getValue() || builder.parsingReplacement()) {
                    if (receiver.isConstant()) {
                        JavaConstant asJavaConstant = receiver.asJavaConstant();
                        return tryConstantFold(builder, metaAccess, constantReflection, field, asJavaConstant);
                    }
                }
                return false;
            }

            public boolean apply(GraphBuilderContext builder, ResolvedJavaField staticField) {
                return tryConstantFold(builder, metaAccess, constantReflection, staticField, null);
            }
        });
        config.setInlineInvokePlugin(new InlineInvokePlugin() {
            public ResolvedJavaMethod getInlinedMethod(GraphBuilderContext builder, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
                ResolvedJavaMethod subst = replacements.getMethodSubstitutionMethod(method);
                if (subst != null) {
                    // Forced inlining of intrinsics
                    return subst;
                }
                if (builder.parsingReplacement()) {
                    if (getIntrinsifier().getIntrinsic(method) != null) {
                        // @NodeIntrinsic methods are handled by the AnnotatedInvocationPlugin
                        // registered below
                        return null;
                    }
                    // Force inlining when parsing replacements
                    return method;
                } else {
                    assert getIntrinsifier().getIntrinsic(method) == null : String.format("@%s method %s must only be called from within a replacement%n%s", NodeIntrinsic.class.getSimpleName(),
                                    method.format("%h.%n"), builder);
                    if (InlineDuringParsing.getValue() && method.hasBytecodes() && method.getCode().length <= TrivialInliningSize.getValue() &&
                                    builder.getDepth() < InlineDuringParsingMaxDepth.getValue()) {
                        return method;
                    }
                }
                return null;
            }
        });
        config.setAnnotatedInvocationPlugin(new AnnotatedInvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ResolvedJavaMethod method, ValueNode[] args) {
                if (builder.parsingReplacement()) {
                    NodeIntrinsificationPhase intrins = getIntrinsifier();
                    NodeIntrinsic intrinsic = intrins.getIntrinsic(method);
                    if (intrinsic != null) {
                        Signature sig = method.getSignature();
                        Kind returnKind = sig.getReturnKind();
                        Stamp stamp = StampFactory.forKind(returnKind);
                        if (returnKind == Kind.Object) {
                            JavaType returnType = sig.getReturnType(method.getDeclaringClass());
                            if (returnType instanceof ResolvedJavaType) {
                                stamp = StampFactory.declared((ResolvedJavaType) returnType);
                            }
                        }

                        ValueNode res = intrins.createIntrinsicNode(Arrays.asList(args), stamp, method, builder.getGraph(), intrinsic);
                        res = builder.append(res);
                        if (res.getKind().getStackKind() != Kind.Void) {
                            builder.push(returnKind.getStackKind(), res);
                        }
                        return true;
                    } else if (intrins.isFoldable(method)) {
                        ResolvedJavaType[] parameterTypes = resolveJavaTypes(method.toParameterTypes(), method.getDeclaringClass());
                        JavaConstant constant = intrins.tryFold(Arrays.asList(args), parameterTypes, method);
                        if (!COULD_NOT_FOLD.equals(constant)) {
                            if (constant != null) {
                                // Replace the invoke with the result of the call
                                ConstantNode res = builder.append(ConstantNode.forConstant(constant, getMetaAccess()));
                                builder.push(res.getKind().getStackKind(), builder.append(res));
                            } else {
                                // This must be a void invoke
                                assert method.getSignature().getReturnKind() == Kind.Void;
                            }
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        suite.appendPhase(new GraphBuilderPhase(config));
        return suite;
    }

    /**
     * Modifies the {@link GraphBuilderConfiguration} to build extra
     * {@linkplain DebugInfoMode#Simple debug info} if the VM
     * {@linkplain CompilerToVM#shouldDebugNonSafepoints() requests} it.
     *
     * @param gbs the current graph builder suite
     * @return a possibly modified graph builder suite
     */
    public static PhaseSuite<HighTierContext> withSimpleDebugInfoIfRequested(PhaseSuite<HighTierContext> gbs) {
        if (HotSpotGraalRuntime.runtime().getCompilerToVM().shouldDebugNonSafepoints()) {
            PhaseSuite<HighTierContext> newGbs = gbs.copy();
            GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
            GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
            GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig.withDebugInfoMode(DebugInfoMode.Simple));
            newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
            return newGbs;
        }
        return gbs;
    }

    public LIRSuites getDefaultLIRSuites() {
        return defaultLIRSuites.getValue();
    }

    public LIRSuites createLIRSuites() {
        return Suites.createDefaultLIRSuites();
    }

}
