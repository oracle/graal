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
package com.oracle.max.graal.examples.opt;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.examples.intrinsics.*;
import com.oracle.max.graal.extensions.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class OptimizerImpl implements Optimizer {

    @Override
    public void optimize(RiRuntime runtime, Graph graph) {
        for (SafeAddNode safeAdd : graph.getNodes(SafeAddNode.class)) {
            if (!canOverflow(safeAdd)) {
                IntegerAdd add = new IntegerAdd(CiKind.Int, safeAdd.x(), safeAdd.y(), graph);
                safeAdd.replaceAndDelete(add);
            }
        }
    }

    private boolean canOverflow(SafeAddNode safeAdd) {
        if (safeAdd.y().isConstant() && safeAdd.y().asConstant().asLong() == 1) {
            if (safeAdd.x() instanceof Phi) {
                Phi phi = (Phi) safeAdd.x();
                if (phi.merge() instanceof LoopBegin && phi.valueAt(1) == safeAdd) {
                    LoopBegin loopBegin = (LoopBegin) phi.merge();
                    return canOverflow(phi, loopBegin);
                }
            }
        }
        return true;
    }

    private boolean canOverflow(Phi phi, LoopBegin loopBegin) {

        NodeBitMap nodes = LoopUtil.markUpCFG(loopBegin);
        NodeBitMap exits = LoopUtil.computeLoopExits(loopBegin, nodes);
        for (Node exit : exits) {
            TTY.println("exit: " + exit);
            Node pred = exit.predecessors().get(0);
            if (pred instanceof If) {
                If ifNode = (If) pred;
                if (ifNode.compare() instanceof Compare) {
                    Compare compare = (Compare) ifNode.compare();
                    Condition cond = compare.condition();
                    Value x = compare.x();
                    Value y = compare.y();
                    if (ifNode.trueSuccessor() == pred) {
                        cond = cond.negate();
                    }
                    if (cond == Condition.LT && x == phi) {
                        return false;
                    }
                    if (cond == Condition.GT && y == phi) {
                        return false;
                    }
                }

            }
        }
        TTY.println("can overflow");
        return true;
    }
}
