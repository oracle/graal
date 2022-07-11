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
package org.graalvm.bisect.core.optimization;

import java.util.List;

import org.graalvm.bisect.util.Writer;

/**
 * Represents a node in the optimization tree. The nodes in the optimization tree are phases and
 * individual optimizations, which are always leaf nodes. The children of an optimization phase are
 * its subphases and performed optimizations.
 */
public interface OptimizationTreeNode {
    /**
     * Gets the children of this node.
     */
    List<OptimizationTreeNode> getChildren();

    /**
     * Gets the name of this node.
     */
    String getName();

    /**
     * Writes the optimization subtree starting from this phase in dfs preorder to the destination
     * writer.
     *
     * @param writer the destination writer
     */
    void writeRecursive(Writer writer);

    /**
     * Writes the representation of this node without its subtree to the destination writer.
     *
     * @param writer the destination writer
     */
    void writeHead(Writer writer);
}
