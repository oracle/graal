/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.runtimeCall;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.LoweringTool.LoweringStage;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.deopt.DeoptimizationRuntime;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.nodes.UnreachableNode;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public final class DeoptHostedSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static void deoptSnippet(@ConstantParameter DeoptimizationReason reason, @ConstantParameter Boolean mustNotAllocate, String message) {
        /*
         * The snippet cannot (yet) simplify a switch of an Enum, so we use an if-cascade here.
         * Because of the constant parameter, only one branch remains anyway.
         */
        if (reason == DeoptimizationReason.NullCheckException) {
            if (mustNotAllocate) {
                runtimeCall(ImplicitExceptions.THROW_CACHED_NULL_POINTER_EXCEPTION);
            } else {
                runtimeCall(ImplicitExceptions.THROW_NEW_NULL_POINTER_EXCEPTION);
            }
        } else if (reason == DeoptimizationReason.BoundsCheckException) {
            if (mustNotAllocate) {
                runtimeCall(ImplicitExceptions.THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION);
            } else {
                runtimeCall(ImplicitExceptions.THROW_NEW_OUT_OF_BOUNDS_EXCEPTION);
            }
        } else if (reason == DeoptimizationReason.ClassCastException) {
            if (mustNotAllocate) {
                runtimeCall(ImplicitExceptions.THROW_CACHED_CLASS_CAST_EXCEPTION);
            } else {
                runtimeCall(ImplicitExceptions.THROW_NEW_CLASS_CAST_EXCEPTION);
            }
        } else if (reason == DeoptimizationReason.ArrayStoreException) {
            if (mustNotAllocate) {
                runtimeCall(ImplicitExceptions.THROW_CACHED_ARRAY_STORE_EXCEPTION);
            } else {
                runtimeCall(ImplicitExceptions.THROW_NEW_ARRAY_STORE_EXCEPTION);
            }
        } else if (reason == DeoptimizationReason.ArithmeticException) {
            if (mustNotAllocate) {
                runtimeCall(ImplicitExceptions.THROW_CACHED_ARITHMETIC_EXCEPTION);
            } else {
                runtimeCall(ImplicitExceptions.THROW_NEW_ARITHMETIC_EXCEPTION);
            }
        } else if (reason == DeoptimizationReason.UnreachedCode ||
                        reason == DeoptimizationReason.TypeCheckedInliningViolated ||
                        reason == DeoptimizationReason.NotCompiledExceptionHandler ||
                        reason == DeoptimizationReason.Unresolved ||
                        reason == DeoptimizationReason.JavaSubroutineMismatch) {
            runtimeCall(SnippetRuntime.UNSUPPORTED_FEATURE, message);
        } else if (reason == DeoptimizationReason.TransferToInterpreter) {
            if (DeoptimizationSupport.enabled()) {
                /* We use this reason in TestDeoptimizeNode for deoptimization testing. */
                runtimeCall(DeoptimizationRuntime.DEOPTIMIZE, Deoptimizer.encodeDeoptActionAndReasonToLong(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter, 0), null);
            }
        }

        throw UnreachableNode.unreachable();
        /*
         * This is an illegal use of the snippet. Luckily, it is also a snippet that cannot be
         * processed (yet), so we get a compile-time error if this branch ever stays alive.
         */
        // throw new RuntimeException("MemorySnippet.deoptSnippet: Illegal snippet parameter");
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new DeoptHostedSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private DeoptHostedSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        lowerings.put(DeoptimizeNode.class, new DeoptimizeLowering());
    }

    private static boolean mustNotAllocate(ResolvedJavaMethod method) {
        return ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
    }

    public static final class AnalysisSpeculationReason implements SpeculationLog.SpeculationReason {
        private final String message;

        public AnalysisSpeculationReason(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class AnalysisSpeculation extends SpeculationLog.Speculation {
        public AnalysisSpeculation(AnalysisSpeculationReason reason) {
            super(reason);
        }
    }

    protected class DeoptimizeLowering implements NodeLoweringProvider<DeoptimizeNode> {

        private final SnippetInfo deopt = snippet(DeoptHostedSnippets.class, "deoptSnippet");

        @Override
        public void lower(DeoptimizeNode node, LoweringTool tool) {
            LoweringStage loweringStage = tool.getLoweringStage();
            if (loweringStage != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }

            String speculationMessage = null;
            SpeculationLog.Speculation speculation = node.getSpeculation();
            if (speculation instanceof AnalysisSpeculation) {
                AnalysisSpeculationReason reason = (AnalysisSpeculationReason) speculation.getReason();
                speculationMessage = reason.getMessage();
            }

            String message;
            switch (node.getReason()) {
                case NullCheckException:
                case BoundsCheckException:
                case ClassCastException:
                case ArrayStoreException:
                case ArithmeticException:
                    message = null;
                    break;
                case UnreachedCode:
                case TypeCheckedInliningViolated:
                case NotCompiledExceptionHandler:
                    message = "Code that was considered unreachable by closed-world analysis was reached.";
                    if (speculationMessage != null) {
                        message += ' ' + speculationMessage;
                    }
                    break;
                case Unresolved:
                    message = "Unresolved element found " + (node.getNodeSourcePosition() != null ? node.getNodeSourcePosition().toString() : "");
                    break;
                case JavaSubroutineMismatch:
                    message = "A JSR/RET structure that could not be simplified by the compiler was reached. The JSR bytecode is unused and deprecated since Java 6. Please recompile your application with a newer Java compiler.";
                    break;
                case TransferToInterpreter:
                    message = null;
                    if (!DeoptimizationSupport.enabled()) {
                        throw VMError.shouldNotReachHere("TransferToInterpreter is only intended for deoptimization testing when the DeoptimizationFeature is enabled");
                    }
                    break;

                default:
                    throw shouldNotReachHere("Unexpected reason: " + node.getReason());
            }

            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(deopt, graph.getGuardsStage(), loweringStage);
            args.addConst("reason", node.getReason());
            args.addConst("mustNotAllocate", mustNotAllocate(graph.method()));
            args.add("message", message);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
