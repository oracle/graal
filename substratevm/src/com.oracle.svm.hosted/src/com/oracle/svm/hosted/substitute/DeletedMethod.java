/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeletedMethod extends CustomSubstitutionMethod {

    public static final String NATIVE_MESSAGE = String.format(
                    "Native method. If you intend to use the Java Native Interface (JNI), specify %1$s+JNI and see also %1$sJNIConfigurationFiles=<path> (use %1$s+PrintFlags for details)",
                    SubstrateOptionsParser.HOSTED_OPTION_PREFIX);

    private final String message;
    private final AnnotationValue[] injectedAnnotations;

    public DeletedMethod(ResolvedJavaMethod original, Delete deleteAnnotation) {
        super(original);
        this.message = deleteAnnotation.value();
        this.injectedAnnotations = SubstrateAnnotationExtractor.prepareInjectedAnnotations(deleteAnnotation);
    }

    @Override
    public AnnotationValue[] getInjectedAnnotations() {
        return injectedAnnotations;
    }

    public static final Method reportErrorMethod = ReflectionUtil.lookupMethod(VMError.class, "unsupportedFeature", String.class);

    @Override
    public int getModifiers() {
        /*
         * We remove the synchonized modifier because our manually constructed graph does not need
         * to do synchronization (since it is reporting a fatal error anyway).
         */
        return original.getModifiers() & ~Modifier.SYNCHRONIZED;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        return buildGraph(debug, method, providers, message, purpose);
    }

    public static StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, String message, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method, purpose);
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder state = new FrameStateBuilder(null, method, graph);
        state.initializeForMethodStart(null, true, providers.getGraphBuilderPlugins());

        /*
         * A random, but unique and consistent, number for every invoke. This is necessary because
         * we, e.g., look up static analysis results by bci.
         */
        int bci = 0;
        graph.start().setStateAfter(state.create(bci++, graph.start()));

        String msg = AnnotationSubstitutionProcessor.deleteErrorMessage(method, message, false);
        ValueNode msgNode = ConstantNode.forConstant(providers.getConstantReflection().forString(msg), providers.getMetaAccess(), graph);
        ValueNode exceptionNode = kit.createInvokeWithExceptionAndUnwind(providers.getMetaAccess().lookupJavaMethod(reportErrorMethod), InvokeKind.Static, state, bci++, msgNode);
        kit.append(new UnwindNode(exceptionNode));

        return kit.finalizeGraph();
    }
}
