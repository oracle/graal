/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.llvm.LLVMGenerationResult;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;

import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateLLVMGenerationResult extends LLVMGenerationResult {
    private Map<CGlobalDataReference, String> cGlobals = new HashMap<>();

    public SubstrateLLVMGenerationResult(ResolvedJavaMethod method) {
        super(method);
    }

    public void recordCGlobal(CGlobalDataReference reference, String symbolName) {
        cGlobals.put(reference, symbolName);
    }

    @Override
    public void populate(CompilationResult compilationResult, StructuredGraph graph) {
        super.populate(compilationResult, graph);

        cGlobals.forEach((reference, symbolName) -> compilationResult.recordDataPatchWithNote(0, reference, symbolName));
        SubstrateDataBuilder dataBuilder = new SubstrateDataBuilder();
        getConstants().forEach((constant, symbolName) -> {
            DataSectionReference reference = compilationResult.getDataSection().insertData(dataBuilder.createDataItem(constant));
            compilationResult.recordDataPatchWithNote(0, reference, symbolName);
        });
    }
}
