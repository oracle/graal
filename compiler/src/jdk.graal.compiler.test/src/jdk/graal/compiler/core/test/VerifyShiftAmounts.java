/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/// Checks that constant distances of known shifts are read through `ShiftNode.getConstantShiftAmount()`.
///
/// This check follows `getY()` results through local aliases and casts in the parsed graph. It
/// cannot identify shifts hidden behind a generic `BinaryNode` receiver or a separate method.
public class VerifyShiftAmounts extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaType shiftType = context.getMetaAccess().lookupJavaType(ShiftNode.class);
        ResolvedJavaType binaryType = context.getMetaAccess().lookupJavaType(BinaryNode.class);
        ResolvedJavaType valueType = context.getMetaAccess().lookupJavaType(ValueNode.class);
        ResolvedJavaMethod caller = graph.method();
        if (caller.getDeclaringClass().equals(shiftType) && caller.getName().equals("getConstantShiftAmount")) {
            // The helper is the single place that reads and masks the raw constant distance.
            return;
        }
        for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = call.targetMethod();
            if (callee.getDeclaringClass().equals(valueType) && (callee.getName().equals("asJavaConstant") || callee.getName().equals("asConstant"))) {
                ValueNode receiver = GraphUtil.unproxify(call.arguments().get(0));
                ValueNode owner = operandOwner(receiver, binaryType);
                if (owner != null) {
                    ResolvedJavaType receiverType = StampTool.typeOrNull(owner);
                    if (receiverType != null && shiftType.isAssignableFrom(receiverType)) {
                        throw new VerificationError(call, "Use ShiftNode.getConstantShiftAmount() instead of extracting a raw constant from ShiftNode.getY(); " +
                                        "the distance may not yet have been masked by canonicalization.");
                    }
                }
            }
        }
    }

    /// Finds the binary node supplying its second operand, including an inlined getter.
    private static ValueNode operandOwner(ValueNode distance, ResolvedJavaType binaryType) {
        if (distance instanceof Invoke invoke && invoke.callTarget() instanceof MethodCallTargetNode getY) {
            ResolvedJavaMethod accessor = getY.targetMethod();
            if (accessor.getDeclaringClass().equals(binaryType) && accessor.getName().equals("getY")) {
                return getY.arguments().get(0);
            }
        } else if (distance instanceof LoadFieldNode load && load.field().getDeclaringClass().equals(binaryType) && load.field().getName().equals("y")) {
            return load.object();
        }
        return null;
    }
}
