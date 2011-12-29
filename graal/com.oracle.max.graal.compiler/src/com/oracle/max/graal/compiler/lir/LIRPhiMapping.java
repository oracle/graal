/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.*;
import com.sun.cri.ci.*;

public class LIRPhiMapping {
    private final LIRBlock block;

    private CiValue[][] inputs;
    private CiValue[] results;

    public LIRPhiMapping(LIRBlock block, LIRGenerator gen) {
        this.block = block;

        assert block.firstNode() instanceof MergeNode : "phi functions are only present at control flow merges";
        MergeNode mergeNode = (MergeNode) block.firstNode();
        List<PhiNode> phis = mergeNode.phis().snapshot();

        for (int i = 0; i < phis.size(); i++) {
            PhiNode phi = phis.get(i);
            if (phi.type() == PhiType.Value) {
                gen.setResult(phi, gen.newVariable(phi.kind()));
            }
        }
    }

    public void fillInputs(LIRGenerator gen) {
        assert block.firstNode() instanceof MergeNode : "phi functions are only present at control flow merges";
        MergeNode mergeNode = (MergeNode) block.firstNode();
        List<PhiNode> phis = mergeNode.phis().snapshot();

        int numPhis = 0;
        for (int i = 0; i < phis.size(); i++) {
            if (phis.get(i).type() == PhiType.Value) {
                numPhis++;
            }
        }
        int numPreds = block.numberOfPreds();

        results = new CiValue[numPhis];
        inputs = new CiValue[numPreds][numPhis];

        int phiIdx = 0;
        for (int i = 0; i < phis.size(); i++) {
            PhiNode phi = phis.get(i);
            if (phi.type() == PhiType.Value) {
                results[phiIdx] = gen.operand(phi);
                for (int j = 0; j < numPreds; j++) {
                    assert j == mergeNode.phiPredecessorIndex((FixedNode) block.predAt(j).lastNode()) : "block predecessors and node predecessors must have same order";
                    inputs[j][phiIdx] = gen.operand(phi.valueAt(j));
                }
                phiIdx++;
            }
        }
        assert phiIdx == numPhis;
    }

    public CiValue[] results() {
        return results;
    }

    public CiValue[] inputs(LIRBlock pred) {
        assert pred.numberOfSux() == 1 && pred.suxAt(0) == block;
        return inputs[block.getPredecessors().indexOf(pred)];
    }

    public void forEachInput(LIRBlock pred, ValueProcedure proc) {
        CiValue[] predInputs = inputs(pred);
        for (int i = 0; i < predInputs.length; i++) {
            predInputs[i] = proc.doValue(predInputs[i]);
        }
    }

    public void forEachOutput(ValueProcedure proc) {
        for (int i = 0; i < results.length; i++) {
            results[i] = proc.doValue(results[i]);
        }
    }

    public void forEachInput(LIRBlock pred, PhiValueProcedure proc) {
        CiValue[] predInputs = inputs(pred);
        for (int i = 0; i < predInputs.length; i++) {
            proc.doValue(predInputs[i], results[i]);
        }
    }

    public interface PhiValueProcedure {
        void doValue(CiValue input, CiValue output);
    }

    @Override
    public String toString() {
        return "PhiMapping for " + block + ": " + Arrays.toString(results);
    }
}
