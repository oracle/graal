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
package com.oracle.truffle.llvm.runtime;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public final class SulongStackTrace {

    private final ArrayList<Element> trace = new ArrayList<>();
    private final String summary;

    public SulongStackTrace(String summary) {
        this.summary = summary;
    }

    public static final class Element {

        private final String sourceFunctionName;
        private final LLVMSourceLocation sourceLocation;

        private final String irFunctionName;
        private final String irBlock;
        private final String irSourceName;

        Element(String sourceFunctionName, LLVMSourceLocation sourceLocation, String irFunctionName, String llvmirSourceName, String irBlock) {
            this.sourceFunctionName = sourceFunctionName;
            this.irFunctionName = irFunctionName;
            this.irBlock = irBlock;
            this.sourceLocation = sourceLocation;
            this.irSourceName = llvmirSourceName;
        }

        Element(String irFunctionName, String llvmirSourceName, String irBlock) {
            this(null, null, irFunctionName, llvmirSourceName, irBlock);
        }

        void appendToStackTrace(StringBuilder builder) {
            builder.append("\t ");
            boolean encloseIRScope = false;

            if (sourceFunctionName != null) {
                builder.append(sourceFunctionName);
                encloseIRScope = true;

            } else if (sourceLocation != null) {
                builder.append("<unknown>");
            }

            if (sourceLocation != null) {
                builder.append(" in ");
                builder.append(sourceLocation.describeLocation());
                encloseIRScope = true;
            }

            if (encloseIRScope) {
                builder.append(" (");
            }

            builder.append("LLVM IR Function ").append(irFunctionName);
            if (irSourceName != null) {
                builder.append(" in ").append(irSourceName);
            }
            if (irBlock != null) {
                builder.append(" in Block {").append(irBlock).append('}');
            }

            if (encloseIRScope) {
                builder.append(')');
            }

            builder.append('\n');
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            appendToStackTrace(sb);
            return sb.toString();
        }
    }

    public void addStackTraceElement(String sourceFunctionName, LLVMSourceLocation sourceLocation, String irFunctionName, String irSourceName, String irBlock) {
        trace.add(new Element(sourceFunctionName, sourceLocation, irFunctionName, irSourceName, irBlock));
    }

    public void addStackTraceElement(String irFunctionName, String irSourceName, String irBlock) {
        trace.add(new Element(irFunctionName, irSourceName, irBlock));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(summary);
        sb.append("\n\n");
        sb.append("C stack trace:\n");
        for (Element e : trace) {
            e.appendToStackTrace(sb);
        }
        return sb.toString();
    }

    public List<Element> getTrace() {
        return java.util.Collections.unmodifiableList(trace);
    }
}
