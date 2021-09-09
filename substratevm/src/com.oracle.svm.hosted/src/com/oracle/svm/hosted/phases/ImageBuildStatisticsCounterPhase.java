/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.phases.Phase;

import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.util.ImageBuildStatistics.CheckCountLocation;

import static com.oracle.svm.util.ImageBuildStatistics.counters;

public class ImageBuildStatisticsCounterPhase extends Phase {

    private final CheckCountLocation location;

    public ImageBuildStatisticsCounterPhase(CheckCountLocation location) {
        this.location = location;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof BytecodeExceptionNode || node instanceof ThrowBytecodeExceptionNode) {
                assert node.isAlive() : "ImageBuildStatisticsCounterPhase must be run after proper canonicalization to get the right numbers. Found not alive node: " + node;
                BytecodeExceptionNode.BytecodeExceptionKind bytecodeExceptionKind = node instanceof BytecodeExceptionNode ? ((BytecodeExceptionNode) node).getExceptionKind()
                                : ((ThrowBytecodeExceptionNode) node).getExceptionKind();
                counters().incByteCodeException(bytecodeExceptionKind, location);
            }
        }
    }
}
