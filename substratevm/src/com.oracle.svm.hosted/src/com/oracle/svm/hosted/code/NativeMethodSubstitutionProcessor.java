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
package com.oracle.svm.hosted.code;

import java.lang.annotation.Annotation;

import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.word.Word.Operation;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Filtering substitution processor that passes on native methods that are not already handled by
 * some other mechanism ({@link CFunction}, {@link NodeIntrinsic}, an {@link InvocationPlugin},
 * etc.) to another {@link SubstitutionProcessor}, or a
 * {@link SubstitutionProcessor#chainUpInOrder(SubstitutionProcessor...) chain} of them.
 */
public class NativeMethodSubstitutionProcessor extends SubstitutionProcessor {
    private static final Class<?>[] FILTER_ANNOTATIONS = {NodeIntrinsic.class, Operation.class, CConstant.class};

    private final SubstitutionProcessor processor;
    private final Replacements replacements;

    public NativeMethodSubstitutionProcessor(SubstitutionProcessor processor, Replacements replacements) {
        this.processor = processor;
        this.replacements = replacements;
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (!method.isNative()) {
            return method;
        }
        if (method.getCodeSize() != 0 || method instanceof GraphProvider) {
            // Substituted native method that still has its native modifier set
            assert !(method instanceof WrappedJavaMethod) : "Must not see AnalysisMethod or HostedMethod here";
            return method;
        }
        for (Annotation annotation : method.getAnnotations()) {
            for (Class<?> c : FILTER_ANNOTATIONS) {
                if (annotation.annotationType() == c) {
                    return method;
                }
            }
            assert annotation.annotationType() != CFunction.class : "CFunction must have been handled by another SubstitutionProcessor";
        }
        boolean isHandledByPlugin = replacements.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method) != null;
        if (isHandledByPlugin) {
            return method;
        }
        return processor.lookup(method);
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        return processor.resolve(method);
    }
}
