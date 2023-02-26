/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.util.Writer;

/**
 * Represents a method in the inlining tree, which is a candidate for inlining. The method might
 * either get inlined or not, which is reflected in the {@link #isPositive() positive} field. The
 * children ({@link #getChildren()}) of a method in the inlining tree are methods that are called by
 * this method and were considered for inlining to the method's body.
 */
public class InliningTreeNode extends TreeNode<InliningTreeNode> implements Comparable<InliningTreeNode> {

    /**
     * If the target method name is {@code null}, display this string instead.
     */
    public static final String UNKNOWN_NAME = "unknown";

    /**
     * The prefix for inlining candidates that were not inlined.
     */
    public static final String NOT_INLINED_PREFIX = "(not inlined) ";

    public static final String AT_BCI = " at bci ";

    /**
     * The bci of the parent method's callsite of this inlinee.
     */
    private final int bci;

    /**
     * The result of the inlining decision, i.e. {@code true} if the target method was inlined.
     */
    private final boolean positive;

    /**
     * The reasoning for this inlining decision. The compiler might assess the inlining of a
     * candidate method several times and can reach several negative decisions before finally
     * inlining it. This list contains the reasons for the decisions in their original order.
     */
    private final List<String> reason;

    /**
     * Constructs an inlining tree node.
     *
     * @param targetMethodName the name of this inlined method
     * @param bci the bci of the parent method's callsite of this inlinee
     * @param positive {@code true} if the target method was inlined
     * @param reason the reasoning for this inlining decision
     */
    public InliningTreeNode(String targetMethodName, int bci, boolean positive, List<String> reason) {
        super(targetMethodName);
        this.bci = bci;
        this.positive = positive;
        this.reason = reason == null ? List.of() : reason;
    }

    /**
     * Gets the bci of the parent method's callsite of this inlinee.
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
     * Gets the reasoning for this inlining decision.
     */
    public List<String> getReason() {
        return reason;
    }

    @Override
    public void writeHead(Writer writer) {
        if (!positive) {
            writer.write(NOT_INLINED_PREFIX);
        }
        writer.writeln((getName() == null ? UNKNOWN_NAME : getName()) + AT_BCI + bci);
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
        return bci == other.bci && Objects.equals(getName(), other.getName()) && positive == other.positive &&
                        Objects.equals(reason, other.reason) && getChildren().equals(other.getChildren());
    }

    @Override
    public int hashCode() {
        int result = getName() == null ? 0 : getName().hashCode();
        result = 31 * result + bci;
        result = 31 * result + (getChildren() == null ? 0 : getChildren().hashCode());
        return result;
    }

    /**
     * Compares this inlining tree node to another inlining tree node. The nodes are compared
     * lexicographically according to their {@link #getBCI() bci},{@link #getName() name} and
     * {@link #isPositive() positivity} (in this order).
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
        return (positive ? 1 : 0) - (other.positive ? 1 : 0);
    }
}
