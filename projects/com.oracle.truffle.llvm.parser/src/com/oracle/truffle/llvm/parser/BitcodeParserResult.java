/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.nio.ByteBuffer;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceModel;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;

public final class BitcodeParserResult {
    private final ModelModule model;
    private final LLVMPhiManager phis;
    private final StackAllocation stackAllocation;
    private final LLVMLabelList labels;
    private final SourceModel sourceModel;

    private BitcodeParserResult(ModelModule model, LLVMPhiManager phis, StackAllocation stackAllocation, LLVMLabelList labels, SourceModel sourceModel) {
        this.model = model;
        this.phis = phis;
        this.stackAllocation = stackAllocation;
        this.labels = labels;
        this.sourceModel = sourceModel;
    }

    public ModelModule getModel() {
        return model;
    }

    public LLVMPhiManager getPhis() {
        return phis;
    }

    public StackAllocation getStackAllocation() {
        return stackAllocation;
    }

    public LLVMLabelList getLabels() {
        return labels;
    }

    public List<String> getLibraries() {
        return model.getLibraries();
    }

    public List<String> getLibraryPaths() {
        return model.getLibraryPaths();
    }

    public SourceModel getSourceModel() {
        return sourceModel;
    }

    public static BitcodeParserResult getFromSource(Source source, ByteBuffer bytes) {
        final ModelModule model = LLVMScanner.parse(bytes);

        // extract SourceSection and LLVMSourceType objects from metadata
        final SourceModel sourceModel = SourceModel.generate(model, source);

        final LLVMPhiManager phis = LLVMPhiManager.generate(model);
        final StackAllocation stackAllocation = StackAllocation.generate(model);
        final LLVMLabelList labels = LLVMLabelList.generate(model);

        return new BitcodeParserResult(model, phis, stackAllocation, labels, sourceModel);
    }
}
