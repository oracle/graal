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
package org.graalvm.profdiff.matching.tree;

import org.graalvm.profdiff.core.TreeNode;

/**
 * Visitor of a delta tree.
 *
 * @param <T> the type of the node of the original diffed tree
 */
public interface DeltaTreeVisitor<T extends TreeNode<T>> {
    /**
     * Notifies the visitor before a delta tree is visited.
     */
    void beforeVisit();

    /**
     * Notifies the visitor after a delta tree is visited.
     */
    void afterVisit();

    /**
     * Visits a delta node that representing an identity operation.
     *
     * @param node a delta node representing an identity operation
     */
    void visitIdentity(DeltaTreeNode<T> node);

    /**
     * Visits a delta node that representing a relabeling operation.
     *
     * @param node a delta node representing a relabeling operation
     */
    void visitRelabeling(DeltaTreeNode<T> node);

    /**
     * Visits a delta node that representing a delete operation.
     *
     * @param node a delta node representing a delete operation
     */
    void visitDeletion(DeltaTreeNode<T> node);

    /**
     * Visits a delta node that representing an insert operation.
     *
     * @param node a delta node representing an insert operation
     */
    void visitInsertion(DeltaTreeNode<T> node);
}
