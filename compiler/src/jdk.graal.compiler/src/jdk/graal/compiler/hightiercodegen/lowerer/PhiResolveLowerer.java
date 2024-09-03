/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.lowerer;

import jdk.graal.compiler.hightiercodegen.CodeGenTool;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;

/**
 * Resolves phi's on end node lowering. This component is responsible for scheduling phi-to-phi
 * assignments at control flow merges. If there is a situation like a=b,b=a, no valid scheduling
 * without tmp vars exists that satisfies this relation. Therefore, phi resolving introduces
 * additional variables during code gen.
 */
public class PhiResolveLowerer {
    protected final AbstractEndNode end;

    public PhiResolveLowerer(AbstractEndNode end) {
        this.end = end;
    }

    public MoveResolver<ValueNode, ValuePhiNode>.Schedule scheduleMoves(CodeGenTool codeGenTool) {
        AbstractMergeNode m = end.merge();
        MoveResolver<ValueNode, ValuePhiNode> moveResolver = new MoveResolver<>(m.valuePhis().snapshot());

        for (ValuePhiNode phi : m.valuePhis()) {
            if (codeGenTool.nodeLowerer().actualUsageCount(phi) > 0) {
                assert codeGenTool.getAllocatedVariable(phi) != null;
                ValueNode value = phi.valueAt(end);

                moveResolver.addMove(value, phi);
            }
        }

        return moveResolver.scheduleMoves();
    }

    public void lower(CodeGenTool codeGenTool) {
        MoveResolver<ValueNode, ValuePhiNode>.Schedule schedule = scheduleMoves(codeGenTool);

        String tmpName = null;

        for (var move : schedule.moves) {
            if (move.target == null) {
                // Move into the temp variable

                if (tmpName == null) {
                    // This is the first move into the temp variable, declare it first.
                    tmpName = "TEMP_" + codeGenTool.genUniqueID();
                    codeGenTool.genResolvedVarDeclPrefix(tmpName, move.source);
                } else {
                    codeGenTool.genResolvedVarAssignmentPrefix(tmpName);
                }

                codeGenTool.lowerValue(move.source);
                codeGenTool.genResolvedVarDeclPostfix("TMP Move Phi Schedule");
            } else {
                // Move into a phi node
                codeGenTool.lowerValue(move.target);
                codeGenTool.genAssignment();
                if (move.useTemporary) {
                    assert tmpName != null;
                    codeGenTool.genResolvedVarAccess(tmpName);
                } else {
                    codeGenTool.lowerValue(move.source);
                }
                codeGenTool.genResolvedVarDeclPostfix(null);
            }
        }
    }
}
