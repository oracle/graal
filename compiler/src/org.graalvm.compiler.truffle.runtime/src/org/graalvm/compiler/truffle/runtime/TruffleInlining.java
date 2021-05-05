/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleInliningData;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import jdk.vm.ci.meta.JavaConstant;

public class TruffleInlining implements TruffleInliningData {

    private final List<CompilableTruffleAST> targetsToDequeue = new ArrayList<>();
    private final List<CompilableTruffleAST> inlinedTargets = new ArrayList<>();
    private int callCount = -1;
    private int inlinedCallCount = -1;

    private TruffleNodeSources nodeSources;

    public TruffleInlining() {
    }

    public TruffleNodeSources getTruffleNodeSources() {
        if (nodeSources == null) {
            nodeSources = new TruffleNodeSources();
        }
        return nodeSources;
    }

    public int countCalls() {
        return callCount;
    }

    @Override
    public int countInlinedCalls() {
        return inlinedCallCount;
    }

    public CompilableTruffleAST[] inlinedTargets() {
        return inlinedTargets.toArray(new CompilableTruffleAST[0]);
    }

    @Override
    public void addInlinedTarget(CompilableTruffleAST target) {
        inlinedTargets.add(target);
    }

    @Override
    public void setInlinedCallCount(int count) {
        inlinedCallCount = count;
    }

    @Override
    public void setCallCount(int count) {
        callCount = count;
    }

    @Override
    public OptimizedDirectCallNode findCallNode(JavaConstant callNodeConstant) {
        return runtime().asObject(OptimizedDirectCallNode.class, callNodeConstant);
    }

    @Override
    public void addTargetToDequeue(CompilableTruffleAST target) {
        targetsToDequeue.add(target);
    }

    public void dequeueTargets() {
        for (CompilableTruffleAST target : targetsToDequeue) {
            target.dequeueInlined();
        }
    }

    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        Node truffleNode = runtime().asObject(Node.class, node);
        if (truffleNode == null) {
            return null;
        }
        return getTruffleNodeSources().getSourceLocation(truffleNode);
    }

    static class TruffleSourceLanguagePosition implements org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition {

        private final SourceSection sourceSection;
        private final Class<?> nodeClass;
        private final int nodeId;

        TruffleSourceLanguagePosition(SourceSection section, Class<?> nodeClass, int nodeId) {
            this.sourceSection = section;
            this.nodeClass = nodeClass;
            this.nodeId = nodeId;
        }

        @Override
        public String getDescription() {
            if (sourceSection == null) {
                return "<no-description>";
            }
            return sourceSection.getSource().getURI() + " " + sourceSection.getStartLine() + ":" + sourceSection.getStartColumn();
        }

        @Override
        public int getOffsetEnd() {
            if (sourceSection == null) {
                return -1;
            }
            return sourceSection.getCharEndIndex();
        }

        @Override
        public int getOffsetStart() {
            if (sourceSection == null) {
                return -1;
            }
            return sourceSection.getCharIndex();
        }

        @Override
        public int getLineNumber() {
            if (sourceSection == null) {
                return -1;
            }
            return sourceSection.getStartLine();
        }

        @Override
        public URI getURI() {
            if (sourceSection == null) {
                return null;
            }
            return sourceSection.getSource().getURI();
        }

        @Override
        public String getLanguage() {
            if (sourceSection == null) {
                return null;
            }
            return sourceSection.getSource().getLanguage();
        }

        @Override
        public int getNodeId() {
            return nodeId;
        }

        @Override
        public String getNodeClassName() {
            return nodeClass.getName();
        }

    }
}
