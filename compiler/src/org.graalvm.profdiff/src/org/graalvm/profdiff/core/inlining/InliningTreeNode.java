/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.inlining;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.optimization.Optimization;

/**
 * Represents a callsite in the inlining tree.
 *
 * The name of the tree node is the name of target method. The callsite may either be inlined or
 * not, which is reflected in the {@link #isPositive() positive} field. A callsite represents an
 * invoke node in Graal IR. The {@link #getChildren() children} are the methods invoked in the
 * target method's body. If the callsite is {@link #indirect}, its children are the devirtualized
 * callsites. In a compilation unit, the root of the inlining tree is the root-compiled method.
 */
public class InliningTreeNode extends TreeNode<InliningTreeNode> implements Comparable<InliningTreeNode> {
    /**
     * Characterizes the kind of callsite. Each inlining-tree node can be characterized as one of
     * the callsite kinds.
     */
    public enum CallsiteKind {
        /**
         * The root callsite. This is the kind of the root method in the inlining tree. The bci of
         * the root is always {@link Optimization#UNKNOWN_BCI}.
         */
        Root,

        /**
         * An inlined callsite. The callsite is not the root, is inlined (the decision is
         * {@link #positive}). We can assume that the invoke node was deleted (it is not
         * {@link #alive}), because the inliner should always delete it.
         */
        Inlined,

        /**
         * A direct callsite. The invoke is not deleted (it is {@link #alive}), the callsite is not
         * inlined, and the call is direct.
         */
        Direct,

        /**
         * An indirect callsite. The invoke is not deleted (it is {@link #alive}), the callsite is
         * not inlined, and the call is indirect.
         */
        Indirect,

        /**
         * A deleted callsite. The callsite is not inlined, the invoke is deleted (it is not
         * {@link #alive}), but the callsite cannot be considered {@link #Devirtualized} (i.e., it
         * is either not {@link #indirect} or does not have children).
         */
        Deleted,

        /**
         * A devirtualized callsite. The callsite is {@link #indirect}, has children in the inlining
         * tree (representing the devirtualized callees), and the invoke is deleted (it is not
         * {@link #alive}).
         */
        Devirtualized;

        /**
         * Returns the label of this callsite kind (e.g. "root", "inlined").
         */
        public String label() {
            return toString().toLowerCase();
        }

        /**
         * Returns the prefix for this callsite kind (e.g. "(root) ", "(inlined) ").
         */
        public String prefix() {
            return '(' + label() + ") ";
        }

        /**
         * Returns a string that represents a changed callsite kind (e.g., "(inlined -> direct) ").
         *
         * @param from the original callsite kind
         * @param to the new callsite kind
         * @return a string that represents a changed callsite kind
         */
        public static String change(CallsiteKind from, CallsiteKind to) {
            return '(' + from.label() + " -> " + to.label() + ") ";
        }
    }

    /**
     * If the target method name is {@code null}, display this string instead.
     */
    public static final String UNKNOWN_NAME = "unknown";

    public static final String AT_BCI = " at bci ";

    /**
     * A phrase introducing the reasons for an inlining decision.
     */
    public static final String REASONING = "|_ reasoning";

    /**
     * A phrase used when there are no inlining decisions.
     */
    public static final String NO_INLINING_DECISIONS = "|_ no inlining decisions";

    public static final String IN_EXPERIMENT = " in experiment ";

    /**
     * The bci of the parent method's callsite of this inlinee.
     */
    private final int bci;

    /**
     * The result of the inlining decision, i.e. {@code true} if the target method was inlined.
     */
    private final boolean positive;

    /**
     * The reasoning for inlining decisions. The compiler might assess the inlining of a candidate
     * method several times and can reach several negative decisions before finally inlining it.
     * This list contains the reasons for the decisions in their original order.
     */
    private final List<String> reason;

    /**
     * {@code true} iff the call is indirect. This property can change during the lifetime of a
     * callsite. The field captures the state at the time of the last inlining decision.
     */
    private final boolean indirect;

    /**
     * A receiver-type profile for the callsite which corresponds to this node.
     */
    private final ReceiverTypeProfile receiverTypeProfile;

    /**
     * {@code true} iff the callsite was alive (not deleted) after all optimization passes.
     */
    private final boolean alive;

    /**
     * Constructs an inlining tree node.
     *
     * @param targetMethodName the name of the target method (at the time of the last inlining
     *            decision)
     * @param bci the bci of the parent method's callsite of this inlinee
     * @param positive {@code true} if the target method was inlined
     * @param reason the reasoning for this inlining decision
     * @param indirect {@code true} if the call is indirect (at the time of the last inlining
     *            decision)
     * @param receiverTypeProfile the receiver-type profile of the callsite
     * @param alive {@code true} iff the callsite was not deleted after all optimization passes
     */
    public InliningTreeNode(String targetMethodName, int bci, boolean positive, List<String> reason, boolean indirect, ReceiverTypeProfile receiverTypeProfile, boolean alive) {
        super(targetMethodName);
        this.bci = bci;
        this.positive = positive;
        this.reason = reason == null ? List.of() : reason;
        this.indirect = indirect;
        this.receiverTypeProfile = receiverTypeProfile;
        this.alive = alive;
    }

    /**
     * Gets the bci of this callsite in the parent method.
     */
    public int getBCI() {
        return bci;
    }

    /**
     * Gets the result of the inlining decision, i.e. {@code true} if the target method was inlined
     */
    public boolean isPositive() {
        return positive;
    }

