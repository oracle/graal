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

package com.oracle.svm.hosted.webimage.js;

import static jdk.graal.compiler.nodeinfo.InputType.State;

import java.util.function.Function;

import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Non-throwing counterpart of {@link JSBodyWithExceptionNode}.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN, sizeRationale = "Is replaced with JS code in JavaScriptBody and JS annotations")
public final class JSBodyNode extends FixedWithNextNode implements JSBody {
    public static final NodeClass<JSBodyNode> TYPE = NodeClass.create(JSBodyNode.class);

    @Input private NodeInputList<ValueNode> arguments;
    @OptionalInput(State) FrameState stateAfter;

    private final JSBody.JSCode jsCode;
    private final ResolvedJavaMethod m;

    private final boolean declaresJSResources;
    private final Function<CodeGenTool, String> codeSupplier;

    @SuppressWarnings("this-escape")
    public JSBodyNode(JSBody.JSCode jsCode, ResolvedJavaMethod m, ValueNode[] arguments, Stamp returnStamp, boolean declaresJSResources,
                    Function<CodeGenTool, String> codeSupplier) {
        super(TYPE, returnStamp);

        assert jsCode != null;

        this.jsCode = jsCode;
        this.m = m;
        this.arguments = new NodeInputList<>(this, arguments);
        this.declaresJSResources = declaresJSResources;
        this.codeSupplier = codeSupplier;
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public JSBody.JSCode getJsCode() {
        return jsCode;
    }

    @Override
    public String getJSCodeAsString(CodeGenTool codeGenTool) {
        return codeSupplier.apply(codeGenTool);
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return m;
    }

    @Override
    public boolean declaresJSResources() {
        return declaresJSResources;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState stateAfter) {
        updateUsages(this.stateAfter, stateAfter);
        this.stateAfter = stateAfter;
    }
}
