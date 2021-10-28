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
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.replacements.IntrinsicGraphBuilder;

import jdk.vm.ci.meta.JavaKind;

public class SubstrateIntrinsicGraphBuilder extends IntrinsicGraphBuilder {

    private int bci;

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
        List<ValueNode> values = new ArrayList<>(Arrays.asList(arguments));
        int stackSize = 0;

        if (returnVal != null) {
            values.add(returnVal);
            stackSize++;
            if (method.getSignature().getReturnKind().needsTwoSlots()) {
                values.add(null);
                stackSize++;
            }
        }

        FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, values, arguments.length, stackSize, false, false, null, null));
        bci++;
        return stateAfter;
    }

    @Override
    public FrameState getInvocationPluginReturnState(JavaKind returnKind, ValueNode retVal) {
        return getFrameState(retVal);
    }

    @Override
    protected void setExceptionState(StateSplit exceptionObject) {
        List<ValueNode> values = new ArrayList<>(Arrays.asList(arguments));
        values.add(exceptionObject.asNode());
        int stackSize = 1;

        FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, values, arguments.length, stackSize, true, false, null, null));
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
            List<ValueNode> values = new ArrayList<>(Arrays.asList(arguments));
            values.add(exceptionValue);
            int stackSize = 1;

            FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, values, arguments.length, stackSize, true, false, null, null));
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
