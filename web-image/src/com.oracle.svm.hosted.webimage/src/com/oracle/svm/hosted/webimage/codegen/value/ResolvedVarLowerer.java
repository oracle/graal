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
package com.oracle.svm.hosted.webimage.codegen.value;

import org.graalvm.collections.EconomicSet;

import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class adds support for source maps.
 */
public class ResolvedVarLowerer {

    public static void lower(ResolvedVar var, CodeGenTool codeGenTool) {
        if (WebImageOptions.GenerateSourceMap.getValue(HostedOptionValues.singleton())) {
            ((JSCodeBuffer) codeGenTool.getCodeBuffer()).markSymbol(getVarName(var.getOrig(), ((ValueNode) var.getOrig()).graph().method(), var.getName()));
        }
        codeGenTool.genResolvedVarAccess(var.getName());
    }

    public static String getVarName(Node valueNode, ResolvedJavaMethod method, String tmpName) {
        LocalVariableTable tab = method.getLocalVariableTable();

        if (tab == null) {
            return varNameFallback(valueNode, method, "{no debug info} " + tmpName);
        }

        // More named locals may have same value. Collect all names for the value.
        // There will almost always at most one item.
        EconomicSet<String> results = EconomicSet.create();

        String messageIfNone = "{temporary} ";

        for (FrameState frameState : valueNode.usages().filter(FrameState.class).snapshot()) {
            if (!frameState.getMethod().equals(method)) {
                // This frame state describes the local variables of an inlined method.
                continue;
            }
            int numSlots = frameState.localsSize();
            for (int slot = 0; slot < numSlots; slot++) {
                if (frameState.localAt(slot) == valueNode) {
                    Local local = tab.getLocal(slot, frameState.bci);
                    if (local == null) {
                        messageIfNone = "{error} ";
                        continue;
                    }
                    String name = local.getName();
                    if (name == null) {
                        // Artificial local. Treat as temporary value.
                        continue;
                    }
                    if (results.contains(name)) {
                        // Already seen that name.
                        continue;
                    }
                    results.add(local.getName());
                }
            }
        }

        if (!results.isEmpty()) {
            return String.join(", ", results);
        }

        return varNameFallback(valueNode, method, messageIfNone + tmpName);
    }

    private static String varNameFallback(Node valueNode, ResolvedJavaMethod method, String fallbackName) {
        if (valueNode instanceof ParameterNode) {
            int index = ((ParameterNode) valueNode).index();
            if (!method.isStatic()) {
                if (index == 0) {
                    return "this?";
                } else {
                    index--;
                }
            }
            return method.getParameters()[index].getName() + "?";
        }
        return fallbackName;
    }
}
