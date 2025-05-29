/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.phases;

import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.meta.SharedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Adds safepoints to loops and at method ends.
 */
public class SubstrateSafepointInsertionPhase extends LoopSafepointInsertionPhase {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean needSafepointCheck(ResolvedJavaMethod method) {
        if (Uninterruptible.Utils.isUninterruptible(method)) {
            /* Uninterruptible methods must not have a safepoint inserted. */
            return false;
        }
        if (AnnotationAccess.isAnnotationPresent(method, CFunction.class) || AnnotationAccess.isAnnotationPresent(method, InvokeCFunctionPointer.class)) {
            /*
             * Methods transferring from Java to C have an implicit safepoint check as part of the
             * transition from C back to Java. So no explicit end-of-method safepoint check needs to
             * be inserted. This is a performance optimization, the annotated methods are not
             * uninterruptible unless the C function is marked as NO_TRANSITION.
             */
            return false;
        }
        return true;
    }

    /**
     * Determines if this (potentially special) method needs safepoint checks.
     */
    public static boolean needsSafepointCheck(StructuredGraph graph) {
        SharedMethod method = (SharedMethod) graph.method();
        return method.needSafepointCheck();
    }

    /**
     * Insert SVM specific safepoints at the method end if necessary.
     */
    public static void insertMethodEndSafepoints(StructuredGraph graph, MidTierContext context) {
        SharedMethod method = (SharedMethod) graph.method();
        if (!((SubstrateBackend) context.getTargetProvider()).safepointCheckedInEpilogue(method)) {
            /* Insert method-end safepoints. */
            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                SafepointNode safepointNode = graph.add(new SafepointNode());
                graph.addBeforeFixed(returnNode, safepointNode);
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        if (!needsSafepointCheck(graph)) {
            return;
        }
        insertMethodEndSafepoints(graph, context);

        /* Insert loop safepoints. */
        super.run(graph, context);
    }
}
