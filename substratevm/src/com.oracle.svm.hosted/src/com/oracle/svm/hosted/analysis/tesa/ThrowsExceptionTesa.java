/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.tesa;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.hosted.analysis.tesa.effect.BooleanEffect;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.java.NewInstanceWithExceptionNode;

/**
 * TESA instance that computes information about exceptions.
 */
public class ThrowsExceptionTesa extends BooleanTesa {
    @Override
    protected BooleanEffect computeInitialState(AnalysisMethod method, StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (!isSupportedNode(node)) {
                return anyEffect();
            }
            if (shouldSkipNode(node)) {
                continue;
            }
            if (node instanceof UnwindNode || node instanceof ThrowBytecodeExceptionNode || node instanceof NewInstanceWithExceptionNode) {
                return anyEffect();
            }
        }
        return noEffect();
    }

    @Override
    protected void optimizeInvoke(HostedUniverse universe, StructuredGraph graph, Invoke invoke, BooleanEffect targetState) {
        assert targetState.hasNoEffects() : "Only false in actionable: " + targetState;
        InvokeNode nonThrowingInvoke = switch (invoke) {
            case InvokeNode invokeNode -> invokeNode;
            case InvokeWithExceptionNode invokeWithExceptionNode -> invokeWithExceptionNode.replaceWithInvoke();
            default -> throw AnalysisError.shouldNotReachHere("Unexpected invoke type: " + invoke);
        };
        nonThrowingInvoke.setGuaranteedNotToThrow();
    }
}
