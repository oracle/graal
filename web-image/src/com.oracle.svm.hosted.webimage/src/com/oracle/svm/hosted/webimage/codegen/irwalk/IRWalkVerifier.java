/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.irwalk;

import java.util.List;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hightiercodegen.CodeGenTool;
import jdk.graal.compiler.hightiercodegen.reconstruction.ReconstructionData;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.common.JVMCIError;

public class IRWalkVerifier {
    /**
     * DEBUG only, verify every node has been visited only once.
     */
    public static final boolean BreakOnMultipleV = true;
    /**
     * DEBUG only, verify every node has been visited.
     */
    public static final boolean BreakOnNoV = true;
    /**
     * DEBUG only, check verifier during visits.
     */
    public static final boolean CheckDuringVisit = true;

    /**
     * the entire list of instructions of the method.
     */
    private final List<Node> instructions;
    /**
     * the visits that have already been made [one index per instruction], each instruction may only
     * be visited once, but must be visited once, so visits must be 1 for each insn after lowering.
     */
    private final int[] visits;

    public IRWalkVerifier(List<Node> instructions) {
        this.instructions = instructions;
        visits = new int[instructions.size()];
    }

    public void visitNode(Node n, CodeGenTool codeGenTool) {
        int index = instructions.indexOf(n);
        assert NumUtil.assertNonNegativeInt(index);
        visits[index]++;
        if (CheckDuringVisit) {
            if (BreakOnMultipleV) {
                if (visits[index] > 1) {
                    if (n instanceof MergeNode || codeGenTool.nodeLowerer().isIgnored(n)) {
                        return;
                    }
                    n.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, n.graph(), "Graph visited multiple times");
                    JVMCIError.shouldNotReachHere("Node " + n + " visited a second time [visit index:" + index + "]");
                }
            }
        }
    }

    public void verify(StructuredGraph g, CodeGenTool codeGenTool, ReconstructionData reconstructionData) {
        for (int i = 0; i < visits.length; i++) {
            if (codeGenTool.nodeLowerer().isIgnored(instructions.get(i))) {
                continue;
            }
            if (visits[i] == 0) {
                if (BreakOnNoV) {
                    g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "Graph not visited");
                    reconstructionData.debugDump(g.getDebug());
                    System.out.println(codeGenTool.getCodeBuffer());
                    throw GraalError.shouldNotReachHere("Node " + instructions.get(i) + " visited 0 times"); // ExcludeFromJacocoGeneratedReport
                }
            } else if (visits[i] > 1) {
                if (BreakOnMultipleV) {
                    g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "Graph not visited");
                    reconstructionData.debugDump(g.getDebug());
                    throw GraalError.shouldNotReachHere("Node " + instructions.get(i) + " visited " + visits[i] + " times"); // ExcludeFromJacocoGeneratedReport
                }
            }
        }
    }

}
