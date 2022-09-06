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
package org.graalvm.profdiff.core;

import org.graalvm.profdiff.util.Writer;

/**
 * Represents a method in the inlining tree. The children (getChildren()) of a method in the
 * inlining tree are methods that were inlined in the method's body.
 */
public class InliningTreeNode extends TreeNode<InliningTreeNode> implements Comparable<InliningTreeNode> {

    /**
     * The bci of the parent method's callsite of this inlinee.
     */
    private final int bci;

    /**
     * Constructs an inlining tree node.
     *
     * @param targetMethodName the name of this inlined method
     * @param bci the bci of the parent method's callsite of this inlinee
     */
    public InliningTreeNode(String targetMethodName, int bci) {
        super(targetMethodName);
        this.bci = bci;
    }

    /**
     * Gets the bci of the parent method's callsite of this inlinee.
     */
    public int getBCI() {
        return bci;
    }

    @Override
    public void writeHead(Writer writer) {
        writer.writeln(getName() + " at bci " + bci);
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
        return bci == other.bci && getName().equals(other.getName()) && getChildren().equals(other.getChildren());
    }

    @Override
    public int hashCode() {
        int result = getName().hashCode();
        result = 31 * result + bci;
        result = 31 * result + (getChildren() != null ? getChildren().hashCode() : 0);
        return result;
    }

    /**
     * Compares this inlining tree node to another inlining tree node. The nodes are compared
     * lexicographically according to their {@link #getBCI() bci} and {@link #getName() name} (in
     * this order).
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
        return getName().compareTo(other.getName());
    }
}
