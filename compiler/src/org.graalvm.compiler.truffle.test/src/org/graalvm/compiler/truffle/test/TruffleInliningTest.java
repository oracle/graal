/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInliningDecision;
import org.graalvm.compiler.truffle.runtime.TruffleInliningPolicy;
import org.junit.Assert;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.polyglot.Context;

public abstract class TruffleInliningTest extends TestWithPolyglotOptions {

    private static final String[] DEFAULT_OPTIONS = {"engine.Compilation", "false", "engine.LanguageAgnosticInlining", "false"};

    class InlineTestRootNode extends RootNode {

        @Children final Node[] children;
        String name;

        @Override
        public String toString() {
            return name;
        }

        InlineTestRootNode(int size, String name) {
            super(null);

            children = new Node[size];
            for (int i = 0; i < children.length; i++) {
                children[i] = new InlineTestRootNode(0, "");
            }
            this.name = name;
        }

        public void addCallSites(OptimizedDirectCallNode[] nodes) {
            if (nodes.length > 0) {
                for (int i = 0; i < nodes.length; i++) {
                    children[i] = nodes[i];
                }
            }
            for (int i = nodes.length; i < children.length; i++) {
                children[i] = new InlineTestRootNode(0, "");
            }
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {

            int maxRecursionDepth = (int) frame.getArguments()[0];
            if (maxRecursionDepth < 100) {
                for (Node child : children) {
                    if (child instanceof OptimizedDirectCallNode) {
                        OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) child;
                        frame.getArguments()[0] = maxRecursionDepth + 1;
                        callNode.call(frame.getArguments());
                    }
                }
            }
            return null;
        }
    }

    class TruffleInliningTestScenarioBuilder {

        class CallInstruction {
            public String target;
            public int count;

            CallInstruction(String target, int count) {
                this.target = target;
                this.count = count;
            }
        }

        class TargetInstruction {
            public int size;
            public int execCount;

            TargetInstruction(int size, int execCount) {
                this.size = size;
                this.execCount = execCount;
            }
        }

        private final Map<String, TargetInstruction> targetInstructions = new HashMap<>();
        private final Map<String, OptimizedCallTarget> targets = new HashMap<>();
        private final Map<String, List<CallInstruction>> callInstructions = new HashMap<>();

        private String lastAddedTargetName = null;

        TruffleInliningTestScenarioBuilder target(String name) {
            return target(name, 0);
        }

        TruffleInliningTestScenarioBuilder target(String name, int size) {
            targetInstructions.put(name, new TargetInstruction(size, 0));
            lastAddedTargetName = name;
            return this;
        }

        TruffleInliningTestScenarioBuilder execute() {
            execute(1);
            return this;
        }

        TruffleInliningTestScenarioBuilder execute(int times) {
            try {
                targetInstructions.get(lastAddedTargetName).execCount = times;
            } catch (NullPointerException e) {
                throw new IllegalStateException("Call to execute before defining a target!");
            }
            return this;
        }

        TruffleInliningTestScenarioBuilder calls(String name) {
            return calls(name, 0);
        }

        TruffleInliningTestScenarioBuilder calls(String name, int count) {
            // Increment the caller size to make room for the call
            targetInstructions.get(lastAddedTargetName).size++;

            // Update call Instructions
            List<CallInstruction> existingCalls = callInstructions.get(lastAddedTargetName);
            if (existingCalls == null) {
                existingCalls = new ArrayList<>();
                callInstructions.put(lastAddedTargetName, existingCalls);
            }
            existingCalls.add(new CallInstruction(name, count));
            return this;
        }

        OptimizedCallTarget buildTarget() {
            return buildTarget(false);
        }

        OptimizedCallTarget buildTarget(boolean andExecute) {
            try {
                buildTargets();
                buildCalls();
                OptimizedCallTarget target = targets.get(lastAddedTargetName);
                if (andExecute) {
                    target.call(0);
                }
                return target;
            } finally {
                cleanup();
            }
        }

        TruffleInlining buildDecisions() {
            return buildDecisions(false);
        }

        TruffleInlining buildDecisions(boolean andExecute) {
            return new TruffleInlining(buildTarget(andExecute), policy);
        }

        private void buildTargets() {
            for (String targetName : targetInstructions.keySet()) {
                TargetInstruction instruction = targetInstructions.get(targetName);
                OptimizedCallTarget newTarget = (OptimizedCallTarget) GraalTruffleRuntime.getRuntime().createCallTarget(new InlineTestRootNode(instruction.size, targetName));
                for (int i = 0; i < instruction.execCount; i++) {
                    newTarget.call(0);
                }
                targets.put(targetName, newTarget);
            }
        }

        private void buildCalls() {
            for (String callerName : callInstructions.keySet()) {
                OptimizedCallTarget caller = targets.get(callerName);
                List<OptimizedDirectCallNode> callSites = new ArrayList<>();
                for (CallInstruction instruction : callInstructions.get(callerName)) {
                    OptimizedCallTarget target = targets.get(instruction.target);
                    if (target == null) {
                        throw new IllegalStateException("Call to undefined target: " + instruction.target);
                    }
                    OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) GraalTruffleRuntime.getRuntime().createDirectCallNode(target);
                    callSites.add(callNode);
                    for (int i = 0; i < instruction.count; i++) {
                        callNode.call(0);
                    }
                }
                InlineTestRootNode rootNode = (InlineTestRootNode) caller.getRootNode();
                rootNode.addCallSites(callSites.toArray(new OptimizedDirectCallNode[0]));
                rootNode.adoptChildren();
            }
        }

        private void cleanup() {
            targets.clear();
            targetInstructions.clear();
            callInstructions.clear();
            lastAddedTargetName = null;
        }

    }

    protected TruffleInliningTestScenarioBuilder builder = new TruffleInliningTestScenarioBuilder();
    protected TruffleInliningPolicy policy = new DefaultInliningPolicy();

    void assertInlined(TruffleInlining decisions, String name) {
        Assert.assertTrue(name + " was not inlined!", countInlines(decisions, name) > 0);
    }

    void assertNotInlined(TruffleInlining decisions, String name) {
        int inlines = countInlines(decisions, name);
        Assert.assertTrue(name + " was inlined " + inlines + " times!", inlines == 0);
    }

    void traverseDecisions(List<TruffleInliningDecision> decisions, Consumer<TruffleInliningDecision> f) {
        for (TruffleInliningDecision decision : decisions) {
            f.accept(decision);
            traverseDecisions(decision.getCallSites(), f);
        }

    }

    int countInlines(TruffleInlining decisions, String name) {
        final int[] count = {0};
        traverseDecisions(decisions.getCallSites(), (TruffleInliningDecision d) -> {
            if (d.shouldInline() && d.getTarget().toString().equals(name)) {
                count[0]++;
            }
        });
        return count[0];
    }

    @Override
    protected Context setupContext(String... keyValuePairs) {
        String[] newOptions;
        if (keyValuePairs.length == 0) {
            newOptions = DEFAULT_OPTIONS;
        } else {
            newOptions = Arrays.copyOf(DEFAULT_OPTIONS, DEFAULT_OPTIONS.length + keyValuePairs.length);
            System.arraycopy(keyValuePairs, 0, newOptions, DEFAULT_OPTIONS.length, keyValuePairs.length);
        }
        return super.setupContext(newOptions);
    }
}
