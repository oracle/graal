/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.debug;

import java.util.Map;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

public final class TraceSplittingListener implements OptimizedTruffleRuntimeListener {

    private TraceSplittingListener() {
    }

    public static void install(OptimizedTruffleRuntime runtime) {
        runtime.addListener(new TraceSplittingListener());
    }

    private int splitCount;

    @Override
    public void onCompilationSplit(OptimizedDirectCallNode callNode) {
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        if (callTarget.getOptionValue(OptimizedRuntimeOptions.TraceSplitting)) {
            String label = String.format("split %3s-%08x-%-4s ", splitCount++, 0xFFFF_FFFFL & callNode.getCurrentCallTarget().hashCode(), callNode.getCallCount());
            final Map<String, Object> debugProperties = callTarget.getDebugProperties();
            debugProperties.put("SourceSection", extractSourceSection(callNode));
            OptimizedTruffleRuntime.getRuntime().logEvent(callTarget, 0, label, debugProperties);
        }
    }

    @Override
    public void onCompilationSplitFailed(OptimizedDirectCallNode callNode, CharSequence reason) {
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        if (callTarget.getOptionValue(OptimizedRuntimeOptions.TraceSplitting)) {
            String label = String.format("split failed " + reason);
            final Map<String, Object> debugProperties = callTarget.getDebugProperties();
            debugProperties.put("SourceSection", extractSourceSection(callNode));
            OptimizedTruffleRuntime.getRuntime().logEvent(callTarget, 0, label, debugProperties);
        }
    }

    private static String extractSourceSection(OptimizedDirectCallNode node) {
        Node cnode = node;
        while (cnode.getSourceSection() == null && !(cnode instanceof RootNode)) {
            cnode = cnode.getParent();
            if (cnode == null) {
                return "";
            }
        }
        return getShortDescription(cnode.getSourceSection());
    }

    static String getShortDescription(SourceSection sourceSection) {
        if (sourceSection == null) {
            return "<Unknown>";
        }
        StringBuilder b = new StringBuilder();
        if (sourceSection.getSource().getPath() == null) {
            b.append(sourceSection.getSource().getName());
        } else {
            b.append(sourceSection.getSource().getPath());
        }

        b.append("~").append(formatIndices(sourceSection, true));
        return b.toString();
    }

    static String formatIndices(SourceSection sourceSection, boolean needsColumnSpecifier) {
        StringBuilder b = new StringBuilder();
        boolean singleLine = sourceSection.getStartLine() == sourceSection.getEndLine();
        if (singleLine) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        if (needsColumnSpecifier) {
            b.append(":");
            if (sourceSection.getCharLength() <= 1) {
                b.append(sourceSection.getCharIndex());
            } else {
                b.append(sourceSection.getCharIndex()).append("-").append(sourceSection.getCharIndex() + sourceSection.getCharLength() - 1);
            }
        }
        return b.toString();
    }

}
