/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

public final class OptimizedOSRLoopNode extends LoopNode implements ReplaceObserver {

    private CompilationProfile cachedProfile;
    private int lastLoopCount;
    private OptimizedCallTarget compiledTarget;

    @Child private RepeatingNode repeatableNode;

    private boolean disabled;

    private OptimizedOSRLoopNode(RepeatingNode repeatableNode) {
        this.repeatableNode = repeatableNode;
    }

    @Override
    public Node copy() {
        OptimizedOSRLoopNode copy = (OptimizedOSRLoopNode) super.copy();
        copy.compiledTarget = null;
        return copy;
    }

    @Override
    public RepeatingNode getRepeatingNode() {
        return repeatableNode;
    }

    @Override
    public void executeLoop(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            boolean done = false;
            while (!done) {
                if (compiledTarget == null) {
                    done = profilingLoop(frame);
                } else {
                    done = compilingLoop(frame);
                }
            }
        } else {
            while (repeatableNode.executeRepeating(frame)) {
                if (CompilerDirectives.inInterpreter()) {
                    // compiled method got invalidated. We might need OSR again.
                    executeLoop(frame);
                    return;
                }
            }
        }
    }

    private boolean profilingLoop(VirtualFrame frame) {
        CompilationProfile profile = getProfile(getCallTarget());
        int loopCount = 0;
        boolean localDisabled = this.disabled;
        try {
            while (repeatableNode.executeRepeating(frame)) {
                loopCount++;
                if (!localDisabled) {
                    try {
                        profile.reportOSR();
                    } catch (ArithmeticException e) {
                        compileLoop(frame);
                        return false;
                    }
                }
            }
        } finally {
            reportLoopCount(loopCount);
        }
        return true;
    }

    private CompilationProfile getProfile(OptimizedCallTarget target) {
        CompilationProfile profile = this.cachedProfile;
        if (profile == null) {
            profile = target.getCompilationProfile();
            this.cachedProfile = profile;
        }
        return profile;
    }

    private OptimizedCallTarget getCallTarget() {
        return (OptimizedCallTarget) getRootNode().getCallTarget();
    }

    private boolean compilingLoop(VirtualFrame frame) {
        int iterations = 0;
        try {
            do {
                OptimizedCallTarget target = compiledTarget;
                if (target == null) {
                    return false;
                } else if (target.isValid()) {
                    Object result = target.callDirect(new Object[]{frame});
                    iterations = lastLoopCount;
                    if (result == Boolean.TRUE) {
                        // loop is done. No further repetitions necessary.
                        return true;
                    } else {
                        invalidate(this, "OSR compilation got invalidated");
                        return false;
                    }
                } else if (!target.isCompiling()) {
                    invalidate(this, "OSR compilation failed or cancelled");
                    return false;
                }
            } while (repeatableNode.executeRepeating(frame));
        } finally {
            reportLoopCount(iterations);
        }
        return true;
    }

    private void compileLoop(VirtualFrame frame) {
        atomic(new Runnable() {
            public void run() {
                /*
                 * Compilations need to run atomically as they may be scheduled by multiple threads
                 * at the same time. This strategy lets the first thread win. Later threads will not
                 * issue compiles.
                 */
                if (compiledTarget == null) {
                    compiledTarget = compileImpl(frame);
                }
            }
        });
    }

    private OptimizedCallTarget compileImpl(VirtualFrame frame) {
        OptimizedCallTarget parentTarget = getCallTarget();
        CompilationProfile profile = getProfile(parentTarget);
        profile.reportOSRCompiledLoop();

        if (parentTarget.isValid() || parentTarget.isCompiling()) {
            disabled = true;
            return null;
        } else {
            Node parent = getParent();
            OptimizedCallTarget osrTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new OSRRootNode(this));
            // to avoid a deopt on first call we provide some profiling information
            osrTarget.profileReturnType(Boolean.TRUE);
            osrTarget.profileReturnType(Boolean.FALSE);
            osrTarget.profileArguments(new Object[]{frame});
            // let the old parent re-adopt the children
            parent.adoptChildren();
            osrTarget.compile();
            return osrTarget;
        }
    }

    private void reportLoopCount(int reportIterations) {
        if (reportIterations != 0) {
            lastLoopCount = reportIterations;
            getRootNode().reportLoopCount(reportIterations);
        }
    }

    public boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        invalidate(newNode, reason);
        return false;
    }

    private void invalidate(Object source, CharSequence reason) {
        OptimizedCallTarget target = this.compiledTarget;
        if (target != null) {
            target.invalidate(source, reason);
            compiledTarget = null;
        }
    }

    public static LoopNode create(RepeatingNode repeat) {
        if (TruffleCompilerOptions.TruffleOSR.getValue()) {
            return new OptimizedOSRLoopNode(repeat);
        } else {
            return new OptimizedLoopNode(repeat);
        }
    }

    private static class OSRRootNode extends RootNode {

        @Child private OptimizedOSRLoopNode loopNode;

        public OSRRootNode(OptimizedOSRLoopNode loop) {
            super(TruffleLanguage.class, loop.getSourceSection(), loop.getRootNode().getFrameDescriptor());
            this.loopNode = loop;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            VirtualFrame parentFrame = (VirtualFrame) frame.getArguments()[0];
            while (loopNode.getRepeatingNode().executeRepeating(parentFrame)) {
                if (CompilerDirectives.inInterpreter()) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }

        @Override
        public boolean isCloningAllowed() {
            return false;
        }

        @Override
        public String toString() {
            return loopNode.getRepeatingNode().toString() + "<OSR>";
        }

    }

}
