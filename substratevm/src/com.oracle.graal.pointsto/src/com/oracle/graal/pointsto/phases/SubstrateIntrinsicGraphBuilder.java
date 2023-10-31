/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.phases;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.replacements.IntrinsicGraphBuilder;

import jdk.vm.ci.meta.JavaKind;

public class SubstrateIntrinsicGraphBuilder extends IntrinsicGraphBuilder {

    private int bci;

    @SuppressWarnings("this-escape")
    public SubstrateIntrinsicGraphBuilder(OptionValues options, DebugContext debug, CoreProviders providers, Bytecode code) {
        super(options, debug, providers, code, -1, AllowAssumptions.NO);
        setStateAfter(getGraph().start());
    }

    @Override
    public void setStateAfter(StateSplit sideEffect) {
        FrameState stateAfter = getFrameState(returnValue);
        sideEffect.setStateAfter(stateAfter);
    }

    private FrameState getFrameState(ValueNode returnVal) {
        ValueNode[] locals = arguments;
        JavaKind[] pushedSlotKinds = null;
        ValueNode[] pushedValues = null;
        ValueNode[] locks = ValueNode.EMPTY_ARRAY;
        ValueNode[] stack;

        if (returnVal != null) {
            if (method.getSignature().getReturnKind().needsTwoSlots()) {
                stack = new ValueNode[]{returnVal, null};
            } else {
                stack = new ValueNode[]{returnVal};
            }
        } else {
            stack = ValueNode.EMPTY_ARRAY;
        }

        FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, locals, stack, stack.length, pushedSlotKinds, pushedValues, locks, null, FrameState.StackState.BeforePop));
        bci++;
        return stateAfter;
    }

    @Override
    public FrameState getInvocationPluginReturnState(JavaKind retKind, ValueNode retVal) {
        return getFrameState(retVal);
    }

    @Override
    protected void setExceptionState(StateSplit exceptionObject) {
        ValueNode[] locals = arguments;
        ValueNode[] stack = {exceptionObject.asNode()};
        JavaKind[] pushedSlotKinds = null;
        ValueNode[] pushedValues = null;
        ValueNode[] locks = ValueNode.EMPTY_ARRAY;
        List<MonitorIdNode> monitorIds = null;
        FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, locals, stack, 1, pushedSlotKinds, pushedValues, locks, monitorIds, FrameState.StackState.Rethrow));
        exceptionObject.setStateAfter(stateAfter);
        bci++;
    }

    @Override
    protected void mergeUnwinds() {
        List<UnwindNode> unwinds = new ArrayList<>();
        for (Node node : getGraph().getNodes()) {
            if (node instanceof UnwindNode) {
                unwinds.add((UnwindNode) node);
            }
        }

        if (unwinds.size() > 1) {
            /*
             * Merging all ExceptionNodes into a MergeNode. All current UnwindNodes are eliminated.
             */
            MergeNode unwindMergeNode = getGraph().add(new MergeNode());
            ValueNode exceptionValue = InliningUtil.mergeUnwindExceptions(unwindMergeNode, unwinds);

            /* Creating new UnwindNode for the merged exceptions. */
            UnwindNode unwindReplacement = getGraph().add(new UnwindNode(exceptionValue));
            unwindMergeNode.setNext(unwindReplacement);

            /* Creating FrameState for new UnwindNode. */
            ValueNode[] locals = arguments;
            ValueNode[] stack = {exceptionValue};
            JavaKind[] pushedSlotKinds = null;
            ValueNode[] pushedValues = null;
            ValueNode[] locks = ValueNode.EMPTY_ARRAY;
            List<MonitorIdNode> monitorIds = null;

            FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, locals, stack, 1, pushedSlotKinds, pushedValues, locks, monitorIds, FrameState.StackState.Rethrow));
            unwindMergeNode.setStateAfter(stateAfter);
            bci++;
        }
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
        return plugin.isGeneratedFromFoldOrNodeIntrinsic();
    }

    @Override
    public boolean needsExplicitException() {
        /*
         * For AOT compilation, all exception edges need to be explicit. Currently, during runtime
         * JIT compilation, no graphs for intrinsics are used, so we do not need to distinguish
         * here.
         */
        return true;
    }
}
