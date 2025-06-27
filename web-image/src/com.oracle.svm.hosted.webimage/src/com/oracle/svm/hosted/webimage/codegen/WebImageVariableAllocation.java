/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.variables.VariableAllocation;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

public class WebImageVariableAllocation extends VariableAllocation {

    /**
     * Threshold above which integer constants should be inlined.
     */
    private static final long LOWER_INLINING_THRESHOLD = -10;
    /**
     * Threshold below which integer constants should be inlined.
     */
    private static final long UPPER_INLINING_THRESHOLD = 100;

    @Override
    public Collection<SafetyPolicy> getSafeInliningPolicies(ValueNode node, CodeGenTool codeGenTool) {
        Set<SafetyPolicy> policies = new HashSet<>();
        if (node.inputs().filter(PhiNode.class).isNotEmpty()) {
            /*
             * Technically, operations on phis can be safely moved within the same block (as long as
             * they're not inlined into the phi resolution at end nodes). However, this cannot be
             * modeled here because even if this node is not moved there directly, it could be
             * inlined into a node that is in turn inlined into the phi resolution. Because of that
             * we over-approximate and only allow inlining in blocks that don't jump to a merge and
             * thus don't schedule phi moves.
             */
            policies.add(SafetyPolicy.InBlockNoMerge);
        }

        if (node instanceof StateSplit) {
            // Nodes with side-effects are only safe to inline with a single usage.
            policies.add(SafetyPolicy.SingleUsage);
        }

        if (node instanceof AbstractNewArrayNode) {
            // contains complex initialization code
            policies.add(SafetyPolicy.Never);
        } else if (node instanceof BoxNode) {
            // contains complex initialization code
            policies.add(SafetyPolicy.Never);
        } else if (node instanceof JSBody) {
            // contains complex initialization code
            policies.add(SafetyPolicy.Never);
        } else if (node instanceof PhiNode) {
            // Phi nodes must always be variables and can never be inlined.
            policies.add(SafetyPolicy.Never);
        } else if (node instanceof FloatingNode) {
            policies.add(SafetyPolicy.Always);
        } else if (node instanceof FixedWithNextNode) {
            policies.add(SafetyPolicy.SingleUsageAtNext);
        } else if (node instanceof FixedNode) {
            // Moving a fixed node would result in reordering of fixed nodes.
            policies.add(SafetyPolicy.Never);
        } else {
            policies.add(SafetyPolicy.SingleUsage);
        }

        return policies;
    }

    @Override
    protected boolean shouldInline(ValueNode node, int numUsages, CodeGenTool codeGenTool) {
        if (node instanceof ParameterNode) {
            return true;
        } else if (node instanceof ValueProxyNode) {
            return true;
        } else if (node instanceof PiNode) {
            /*
             * If the original node is inlineable, so is the current PiNode.
             *
             * If the original node is not inlineable, then it's associated with a variable,
             * therefore the current PiNode is inlineable.
             *
             */
            return true;
        } else if (node instanceof ConstantNode) {
            Constant value = ((ConstantNode) node).getValue();

            if (value.isDefaultForKind()) {
                return true;
            } else if (value instanceof PrimitiveConstant) {
                PrimitiveConstant primitiveConstant = (PrimitiveConstant) value;
                JavaKind kind = primitiveConstant.getJavaKind();

                if (kind == JavaKind.Boolean) {
                    return true;
                } else if (kind.isNumericInteger()) {
                    long val = primitiveConstant.asLong();

                    if (LOWER_INLINING_THRESHOLD < val && val < UPPER_INLINING_THRESHOLD) {
                        return true;
                    }
                }
            }
        }

        return numUsages == 1;
    }
}
