/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class StopRequest implements Iterable<StopRequest.Scope> {

    private final ContinueStrategy nextAction;
    private final List<Scope> scopes;
    private final String functionName;
    private final int expectLine;
    private final boolean needsBreakPoint;

    StopRequest(ContinueStrategy nextAction, String functionName, int expectLine, boolean needsBreakPoint) {
        this.nextAction = nextAction;
        this.functionName = functionName;
        this.expectLine = expectLine;
        this.scopes = new ArrayList<>(2);
        this.needsBreakPoint = needsBreakPoint;
    }

    String getFunctionName() {
        return functionName;
    }

    int getLine() {
        return expectLine;
    }

    ContinueStrategy getNextAction() {
        return nextAction;
    }

    boolean needsBreakPoint() {
        return needsBreakPoint;
    }

    void addScope(Scope scope) {
        scopes.add(scope);
    }

    StopRequest updateLines(int from, int offset) {
        if (expectLine < from) {
            return this;
        }

        final StopRequest newRequest = new StopRequest(nextAction, functionName, expectLine + offset, needsBreakPoint);
        for (Scope scope : scopes) {
            newRequest.addScope(scope);
        }
        return newRequest;
    }

    @Override
    public Iterator<Scope> iterator() {
        return scopes.iterator();
    }

    static final class Scope {

        private final Map<String, LLVMDebugValue> expectLocals;
        private final String scopeName;
        private final boolean isPartial;

        Scope(String scopeName, boolean isPartial) {
            this.scopeName = scopeName;
            this.isPartial = isPartial;
            this.expectLocals = new HashMap<>();
        }

        void addMember(String name, LLVMDebugValue value) {
            expectLocals.put(name, value);
        }

        Map<String, LLVMDebugValue> getLocals() {
            return expectLocals;
        }

        boolean isPartial() {
            return isPartial;
        }

        String getName() {
            return scopeName;
        }
    }
}
