/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.UninterruptibleAnnotationUtils;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Central design note for SVM explicit allocation OOME exception edges.
 * <p>
 * A normal allocation node does not have an explicit graph edge for {@link OutOfMemoryError}. That
 * is sufficient for runtimes that can leave compiled code by deoptimizing or unwinding through
 * runtime support when allocation fails. SVM compiled image code cannot rely on that behavior for
 * allocations inside a Java handler that is supposed to catch the OOME, so such allocations must be
 * represented with allocation-with-exception nodes when the graph is built or materialized for an
 * OOME-protected context.
 * <p>
 * The repair is intentionally done while a graph is parsed or decoded for a concrete caller context,
 * before inlining removes the invoke boundary. If a decoded callee is inlined into a caller whose
 * invoke can catch OOME, the decoded callee must expose an unwind for allocation OOME so normal
 * inlining can connect that unwind to the caller's concrete exception successor. A later generic
 * compiler phase would have to reconstruct that handler instance after inlining from bytecode
 * metadata, frame states, or source positions, which is not reliable for duplicated inline scopes.
 * <p>
 * Supported protected regions are direct typed handlers for {@code OutOfMemoryError}, plus explicit
 * OOME contexts created by infrastructure that owns a concrete exception edge, such as
 * bytecode-handler stubs. Broader handlers such as {@code VirtualMachineError}, {@code Error},
 * {@code Throwable}, and bytecode catch-all/finally regions are deliberately not modelled as OOME
 * allocation handlers yet. Making those paths explicit exposed low-level allocation-restriction
 * issues, and {@code Throwable} is not reliably distinguishable from bytecode catch-all/finally
 * handlers through all parser metadata paths.
 * <p>
 * Allocation-restricted methods are also excluded. Low-level VM code can contain assertion/error
 * paths that are normally removed or ignored by the SVM restriction checkers, but the
 * allocation-with-exception shape can leave those paths visible as ordinary allocation control flow.
 * <p>
 * Repaired allocation nodes keep only the normal allocation {@code stateBefore}; they do not
 * reconstruct a parser-created post-allocation state. The OOME path carries its state on the
 * synthetic exception object, while the normal successor continues with the frame state that is
 * established by the next real state-bearing node.
 * <p>
 * Later phases that introduce new allocation nodes must make the same decision locally. For example,
 * enterprise string inlining only preserves OOME routing when a new materialization allocation can
 * share an existing local OOME exception boundary; it deliberately does not reconstruct handlers
 * from bytecode metadata after inlining.
 */
public final class OOMEExceptionEdgePolicy {

    /**
     * Explicit OOME edges are not supported in allocation-restricted methods yet. Low-level VM
     * code can contain assertion/error paths that are normally removed or ignored by the SVM
     * restriction checkers, but the allocation-with-exception shape can leave those paths visible as
     * ordinary allocation control flow.
     */
    public static boolean supportsOOMEExceptionEdges(ResolvedJavaMethod method) {
        return method == null || !(UninterruptibleAnnotationUtils.isUninterruptible(method) || mustNotAllocate(method));
    }

    private static boolean mustNotAllocate(ResolvedJavaMethod method) {
        return (method instanceof AnalysisMethod || method instanceof HostedMethod) && ImageSingletons.contains(RestrictHeapAccessCallees.class) &&
                        ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
    }
}
