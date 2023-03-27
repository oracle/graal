/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.nodes;

import java.util.SortedSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import org.graalvm.wasm.debugging.data.DebugFunction;

/**
 * Represents the statements in the source file of a wasm binary. Provides some helper methods to
 * interact with the instrumentation system.
 */
public final class WasmInstrumentationSupportNode extends Node {
    @Children private final WasmBaseStatementNode[] statementNodes;

    private final EconomicMap<Integer, Integer> lineToIndexMap;
    private int sourceLocation;

    @TruffleBoundary
    public WasmInstrumentationSupportNode(DebugFunction debugFunction, WasmModule module, int functionIndex) {
        final DebugLineMap sourceLineMap = debugFunction.lineMap();
        final Source source = debugFunction.sourceSection().getSource();
        final int startOffset = module.functionSourceCodeStartOffset(functionIndex);
        if (sourceLineMap != null && startOffset != -1) {
            IntArrayList functionLines = new IntArrayList();
            int endOffset = module.functionSourceCodeEndOffset(functionIndex);

            int startElement = sourceLineMap.getLine(startOffset);
            SortedSet<Integer> lineSet = sourceLineMap.lines().tailSet(startElement);
            lineToIndexMap = EconomicMap.create(lineSet.size());
            // start of the function, calculate sub array offset and length of all lines
            for (int line : lineSet) {
                int pc = sourceLineMap.getSourceLocation(line);
                if (pc > endOffset) {
                    break;
                }
                lineToIndexMap.put(line, functionLines.size());
                functionLines.add(line);
            }

            // create statement nodes for every source code line
            int length = functionLines.size();
            statementNodes = length == 0 ? null : new WasmBaseStatementNode[length];
            for (int i = 0; i < length; i++) {
                statementNodes[i] = new WasmStatementNode(functionLines.get(i), source);
            }
        } else {
            statementNodes = null;
            lineToIndexMap = null;
        }
    }

    public void notifyLine(VirtualFrame frame, int currentLine, int nextLine, int currentSourceLocation) {
        CompilerAsserts.partialEvaluationConstant(currentLine);
        CompilerAsserts.partialEvaluationConstant(nextLine);
        final int currentLineIndex = lineIndexOrDefault(currentLine);
        final int nextLineIndex = lineIndexOrDefault(nextLine);
        if (currentLineIndex == nextLineIndex) {
            return;
        }
        this.sourceLocation = currentSourceLocation;
        exitAt(frame, currentLineIndex);
        enterAt(frame, nextLineIndex);
    }

    @TruffleBoundary
    private int lineIndexOrDefault(int line) {
        return lineToIndexMap.get(line, -1);
    }

    private void enterAt(VirtualFrame frame, int lineIndex) {
        InstrumentableNode.WrapperNode wrapperNode = getWrapperAt(lineIndex);
        if (wrapperNode == null) {
            return;
        }
        ProbeNode probeNode = wrapperNode.getProbeNode();
        try {
            probeNode.onEnter(frame);
        } catch (Throwable t) {
            Object result = probeNode.onReturnExceptionalOrUnwind(frame, t, false);
            if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException();
            } else if (result != null) {
                return;
            }
            throw t;
        }
    }

    private void exitAt(VirtualFrame frame, int lineIndex) {
        InstrumentableNode.WrapperNode wrapperNode = getWrapperAt(lineIndex);
        if (wrapperNode == null) {
            return;
        }
        ProbeNode probeNode = wrapperNode.getProbeNode();
        try {
            probeNode.onReturnValue(frame, WasmConstant.VOID);
        } catch (Throwable t) {
            Object result = probeNode.onReturnExceptionalOrUnwind(frame, t, true);
            if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException();
            } else if (result != null) {
                return;
            }
            throw t;
        }
    }

    @TruffleBoundary
    private InstrumentableNode.WrapperNode getWrapperAt(int lineIndex) {
        if (statementNodes == null || lineIndex < 0 || lineIndex > statementNodes.length) {
            return null;
        }
        WasmBaseStatementNode node = statementNodes[lineIndex];
        if (!(node instanceof InstrumentableNode.WrapperNode)) {
            return null;
        }
        CompilerAsserts.partialEvaluationConstant(node);
        return ((InstrumentableNode.WrapperNode) node);
    }

    public int currentSourceLocation() {
        return sourceLocation;
    }
}
