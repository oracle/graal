/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining.policy;

import com.oracle.graal.api.code.BailoutException;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.phases.common.inlining.info.InlineInfo;

import java.util.function.ToDoubleFunction;

import static com.oracle.graal.compiler.common.GraalOptions.MaximumDesiredSize;

public final class InlineEverythingPolicy implements InliningPolicy {

    public boolean continueInlining(StructuredGraph graph) {
        if (graph.getNodeCount() >= MaximumDesiredSize.getValue()) {
            throw new BailoutException("Inline all calls failed. The resulting graph is too large.");
        }
        return true;
    }

    public boolean isWorthInlining(ToDoubleFunction<FixedNode> probabilities, Replacements replacements, InlineInfo info, int inliningDepth, double probability, double relevance,
                    boolean fullyProcessed) {
        return true;
    }
}
