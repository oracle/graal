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

import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
 * <li>the inlining log of the inlined graph, or {@code null} if the decision was negative</li>
 * </ul>
 *
 * A phase that does inlining should use the instance of this class contained in the
 * {@link StructuredGraph} by calling {@link #addDecision} whenever it decides to inline a method.
 * If there are invokes in the graph at the end of the respective phase, then that phase must call
 * {@link #addDecision} to log negative decisions.
 */
public class InliningLog {
    public static final class Decision {
        private final boolean positive;
        private final String reason;
        private final String phase;
        private final ResolvedJavaMethod target;

        private Decision(boolean positive, String reason, String phase, ResolvedJavaMethod target) {
            this.positive = positive;
            this.reason = reason;
            this.phase = phase;
            this.target = target;
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

        public ResolvedJavaMethod getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return String.format("%s - %s at %s:", positive ? "yes" : "no ", phase, target.format("%H.%n(%p)"), reason);
        }
    }

    private class Callsite {
        public final List<Decision> decisions;
        public final List<Callsite> children;
        public Callsite parent;
        private Invokable originalInvoke;

        Callsite(Callsite parent, Invokable originalInvoke) {
            this.parent = parent;
            this.decisions = new ArrayList<>();
            this.children = new ArrayList<>();
            this.originalInvoke = originalInvoke;
        }

        public Callsite addChild(Invokable childInvoke) {
            Callsite child = new Callsite(this, childInvoke);
            children.add(child);
            return child;
        }

        public String positionString() {
            if (originalInvoke == null) {
                return "<root>";
            }
            ResolvedJavaMethod targetMethod = parent.getOriginalInvoke() == null ? rootMethod : parent.getOriginalInvoke().getTargetMethod();
            return MetaUtil.appendLocation(new StringBuilder(100), targetMethod, getBci()).toString();
        }

        public Invokable getOriginalInvoke() {
            return originalInvoke;
        }

        public void setOriginalInvoke(Invokable originalInvoke) {
            this.originalInvoke = originalInvoke;
        }

        public int getBci() {
            return originalInvoke != null ? originalInvoke.bci() : -1;
        }
    }

    private ResolvedJavaMethod rootMethod;
    private final Callsite root;
    private final EconomicMap<Invokable, Callsite> leaves;

    public InliningLog(ResolvedJavaMethod rootMethod) {
        this.rootMethod = rootMethod;
        this.root = new Callsite(null, null);
        this.leaves = EconomicMap.create();
    }

    public void addDecision(Invokable invoke, boolean positive, String reason, String phase, EconomicMap<Node, Node> duplicationMap, InliningLog calleeLog) {
        assert leaves.containsKey(invoke);
        assert (!positive && duplicationMap == null && calleeLog == null) || (positive && duplicationMap != null && calleeLog != null);
        Callsite callsite = leaves.get(invoke);
        Decision decision = new Decision(positive, reason, phase, invoke.getTargetMethod());
        callsite.decisions.add(decision);
        leaves.removeKey(invoke);
        if (positive) {
            MapCursor<Invokable, Callsite> entries = calleeLog.leaves.getEntries();
            while (entries.advance()) {
                Invokable invokeFromCallee = entries.getKey();
                Callsite callsiteFromCallee = entries.getValue();
                Invokable inlinedInvokeFromCallee = (Invokable) duplicationMap.get(invokeFromCallee.asFixedNode());
                callsiteFromCallee.setOriginalInvoke(inlinedInvokeFromCallee);
                leaves.put(inlinedInvokeFromCallee, callsiteFromCallee);
            }
            for (Callsite child : calleeLog.root.children) {
                child.parent = callsite;
                callsite.children.add(child);
            }
        }
    }

    private UpdateScope activated = null;

    public class UpdateScope implements AutoCloseable {
        private BiConsumer<Invokable, Invokable> updater;

        public UpdateScope(BiConsumer<Invokable, Invokable> updater) {
            this.updater = updater;
            if (activated != null) {
                throw GraalError.shouldNotReachHere("InliningLog updating already set.");
            }
            activated = this;
        }

        @Override
        public void close() {
            assert activated != null;
            activated = null;
        }

        public BiConsumer<Invokable, Invokable> getUpdater() {
            return updater;
        }
    }

    public BiConsumer<Invokable, Invokable> getUpdateScope() {
        if (activated == null) {
            return null;
        }
        return activated.getUpdater();
    }

    public UpdateScope createUpdateScope(BiConsumer<Invokable, Invokable> updater) {
        return new UpdateScope(updater);
    }

    public boolean containsLeafCallsite(Invokable invokable) {
        return leaves.containsKey(invokable);
    }

    public void removeLeafCallsite(Invokable invokable) {
        leaves.removeKey(invokable);
    }

    public void trackNewCallsite(Invokable invoke) {
        assert !leaves.containsKey(invoke);
        Callsite callsite = new Callsite(root, invoke);
        root.children.add(callsite);
        leaves.put(invoke, callsite);
    }

    public void trackDuplicatedCallsite(Invokable sibling, Invokable newInvoke) {
        Callsite siblingCallsite = leaves.get(sibling);
        Callsite parentCallsite = siblingCallsite.parent;
        Callsite callsite = parentCallsite.addChild(newInvoke);
        leaves.put(newInvoke, callsite);
    }

    public void updateExistingCallsite(Invokable previousInvoke, Invokable newInvoke) {
        Callsite callsite = leaves.get(previousInvoke);
        leaves.removeKey(previousInvoke);
        leaves.put(newInvoke, callsite);
        callsite.setOriginalInvoke(newInvoke);
    }

    public String formatAsTree() {
        StringBuilder builder = new StringBuilder(512);
        formatAsTree(root, "", builder);
        return builder.toString();
    }

    private void formatAsTree(Callsite site, String indent, StringBuilder builder) {
        String position = site.positionString();
        String decision = String.join(System.lineSeparator() + indent + "    ", site.decisions.stream().map(d -> d.toString()).collect(Collectors.toList()));
        builder.append(indent).append(position).append(": ").append(decision).append(System.lineSeparator());
        for (Callsite child : site.children) {
            formatAsTree(child, indent + "  ", builder);
        }
    }
}
