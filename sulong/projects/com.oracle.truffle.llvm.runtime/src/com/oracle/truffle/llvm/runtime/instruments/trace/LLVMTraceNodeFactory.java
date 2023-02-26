/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.instruments.trace;

import com.oracle.truffle.api.CompilerAsserts;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMContext;

final class LLVMTraceNodeFactory implements ExecutionEventNodeFactory {

    LLVMTraceNodeFactory() {
    }

    @Override
    public ExecutionEventNode create(EventContext eventContext) {
        CompilerAsserts.neverPartOfCompilation();
        if (eventContext.hasTag(StandardTags.RootTag.class)) {
            assert eventContext.getInstrumentedNode() != null;
            final RootNode rootNode = eventContext.getInstrumentedNode().getRootNode();
            assert rootNode != null;
            final SourceSection sourceSection = rootNode.getSourceSection();
            return new RootTrace(rootNode.getName(), toTraceLine(sourceSection, false));

        } else if (eventContext.hasTag(StandardTags.StatementTag.class)) {
            return new StatementTrace(toTraceLine(eventContext.getInstrumentedSourceSection(), true));

        } else {
            throw new IllegalStateException("Unknown node for tracing: " + eventContext.getInstrumentedNode());
        }
    }

    @TruffleBoundary
    private static String toTraceLine(SourceSection sourceSection, boolean includeText) {
        final StringBuilder builder = new StringBuilder();

        builder.append(sourceSection.getSource().getName());

        if (sourceSection.hasLines()) {
            builder.append(':');
            builder.append(sourceSection.getStartLine());
        } else {
            builder.append(":?");
        }

        if (includeText && sourceSection.hasCharIndex() && sourceSection.getCharLength() > 0) {
            builder.append(" -> ");
            builder.append(sourceSection.getCharacters());
        }

        return builder.toString();
    }

    private abstract static class TraceNode extends ExecutionEventNode {

        TraceNode() {
        }

        @TruffleBoundary
        @SuppressWarnings("deprecation") // GR-41711: we still need Thread.getId() for JDK17 support
        static void trace(String message) {
            LLVMContext.traceIRLog(String.format("(Thread #%d) %s", Thread.currentThread().getId(), message));
        }
    }

    private static final class StatementTrace extends TraceNode {

        private final String location;

        StatementTrace(String location) {
            this.location = location;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            trace(location);
        }
    }

    private static final class RootTrace extends TraceNode {

        private final String enterPrefix;
        private final String exitPrefix;
        private final String exceptionPrefix;

        @TruffleBoundary
        RootTrace(String functionName, String sourceSection) {
            this.enterPrefix = String.format("Entering function %s at %s with arguments: ", functionName, sourceSection);
            this.exitPrefix = "Leaving " + functionName;
            this.exceptionPrefix = "Exceptionally leaving " + functionName;
        }

        @TruffleBoundary
        private void traceFunctionArgs(Object[] arguments) {
            trace(enterPrefix + Arrays.toString(arguments));
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            traceFunctionArgs(frame.getArguments());
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            trace(exitPrefix);
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            trace(exceptionPrefix);
        }
    }
}
