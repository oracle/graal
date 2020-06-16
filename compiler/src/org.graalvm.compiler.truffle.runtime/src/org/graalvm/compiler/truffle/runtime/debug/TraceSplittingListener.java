/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import java.util.Map;

import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public final class TraceSplittingListener implements GraalTruffleRuntimeListener {

    private TraceSplittingListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new TraceSplittingListener());
    }

    private int splitCount;

    @Override
    public void onCompilationSplit(OptimizedDirectCallNode callNode) {
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        if (callTarget.getOptionValue(PolyglotCompilerOptions.TraceSplitting)) {
            String label = String.format("split %3s-%-4s-%-4s ", splitCount++, Integer.toHexString(callNode.getCurrentCallTarget().hashCode()), callNode.getCallCount());
            final Map<String, Object> debugProperties = callTarget.getDebugProperties(null);
            debugProperties.put("SourceSection", extractSourceSection(callNode));
            TruffleCompilerRuntime.getRuntime().logEvent(callTarget, 0, label, debugProperties);
        }
    }

    @Override
    public void onCompilationSplitFailed(OptimizedDirectCallNode callNode, CharSequence reason) {
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        if (callTarget.getOptionValue(PolyglotCompilerOptions.TraceSplitting)) {
            String label = String.format("split failed " + reason);
            final Map<String, Object> debugProperties = callTarget.getDebugProperties(null);
            debugProperties.put("SourceSection", extractSourceSection(callNode));
            TruffleCompilerRuntime.getRuntime().logEvent(callTarget, 0, label, debugProperties);
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
        if (sourceSection == null || sourceSection.getSource() == null) {
            // TODO the source == null branch can be removed if the deprecated
            // SourceSection#createUnavailable has be removed.
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
