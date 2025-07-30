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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.Collection;

import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.hosted.webimage.codegen.WebImageVariableAllocation;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmAddressNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmIsNonZeroNode;
import com.oracle.svm.hosted.webimage.wasm.phases.WasmSwitchPhase;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;

import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class WebImageWasmVariableAllocation extends WebImageVariableAllocation {

    @Override
    public Collection<SafetyPolicy> getSafeInliningPolicies(ValueNode node, CodeGenTool codeGenTool) {
        Collection<SafetyPolicy> policies = super.getSafeInliningPolicies(node, codeGenTool);

        // TODO GR-55249 Should be moved into the WasmLM-specific allocator once implemented
        if (codeGenTool instanceof WasmLMCodeGenTool lmCodeGenTool && lmCodeGenTool.isSpilled(node)) {
            policies.add(SafetyPolicy.Never);
        }

        if (node instanceof ReadExceptionObjectNode) {
            policies.add(SafetyPolicy.Always);
        }

        /*
         * Invokes and foreign calls generate call instructions that require some setup and thus can
         * neve be inlined
         */
        if (node instanceof Invoke || node instanceof ForeignCall) {
            policies.add(SafetyPolicy.Never);
        }

        IntegerSwitchNode switchUsage = node.usages().filter(IntegerSwitchNode.class).first();
        if (switchUsage != null && !WasmSwitchPhase.isSimplified(switchUsage)) {
            // The input to a degenerate switch may never be inlined, because it may be duplicated
            // during lowering.
            policies.add(SafetyPolicy.Never);
        }

        return policies;
    }

    @Override
    protected boolean shouldInline(ValueNode node, int numUsages, CodeGenTool c) {
        WasmCodeGenTool codeGenTool = (WasmCodeGenTool) c;
        ResolvedJavaMethod method = codeGenTool.method;
        if (node instanceof ParameterNode parameterNode && method.hasReceiver() && parameterNode.index() == 0 && !codeGenTool.compilationResult.getParamTypes()[0].equals(method.getDeclaringClass())) {
            /*
             * If this node represents the receiver object of an instance method and the declared
             * receiver type does not match the receiver type stored in the compilation result,
             * codegen will insert a downcast. In that case, it only makes sense to inline this node
             * if it only has one usage. In all other cases, creating a variable, which is assigned
             * the downcast object, uses less space.
             */
            return numUsages == 1;
        }

        if (node instanceof ReadExceptionObjectNode) {
            // The node just reads the exception variable and so can always be inlined.
            return true;
        }

        // In WASM it is almost always better to inline constants.
        if (node instanceof ConstantNode) {
            return true;
        }

        if (node instanceof WasmIsNonZeroNode) {
            return true;
        }

        // Compression nodes are no-ops
        if (node instanceof CompressionNode) {
            return true;
        }

        /*
         * WasmAddressNode are most often used for memory loads/stores. As such a constant offset
         * can be inlined into the memory instruction and the node is basically a proxy for the
         * base.
         */
        if (node instanceof WasmAddressNode addressNode && addressNode.hasConstantOffset()) {
            return true;
        }

        return super.shouldInline(node, numUsages, codeGenTool);
    }
}
