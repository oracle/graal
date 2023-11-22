/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class PEAgnosticInlineInvokePlugin implements InlineInvokePlugin {
    private final EconomicSet<Invoke> invokeToTruffleCallNode = EconomicSet.create();
    private final List<Invoke> indirectInvokes = new ArrayList<>();
    private final PartialEvaluator partialEvaluator;
    private JavaConstant lastDirectCallNode;
    private boolean indirectCall;

    public PEAgnosticInlineInvokePlugin(PartialEvaluator partialEvaluator) {
        this.partialEvaluator = partialEvaluator;
    }

    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
        InlineInfo inlineInfo = partialEvaluator.asInlineInfo(original);
        if (original.equals(partialEvaluator.types.OptimizedCallTarget_callDirect)) {
            ValueNode arg0 = arguments[1];
            if (!arg0.isConstant()) {
                GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!"); // ExcludeFromJacocoGeneratedReport
            }
            lastDirectCallNode = (JavaConstant) arg0.asConstant();
            return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        }
        if (original.equals(partialEvaluator.types.OptimizedCallTarget_callIndirect)) {
            indirectCall = true;
        }
        return inlineInfo;
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod original, Invoke invoke) {
        if (original.equals(partialEvaluator.types.OptimizedCallTarget_callDirect)) {
            invokeToTruffleCallNode.add(invoke);
            lastDirectCallNode = null;
        } else if (lastDirectCallNode == null && indirectCall) {
            indirectCall = false;
            indirectInvokes.add(invoke);
        }
    }

    public EconomicSet<Invoke> getInvokeToTruffleCallNode() {
        return invokeToTruffleCallNode;
    }

    public List<Invoke> getIndirectInvokes() {
        return indirectInvokes;
    }
}
