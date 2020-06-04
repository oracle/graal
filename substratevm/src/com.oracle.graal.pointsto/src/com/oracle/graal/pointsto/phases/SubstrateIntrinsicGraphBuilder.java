/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.IntrinsicGraphBuilder;

public class SubstrateIntrinsicGraphBuilder extends IntrinsicGraphBuilder {

    private int bci;

    public SubstrateIntrinsicGraphBuilder(OptionValues options, DebugContext debug, CoreProviders providers, Bytecode code) {
        super(options, debug, providers, code, -1, AllowAssumptions.NO);
        setStateAfter(getGraph().start());
    }

    @Override
    public void setStateAfter(StateSplit sideEffect) {
        List<ValueNode> values = new ArrayList<>(Arrays.asList(arguments));
        int stackSize = 0;

        if (returnValue != null) {
            values.add(returnValue);
            stackSize++;
            if (method.getSignature().getReturnKind().needsTwoSlots()) {
                values.add(null);
                stackSize++;
            }
        }

        FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, values, arguments.length, stackSize, false, false, null, null));
        sideEffect.setStateAfter(stateAfter);
        bci++;
    }

    @Override
    protected void setExceptionState(ExceptionObjectNode exceptionObject) {
        List<ValueNode> values = new ArrayList<>(Arrays.asList(arguments));
        values.add(exceptionObject);
        int stackSize = 1;

        FrameState stateAfter = getGraph().add(new FrameState(null, code, bci, values, arguments.length, stackSize, true, false, null, null));
        exceptionObject.setStateAfter(stateAfter);
        bci++;
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
        return plugin.isGeneratedFromFoldOrNodeIntrinsic();
    }
}
