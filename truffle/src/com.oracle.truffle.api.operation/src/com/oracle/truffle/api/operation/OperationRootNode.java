/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.operation;

import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.operation.introspection.ExceptionHandler;
import com.oracle.truffle.api.operation.introspection.Instruction;
import com.oracle.truffle.api.operation.introspection.OperationIntrospection;
import com.oracle.truffle.api.source.SourceSection;

public interface OperationRootNode extends BytecodeOSRNode, OperationIntrospection.Provider {

    default String dump() {
        StringBuilder sb = new StringBuilder();
        OperationIntrospection id = getIntrospectionData();

        for (Instruction i : id.getInstructions()) {
            sb.append(i.toString()).append('\n');
        }

        List<ExceptionHandler> handlers = id.getExceptionHandlers();
        if (handlers.size() > 0) {
            sb.append("Exception handlers:\n");
            for (ExceptionHandler eh : handlers) {
                sb.append("  ").append(eh.toString()).append('\n');
            }
        }

        return sb.toString();
    }

    Object execute(VirtualFrame frame);

    @SuppressWarnings("unused")
    default void executeProlog(VirtualFrame frame) {
    }

    @SuppressWarnings("unused")
    default void executeEpilog(VirtualFrame frame, Object returnValue, Throwable throwable) {
    }

    // Sets an invocation threshold that must be reached before the baseline interpreter switches to
    // a specializing interpreter. This method has no effect if the node has already switched to a
    // specializing interpreter.
    @SuppressWarnings("unused")
    default void setBaselineInterpreterThreshold(int invocationCount) {
    }

    @SuppressWarnings("unused")
    default SourceSection getSourceSectionAtBci(int bci) {
        throw new AbstractMethodError();
    }

    @SuppressWarnings("unused")
    default InstrumentableNode materializeInstrumentTree(Set<Class<? extends Tag>> materializedTags) {
        throw new AbstractMethodError();
    }

    /**
     * If an {@code OperationRootNode} is not well-formed, the Operation DSL will provide an
     * actionable error message to fix it. The default implementations below are provided so that
     * "abstract method not implemented" errors do not hide the DSL's error messages. When there are
     * no errors, the DSL will generate actual implementations for these methods.
     */

    @Override
    default Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        throw new AbstractMethodError();
    }

    @Override
    default void setOSRMetadata(Object osrMetadata) {
        throw new AbstractMethodError();
    }

    @Override
    default Object getOSRMetadata() {
        throw new AbstractMethodError();
    }
}
