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

import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.parser.ExperimentParserError;

import java.util.List;

/**
 * Represents a fragment of a full compilation unit in terms of optimization scope.
 *
 * A fragment is created from the parent compilation unit's inlining tree and optimization tree. A
 * subtree of the inlining tree becomes the inlining tree of the newly created fragment. The
 * optimization tree of the fragment is created by cloning all optimization phases of the
 * compilation unit's optimization tree and cloning individual optimizations whose position is in
 * the fragment's inlining subtree. This happens lazily only when the fragment's trees are
 * {@link #loadTrees() loaded}.
 *
 * Proftool provides execution data on the granularity of compilation units. For that reason, the
 * reported execution fraction of a compilation fragment is inherited from its parent compilation
 * unit.
 */
public class CompilationFragment extends CompilationUnit {
    /**
     * The ID that will be assigned the fragment created next.
     */
    private static int nextFragmentId = 0;

    /**
     * The compilation unit from which this fragment was created.
     */
    private final CompilationUnit parentCompilationUnit;

    /**
     * The index of the root method of this fragment in the inlining tree. The parent compilation
     * unit and the index define the fragment.
     */
    private final List<Integer> index;

    /**
     * The path to root in the parent compilation unit's inlining tree. The path does not define the
     * fragment precisely, because a path can lead to multiple nodes in an inlining tree.
     */
    private final InliningPath pathFromRoot;

    /**
     * The ID of this fragment.
     */
    private final int fragmentId;

    /**
     * Constructs a compilation fragment.
     *
     * @param rootFragmentMethod the root method of this fragment
     * @param parentCompilationUnit the compilation unit from which this fragment was created
     * @param rootNode the root inlining node of this compilation fragment
     */
    @SuppressWarnings("this-escape")
    public CompilationFragment(Method rootFragmentMethod, CompilationUnit parentCompilationUnit, InliningTreeNode rootNode) {
        super(rootFragmentMethod, parentCompilationUnit.getCompilationId(), parentCompilationUnit.getPeriod(), null);
        this.parentCompilationUnit = parentCompilationUnit;
        this.index = rootNode.getIndex();
        this.pathFromRoot = InliningPath.fromRootToNode(rootNode);
        this.fragmentId = ++nextFragmentId;
        setHot(parentCompilationUnit.isHot());
    }

    /**
     * Gets the compilation ID of this compilation fragment. Includes the compilation ID of the
     * parent compilation unit, followed by {@code "#"}, and the fragment ID, e.g. {@code "123#42"}.
     *
     * @return the compilation ID
     */
    @Override
    public String getCompilationId() {
        return super.getCompilationId() + "#" + fragmentId;
    }

    @Override
    protected String getCompilationKind() {
        return "fragment";
    }

    /**
     * Gets the path from root to the root of this fragment in the inlining tree of the parent's
     * compilation unit.
     */
    public InliningPath getPathFromRoot() {
        return pathFromRoot;
    }

    @Override
    public TreePair loadTrees() throws ExperimentParserError {
        TreePair treePair = parentCompilationUnit.loadTrees();
        InliningTree inliningTree = treePair.getInliningTree().cloneSubtreeAt(index);
        OptimizationTree optimizationTree = new OptimizationTree(treePair.getOptimizationTree().getRoot().cloneMatchingPath(pathFromRoot));
        return new TreePair(optimizationTree, inliningTree);
    }
}
