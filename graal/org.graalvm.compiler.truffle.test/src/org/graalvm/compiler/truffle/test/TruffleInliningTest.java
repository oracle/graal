/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.graalvm.compiler.test.GraalTest.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.truffle.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.graalvm.compiler.truffle.TruffleInliningDecision;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class TruffleInliningTest {

    class InlineTestRootNode extends RootNode {

        @Children final Node[] children;
        String name;

        @Override
        public String toString() {
            return name;
        }

        InlineTestRootNode(int size, String name) {
            super(MockLanguage.class, null, null);

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
        private Map<String, OptimizedCallTarget> targets = new HashMap<>();
        private String lastAddedTargetName = null;
        private Map<String, Integer> targetInstructions = new HashMap<>();
        private Map<String, Integer> executeTargetInstructions = new HashMap<>();

        class CallInstruction {
            public String target;
            public int count;

            CallInstruction(String target, int count) {
                this.target = target;
                this.count = count;
            }
        }

        private Map<String, List<CallInstruction>> callInstructions = new HashMap<>();

        TruffleInliningTestScenarioBuilder target(String name) {
            return target(name, 0);
        }

        TruffleInliningTestScenarioBuilder target(String name, int size) {
            targetInstructions.put(name, size);
            lastAddedTargetName = name;
            return this;
        }

        TruffleInliningTestScenarioBuilder execute() {
            execute(1);
            return this;
        }

        TruffleInliningTestScenarioBuilder execute(int times) {
            executeTargetInstructions.put(lastAddedTargetName, times);
            return this;
        }

        TruffleInliningTestScenarioBuilder calls(String name) {
            return calls(name, 0);
        }

        TruffleInliningTestScenarioBuilder calls(String name, int count) {
            // Increment the caller size to make room for the call
            int targetSize = targetInstructions.get(lastAddedTargetName);
            targetInstructions.replace(lastAddedTargetName, targetSize, targetSize + 1);

            // Update call Instructions
            List<CallInstruction> existingCalls = callInstructions.get(lastAddedTargetName);
            if (existingCalls == null) {
                existingCalls = new ArrayList<>();
                callInstructions.put(lastAddedTargetName, existingCalls);
            }
            existingCalls.add(new CallInstruction(name, count));
            return this;
        }

        TruffleInlining build() {
            return build(false);
        }

        TruffleInlining build(boolean andExecute) {
            try {
                buildTargets();
                buildCalls();
                if (andExecute) {
                    targets.get(lastAddedTargetName).call(0);
                }
                return new TruffleInlining(targets.get(lastAddedTargetName), new DefaultInliningPolicy());
            } finally {
                cleanup();
            }
        }

        private void buildTargets() {
            for (String targetName : targetInstructions.keySet()) {
                int size = targetInstructions.get(targetName);
                OptimizedCallTarget newTarget = new OptimizedCallTarget(null, new InlineTestRootNode(size, targetName));
                Integer calledFromOutside = executeTargetInstructions.get(targetName);
                if (calledFromOutside != null) {
                    for (int i = 0; i < calledFromOutside; i++) {
                        newTarget.call(0);
                    }
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
                    OptimizedDirectCallNode callNode = new OptimizedDirectCallNode(GraalTruffleRuntime.getRuntime(), target);
                    callSites.add(callNode);
                    for (int i = 0; i < instruction.count; i++) {
                        Integer[] args = {0};
                        callNode.call(args);
                    }
                }
                InlineTestRootNode rootNode = (InlineTestRootNode) caller.getRootNode();
                rootNode.addCallSites(callSites.toArray(new OptimizedDirectCallNode[0]));
                rootNode.adoptChildren();
            }
        }

        private void cleanup() {
            targets = new HashMap<>();
            lastAddedTargetName = null;
            callInstructions = new HashMap<>();
        }

    }

    protected TruffleInliningTestScenarioBuilder builder = new TruffleInliningTestScenarioBuilder();

    void assertInlined(TruffleInlining decisions, String name) {
        assertTrue(countInlines(decisions, name) > 0, name + " was not inlined!");
    }

    void assertNotInlined(TruffleInlining decisions, String name) {
        assertTrue(countInlines(decisions, name) == 0, name + " was inlined!");
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
            if (d.isInline() && d.getTarget().toString().equals(name)) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static OptionValue.OverrideScope scope = null;

    @BeforeClass
    public static void before() {
        scope = OptionValue.override(TruffleCompilerOptions.TruffleCompilationThreshold, Integer.MAX_VALUE);
    }

    @AfterClass
    public static void after() {
        scope.close();
    }
}
