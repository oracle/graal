/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.amd64;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotLIRGenerator.CompareMemoryCompressedOp;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

/**
 * Specialized code gen for comparison with compressed memory.
 */

public class AMD64HotSpotMemoryPeephole extends AMD64MemoryPeephole {
    AMD64HotSpotMemoryPeephole(AMD64NodeLIRBuilder gen) {
        super(gen);
    }

    @Override
    protected Kind getMemoryKind(Access access) {
        PlatformKind kind = gen.getLIRGeneratorTool().getPlatformKind(access.asNode().stamp());
        if (kind == NarrowOopStamp.NarrowOop) {
            return Kind.Int;
        } else {
            return (Kind) kind;
        }
    }

    @Override
    protected boolean emitCompareBranchMemory(ValueNode left, ValueNode right, Access access, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        if (HotSpotGraalRuntime.runtime().getConfig().useCompressedOops) {
            ValueNode other = selectOtherInput(left, right, access);
            Kind kind = getMemoryKind(access);

            if (other.isConstant() && kind == Kind.Object && access.isCompressible()) {
                ensureEvaluated(other);
                gen.append(new CompareMemoryCompressedOp(makeAddress(access), other.asConstant(), getState(access)));
                Condition finalCondition = right == access ? cond.mirror() : cond;
                gen.append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
                return true;
            }
        }

        return super.emitCompareBranchMemory(left, right, access, cond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability);
    }
}
