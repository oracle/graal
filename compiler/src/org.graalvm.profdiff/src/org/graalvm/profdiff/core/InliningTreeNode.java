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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.graalvm.profdiff.util.Writer;

/**
 * Represents a method in the inlining tree. The children (inlinees) of a method in the inlining
 * tree are methods that were inlined in the method's body.
 */
public class InliningTreeNode implements TreeNode<InliningTreeNode> {
    /**
     * The name of this inlined method.
     */
    private final String targetMethodName;

    /**
     * The bci of the parent method's callsite of this inlinee.
     */
    private final int bci;

    /**
     * The list of methods inlined into this method.
     */
    private List<InliningTreeNode> inlinees;

    public InliningTreeNode(String targetMethodName, int bci) {
        this.targetMethodName = targetMethodName;
        this.bci = bci;
        this.inlinees = null;
    }

    /**
     * Gets the bci of the parent method's callsite of this inlinee.
     */
    public int getBCI() {
        return bci;
    }

    /**
     * Adds a method that was inlined in this method.
     *
     * @param inlinee the inlined method to be added
     */
    public void addInlinee(InliningTreeNode inlinee) {
        if (inlinees == null) {
            inlinees = new ArrayList<>();
        }
        inlinees.add(inlinee);
    }

    @Override
    public List<InliningTreeNode> getChildren() {
        if (inlinees == null) {
            return List.of();
        }
        return inlinees;
    }

    @Override
    public String getName() {
        return targetMethodName;
    }

    @Override
    public void writeHead(Writer writer) {
        writer.writeln(targetMethodName + " at bci " + bci);
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
        if (bci != other.bci || !targetMethodName.equals(other.targetMethodName)) {
            return false;
        }
        return Objects.equals(inlinees, other.inlinees);
    }

    @Override
    public int hashCode() {
        int result = targetMethodName.hashCode();
        result = 31 * result + bci;
        result = 31 * result + (inlinees != null ? inlinees.hashCode() : 0);
        return result;
    }
}
