/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm;

import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maintains the results for already parsed libraries and a queue of yet to be parsed dependencies.
 */
final class ParseContext {
    private final List<LLVMParserResult> parserResults;
    private final ArrayDeque<ExternalLibrary> dependencyQueue;

    static ParseContext create() {
        ArrayDeque<ExternalLibrary> dependencyQueue = new ArrayDeque<>();
        ArrayList<LLVMParserResult> parserResults = new ArrayList<>();
        return new ParseContext(parserResults, dependencyQueue);
    }

    private ParseContext(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue) {
        this.parserResults = parserResults;
        this.dependencyQueue = dependencyQueue;
    }

    public List<LLVMParserResult> getParserResults() {
        return Collections.unmodifiableList(parserResults);
    }

    public int dependencyQueueSize() {
        return dependencyQueue.size();
    }

    public ExternalLibrary dependencyQueueRemoveFirst() {
        return dependencyQueue.removeFirst();
    }

    public boolean dependencyQueueIsEmpty() {
        return dependencyQueue.isEmpty();
    }

    public boolean parserResultsIsEmpty() {
        return parserResults.isEmpty();
    }

    public void dependencyQueueAddLast(ExternalLibrary dependency) {
        dependencyQueue.addLast(dependency);
    }

    public void parserResultsAdd(LLVMParserResult parserResult) {
        parserResults.add(parserResult);
    }

    public void parserResultsAddAll(List<LLVMParserResult> otherResults) {
        parserResults.addAll(otherResults);
    }
}
