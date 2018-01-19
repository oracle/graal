/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains all inlining decisions performed on a graph during the compilation.
 *
 * Each inlining decision consists of:
 *
 * <ul>
 * <li>a value indicating whether the decision was positive or negative</li>
 * <li>the call target method</li>
 * <li>the reason for the inlining decision</li>
 * <li>the name of the phase in which the inlining decision took place</li>
 * <li>the special {@link BytecodePositionWithId} value that describes the position in the bytecode
 * together with the callsite-specific unique identifier</li>
 * <li>the inlining log of the inlined graph, or {@code null} if the decision was negative</li>
 * </ul>
 *
 * A phase that does inlining should use the instance of this class contained in the {@link StructuredGraph}
 * by calling {@link #addDecision} whenever it decides to inline a method. If there are invokes in the graph
 * at the end of the respective phase, then that phase must call {@link #addDecision} to log negative decisions.
 *
 * At the end of the compilation, the contents of the inlining log can be converted into a list of decisions
 * by calling {@link #formatAsList} or into an inlining tree, by calling {@link #formatAsTree}.
 */
public class InliningLog {
    /**
     * A bytecode position with a unique identifier attached.
     *
     * The purpose of this class is to disambiguate callsites that are duplicated by a transformation
     * (such as loop peeling or path duplication).
     */
    public static final class BytecodePositionWithId extends BytecodePosition implements Comparable<BytecodePositionWithId> {
        private final int id;

        public BytecodePositionWithId(BytecodePositionWithId caller, ResolvedJavaMethod method, int bci, int id) {
            super(caller, method, bci);
            this.id = id;
        }

        public BytecodePositionWithId addCallerWithId(BytecodePositionWithId caller) {
            if (getCaller() == null) {
                return new BytecodePositionWithId(caller, getMethod(), getBCI(), id);
            } else {
                return new BytecodePositionWithId(getCaller().addCallerWithId(caller), getMethod(), getBCI(), id);
            }
        }

        public static BytecodePositionWithId create(FrameState state) {
            return create(state, true);
        }

        @SuppressWarnings("deprecation")
        private static BytecodePositionWithId create(FrameState state, boolean topLevel) {
            if (state == null) {
                return null;
            }
            ResolvedJavaMethod method = state.getMethod();
            int bci = topLevel ? state.bci - 3 : state.bci;
            int id = state.getId();
            return new BytecodePositionWithId(create(state.outerFrameState(), false), method, bci, id);
        }

        @Override
        public BytecodePositionWithId getCaller() {
            return (BytecodePositionWithId) super.getCaller();
        }

        public BytecodePositionWithId withoutCaller() {
            return new BytecodePositionWithId(null, getMethod(), getBCI(), id);
        }

        public long getId() {
            return id;
        }

        @Override
        public boolean equals(Object that) {
            if (!(that instanceof BytecodePositionWithId)) {
                return false;
            }
            return super.equals(that) && this.id == ((BytecodePositionWithId) that).id;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ (id << 16);
        }

        public int compareTo(BytecodePositionWithId that) {
            int diff = this.getBCI() - that.getBCI();
            if (diff != 0) {
                return diff;
            }
            diff = (int) (this.getId() - that.getId());
            return diff;
        }
    }

    public static final class Decision {
        private final boolean positive;
        private final String reason;
        private final String phase;
        private final ResolvedJavaMethod target;
        private final BytecodePositionWithId position;
        private final InliningLog childLog;

        private Decision(boolean positive, String reason, String phase, ResolvedJavaMethod target, BytecodePositionWithId position, InliningLog childLog) {
            assert position != null;
            this.positive = positive;
            this.reason = reason;
            this.phase = phase;
            this.target = target;
            this.position = position;
            this.childLog = childLog;
        }

        public boolean isPositive() {
            return positive;
        }

        public String getReason() {
            return reason;
        }

        public String getPhase() {
            return phase;
        }

        public BytecodePositionWithId getPosition() {
            return position;
        }

        public InliningLog getChildLog() {
            return childLog;
        }

        public ResolvedJavaMethod getTarget() {
            return target;
        }
    }

    private static class Callsite {
        public final List<String> decisions;
        public final Map<BytecodePositionWithId, Callsite> children;
        public final BytecodePositionWithId position;

        Callsite(BytecodePositionWithId position) {
            this.children = new HashMap<>();
            this.position = position;
            this.decisions = new ArrayList<>();
        }

        public Callsite getOrCreateChild(BytecodePositionWithId position) {
            Callsite child = children.get(position.withoutCaller());
            if (child == null) {
                child = new Callsite(position);
                children.put(position.withoutCaller(), child);
            }
            return child;
        }

        public Callsite createCallsite(BytecodePositionWithId position, String decision) {
            Callsite parent = getOrCreateCallsite(position.getCaller());
            Callsite callsite = parent.getOrCreateChild(position);
            callsite.decisions.add(decision);
            return null;
        }

        private Callsite getOrCreateCallsite(BytecodePositionWithId position) {
            if (position == null) {
                return this;
            } else {
                Callsite parent = getOrCreateCallsite(position.getCaller());
                Callsite callsite = parent.getOrCreateChild(position);
                return callsite;
            }
        }
    }

    private final List<Decision> decisions;

    public InliningLog() {
        this.decisions = new ArrayList<>();
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public void addDecision(boolean positive, String reason, String phase, ResolvedJavaMethod target, BytecodePositionWithId position,
                    InliningLog calleeLog) {
        Decision decision = new Decision(positive, reason, phase, target, position, calleeLog);
        decisions.add(decision);
    }

    public String formatAsList() {
        StringBuilder builder = new StringBuilder();
        formatAsList("", null, decisions, builder);
        return builder.toString();
    }

    private void formatAsList(String phasePrefix, BytecodePositionWithId caller, List<Decision> decisions, StringBuilder builder) {
        for (Decision decision : decisions) {
            String phaseStack = phasePrefix.equals("") ? decision.getPhase() : phasePrefix + "-" + decision.getPhase();
            String target = decision.getTarget().format("%H.%n(%p)");
            String positive = decision.isPositive() ? "inline" : "do not inline";
            BytecodePositionWithId absolutePosition = decision.getPosition().addCallerWithId(caller);
            String position = "  " + decision.getPosition().toString().replaceAll("\n", "\n  ");
            String line = String.format("<%s> %s %s: %s\n%s", phaseStack, positive, target, decision.getReason(), position);
            builder.append(line).append(System.lineSeparator());
            if (decision.getChildLog() != null) {
                formatAsList(phaseStack, absolutePosition, decision.getChildLog().getDecisions(), builder);
            }
        }
    }

    public String formatAsTree() {
        Callsite root = new Callsite(null);
        createTree("", null, root, decisions);
        StringBuilder builder = new StringBuilder();
        formatAsTree(root, "", builder);
        return builder.toString();
    }

    private void createTree(String phasePrefix, BytecodePositionWithId caller, Callsite root, List<Decision> decisions) {
        for (Decision decision : decisions) {
            String phaseStack = phasePrefix.equals("") ? decision.getPhase() : phasePrefix + "-" + decision.getPhase();
            String target = decision.getTarget().format("%H.%n(%p)");
            BytecodePositionWithId absolutePosition = decision.getPosition().addCallerWithId(caller);
            String line = String.format("<%s> %s: %s", phaseStack, target, decision.getReason());
            root.createCallsite(absolutePosition, line);
            if (decision.getChildLog() != null) {
                createTree(phaseStack, absolutePosition, root, decision.getChildLog().getDecisions());
            }
        }
    }

    private void formatAsTree(Callsite site, String indent, StringBuilder builder) {
        String position = site.position != null ? site.position.withoutCaller().toString() : "<root>";
        String decision = String.join("; ", site.decisions);
        String line = String.format("%s%s; %s", indent, position, decision);
        builder.append(line).append(System.lineSeparator());
        String childIndent = indent + "  ";
        site.children.entrySet().stream().sorted((x, y) -> x.getKey().compareTo(y.getKey())).forEach(e -> {
            formatAsTree(e.getValue(), childIndent, builder);
        });
    }
}
