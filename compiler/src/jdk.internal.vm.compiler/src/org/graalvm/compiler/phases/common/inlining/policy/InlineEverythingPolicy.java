/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.inlining.policy;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation;

public class InlineEverythingPolicy implements InliningPolicy {

    @Override
    public boolean continueInlining(StructuredGraph graph) {
        if (InliningUtil.getNodeCount(graph) >= MaximumDesiredSize.getValue(graph.getOptions())) {
            throw new PermanentBailoutException("Inline all calls failed. The resulting graph is too large.");
        }
        return true;
    }

    @Override
    public Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, InlineInfo calleeInfo, int inliningDepth, boolean fullyProcessed) {
        boolean isTracing = GraalOptions.TraceInlining.getValue(calleeInfo.graph().getOptions()) || calleeInfo.graph().getDebug().hasCompilationListener();
        return Decision.YES.withReason(isTracing, "inline everything");
    }
}