    /**
     * Returns {@code true} if the call is indirect.
     */
    public boolean isIndirect() {
        return indirect;
    }

    /**
     * Gets the reasoning of all inlining decisions at this callsite.
     */
    public List<String> getReason() {
        return reason;
    }

    /**
     * Returns {@code true} iff the callsite was not deleted after all optimization passes.
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * Returns the kind of callsite represented by this inlining-tree node.
     */
    public CallsiteKind getCallsiteKind() {
        if (getParent() == null) {
            return CallsiteKind.Root;
        }
        if (positive) {
            assert !alive : "an inlined node cannot be alive";
            return CallsiteKind.Inlined;
        }
        if (alive) {
            if (indirect) {
                return CallsiteKind.Indirect;
            }
            return CallsiteKind.Direct;
        }
        if (indirect && !getChildren().isEmpty()) {
            return CallsiteKind.Devirtualized;
        }
        return CallsiteKind.Deleted;
    }

    @Override
    public void writeHead(Writer writer) {
        writer.write(getCallsiteKind().prefix());
        writer.write(getName() == null ? UNKNOWN_NAME : getName());
        if (bci == Optimization.UNKNOWN_BCI) {
            writer.writeln();
        } else {
            writer.writeln(AT_BCI + bci);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof InliningTreeNode)) {
            return false;
        }
        InliningTreeNode other = (InliningTreeNode) object;
        return bci == other.bci && indirect == other.indirect && positive == other.positive &&
                        alive == other.alive &&
                        Objects.equals(getName(), other.getName()) &&
                        Objects.equals(reason, other.reason) &&
                        Objects.equals(receiverTypeProfile, other.receiverTypeProfile) &&
                        getChildren().equals(other.getChildren());
    }

    @Override
    public int hashCode() {
        return Objects.hash(bci, getName(), getChildren(), positive, reason, indirect, receiverTypeProfile, alive);
    }

    /**
     * Compares this inlining tree node to another inlining tree node. The nodes are compared
     * lexicographically according to their {@link #getBCI() bci},{@link #getName() name} and
     * {@link #isPositive() positivity}, {@link #isAlive() liveness}, and the number of
     * {@link #reason inlining decisions} (in this order).
     *
     * @param other the object to be compared
     * @return the result of the comparison
     */
    @Override
    public int compareTo(InliningTreeNode other) {
        int order = bci - other.bci;
        if (order != 0) {
            return order;
        }
        order = Comparator.nullsFirst(String::compareTo).compare(getName(), other.getName());
        if (order != 0) {
            return order;
        }
        order = (positive ? 0 : 1) - (other.positive ? 0 : 1);
        if (order != 0) {
            return order;
        }
        order = (alive ? 0 : 1) - (other.alive ? 0 : 1);
        if (order != 0) {
            return order;
        }
        return other.reason.size() - reason.size();
    }

    /**
     * Creates and returns an inlining path element representing this callsite.
     */
    public InliningPath.PathElement pathElement() {
        return new InliningPath.PathElement(getName(), getBCI());
    }

    /**
     * Writes the reasoning for the inlining decision, optionally including an {@link ExperimentId},
     * if it is {@link OptionValues#shouldAlwaysPrintInlinerReasoning() always enabled} and this is
     * not the root method.
     *
     * @param writer the destination writer
     * @param experimentId an optional experiment ID
     */
    public void writeReasoningIfEnabled(Writer writer, ExperimentId experimentId) {
        if (writer.getOptionValues().shouldAlwaysPrintInlinerReasoning() && getParent() != null) {
            writeReasoning(writer, experimentId);
        }
    }

    /**
     * Writes the reasoning for the inlining decisions, optionally including an
     * {@link ExperimentId}.
     *
     * @param writer the destination writer
     * @param experimentId an optional experiment ID
     */
    public void writeReasoning(Writer writer, ExperimentId experimentId) {
        writer.increaseIndent();
        if (reason.isEmpty()) {
            writer.write(NO_INLINING_DECISIONS);
        } else {
            writer.write(REASONING);
        }
        if (experimentId != null) {
            writer.write(IN_EXPERIMENT + experimentId);
        }
        if (reason.size() == 1) {
            writer.write(": ");
        } else {
            writer.writeln();
        }
        writer.increaseIndent(2);
        for (String reasonString : reason) {
            writer.writeln(reasonString);
        }
        writer.decreaseIndent(3);
    }

    /**
     * Gets the receiver-type profile of this callsite.
     */
    public ReceiverTypeProfile getReceiverTypeProfile() {
        return receiverTypeProfile;
    }

    /**
     * Writes the {@link ReceiverTypeProfile} if it is not empty, including its maturity and
     * optionally and experiment ID.
     *
     * @param writer the destination writer
     * @param experimentId an optional experiment ID
     */
    public void writeReceiverTypeProfile(Writer writer, ExperimentId experimentId) {
        if (receiverTypeProfile == null || (receiverTypeProfile.isMature() && receiverTypeProfile.profiledTypes().isEmpty())) {
            return;
        }
        writer.increaseIndent();
        writer.write("|_");
        if (!receiverTypeProfile.isMature()) {
            writer.write(" immature");
        }
        writer.write(" receiver-type profile");
        if (experimentId == null) {
            writer.writeln();
        } else {
            writer.writeln(" in experiment " + experimentId);
        }
        writer.increaseIndent(2);
        receiverTypeProfile.write(writer);
        writer.decreaseIndent(3);
    }
}
