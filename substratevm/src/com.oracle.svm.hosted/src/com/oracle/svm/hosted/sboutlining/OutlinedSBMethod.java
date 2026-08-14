/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.sboutlining.OutlinedSBMethodHolder;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.hosted.sboutlining.concat.SubstrateSBConcatFactory;
import com.oracle.svm.hosted.sboutlining.concat.SubstrateStringConcatFactory;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A synthetic, non-bytecode method that implements one outlined string aggregation shape.
 *
 * <p>
 * Each instance represents a single {@link MethodType} and is created and cached by
 * {@link OutlinedSBMethodSupport}. The return type selects whether the generated graph creates a
 * {@link String}, {@link StringBuilder}, or {@link StringBuffer}. The parameter types describe the
 * values to aggregate and, for builder or buffer materialization, the initial capacity.
 *
 * <p>
 * {@link #buildGraph} uses a {@link HostedGraphKit} and the factories in the {@code concat}
 * package to build the method graph directly. Callers therefore invoke a regular static method
 * declared by {@link OutlinedSBMethodHolder}, while no bytecode or Java source needs to be
 * generated for the method.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class OutlinedSBMethod extends NonBytecodeMethod {

    public static final class Options {
        @Option(help = "Dump all graphs for outlined String, StringBuilder, and StringBuffer aggregations.")//
        public static final HostedOptionKey<Boolean> DumpOutlinedSBGraphs = new HostedOptionKey<>(false);
    }

    private final MethodType methodType;

    public OutlinedSBMethod(ResolvedJavaType declaringClass, Signature signature, ConstantPool constantPool, MethodType methodType) {
        super(uniqueName(methodType), true, declaringClass, signature, constantPool);
        this.methodType = methodType;
    }

    public MethodType getMethodType() {
        return methodType;
    }

    private static String uniqueName(MethodType methodType) {
        StringBuilder name = new StringBuilder(ClassUtil.getUnqualifiedName(methodType.returnType()));
        /*
         * Keep the synthetic name valid for every backend. In particular, WebAssembly symbolic
         * identifiers do not allow parentheses.
         */
        name.append("_");
        methodType.parameterList().forEach(clazz -> name.append(JavaKind.fromJavaClass(clazz).getTypeChar()));

        return name.toString();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, GraphProvider.Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);

        ValueNode returnNode;
        try {
            MethodHandle graphBuilderMH;
            if (methodType.returnType().equals(String.class)) {
                graphBuilderMH = new SubstrateStringConcatFactory(kit).makeConcat(methodType);
            } else {
                graphBuilderMH = new SubstrateSBConcatFactory(kit).makeConcat(methodType.dropParameterTypes(0, 1));
            }
            returnNode = (ValueNode) graphBuilderMH.invokeWithArguments(kit.getInitialArguments());
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere("failed building outlined SB method", t);
        }
        kit.createReturn(returnNode, returnNode.getStackKind());

        StructuredGraph graph = kit.finalizeGraph();
        if (Options.DumpOutlinedSBGraphs.getValue()) {
            debug.forceDump(graph, "outlined sb method");
        }

        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            // ensure generated graph is valid
            assert GraphOrder.assertSchedulableGraph(graph);
            assert graph.verify();
        }
        return graph;
    }
}
