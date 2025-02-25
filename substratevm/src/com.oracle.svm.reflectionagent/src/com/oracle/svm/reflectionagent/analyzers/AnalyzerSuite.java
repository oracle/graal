/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflectionagent.analyzers;

import com.oracle.svm.reflectionagent.MethodCallUtils;
import com.oracle.svm.reflectionagent.cfg.ControlFlowGraphNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceValue;

import java.util.HashMap;
import java.util.Map;

public class AnalyzerSuite {

    private final Map<String, ConstantValueAnalyzer> valueAnalyzers = new HashMap<>();
    private final Map<String, ConstantArrayAnalyzer> arrayAnalyzers = new HashMap<>();

    public void registerAnalyzer(ConstantValueAnalyzer analyzer) {
        valueAnalyzers.put(analyzer.typeDescriptor(), analyzer);
    }

    public void registerAnalyzer(ConstantArrayAnalyzer analyzer) {
        arrayAnalyzers.put(analyzer.typeDescriptor(), analyzer);
    }

    public boolean isConstant(MethodInsnNode methodCall, ControlFlowGraphNode<SourceValue> frame, int[] mustBeConstantArgs) {
        for (int argIdx : mustBeConstantArgs) {
            String argTypeName = MethodCallUtils.getTypeDescOfArg(methodCall, argIdx);
            if (argTypeName.startsWith("[")) {
                ConstantArrayAnalyzer analyzer = arrayAnalyzers.get(argTypeName);
                assert analyzer != null : "Analyzer for " + argTypeName + " not found";
                if (!analyzer.isConstant(MethodCallUtils.getCallArg(methodCall, argIdx, frame), methodCall)) {
                    return false;
                }
            } else {
                ConstantValueAnalyzer analyzer = valueAnalyzers.get(argTypeName);
                assert analyzer != null : "Analyzer for " + argTypeName + " not found";
                if (!analyzer.isConstant(MethodCallUtils.getCallArg(methodCall, argIdx, frame))) {
                    return false;
                }
            }
        }
        return true;
    }
}
