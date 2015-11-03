/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.sparc;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;

import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.phases.Phase;

public class SPARCSubIntCompareCanonicalizationPhase extends Phase {
    public SPARCSubIntCompareCanonicalizationPhase() {

    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof CompareNode) {
                CompareNode enode = (CompareNode) n;
                min32(enode, enode.getX());
                min32(enode, enode.getY());
            }
        }
    }

    private static void min32(CompareNode enode, ValueNode v) {
        Stamp s = v.stamp();
        if (s instanceof IntegerStamp) {
            int bits = ((IntegerStamp) s).getBits();
            if (bits != 32 && bits != 64) {
                if (bits <= 32) {
                    bits = 32;
                } else {
                    bits = 64;
                }
                ValueNode replacement;
                if (v instanceof ConstantNode) {
                    JavaConstant newConst;
                    if (bits == 32) {
                        newConst = JavaConstant.forInt(v.asJavaConstant().asInt());
                    } else if (bits == 64) {
                        newConst = JavaConstant.forLong(v.asJavaConstant().asLong());
                    } else {
                        throw JVMCIError.shouldNotReachHere();
                    }
                    long mask = CodeUtil.mask(bits);
                    replacement = v.graph().addOrUnique(new ConstantNode(newConst, IntegerStamp.stampForMask(bits, newConst.asLong() & mask, newConst.asLong() & mask)));
                } else {
                    replacement = v.graph().addOrUnique(new SignExtendNode(v, bits));
                }
                v.replaceAtUsages(replacement, x -> x == enode);
            }
        }
    }
}
