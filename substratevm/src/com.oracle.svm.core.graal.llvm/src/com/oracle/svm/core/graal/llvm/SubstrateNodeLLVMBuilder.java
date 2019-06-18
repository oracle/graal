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

import org.bytedeco.javacpp.LLVM.LLVMValueRef;
import org.graalvm.compiler.core.llvm.LLVMGenerator;
import org.graalvm.compiler.core.llvm.LLVMUtils;
import org.graalvm.compiler.core.llvm.NodeLLVMBuilder;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;

public class SubstrateNodeLLVMBuilder extends NodeLLVMBuilder implements SubstrateNodeLIRBuilder {
    private long nextCGlobalId = 0L;

    protected SubstrateNodeLLVMBuilder(StructuredGraph graph, LLVMGenerator gen) {
        super(graph, gen, SubstrateDebugInfoBuilder::new);

        gen.getBuilder().setPersonalityFunction(gen.getFunction(LLVMFeature.getPersonalityStub()));
    }

    @Override
    public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
        CGlobalDataInfo dataInfo = node.getDataInfo();

        String symbolName = (dataInfo.isSymbolReference()) ? dataInfo.getData().symbolName : "global_" + builder.getFunctionName() + "#" + nextCGlobalId++;
        ((SubstrateLLVMGenerationResult) gen.getLLVMResult()).recordCGlobal(new CGlobalDataReference(dataInfo), symbolName);

        setResult(node, builder.buildPtrToInt(builder.getExternalSymbol(symbolName), builder.longType()));
    }

    @Override
    public Variable emitReadReturnAddress() {
        LLVMValueRef returnAddress = getLIRGeneratorTool().getBuilder().buildReturnAddress(getLIRGeneratorTool().getBuilder().constantInt(0));
        return new LLVMUtils.LLVMVariable(returnAddress);
    }
}
