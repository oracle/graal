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
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.parser.experiment.ExperimentParserError;
import org.graalvm.profdiff.util.StdoutWriter;

public class CompilationFragment extends CompilationUnit {

    private static int nextFragmentId = 0;

    private final CompilationUnit parentCompilationUnit;

    private final InliningPath pathFromRoot;

    private final int fragmentId;

    public CompilationFragment(Method rootFragmentMethod, CompilationUnit parentCompilationUnit, InliningPath pathFromRoot) {
        super(rootFragmentMethod, parentCompilationUnit.getCompilationId(), parentCompilationUnit.getPeriod(), null);
        this.parentCompilationUnit = parentCompilationUnit;
        this.pathFromRoot = pathFromRoot;
        fragmentId = ++nextFragmentId;
        setHot(parentCompilationUnit.isHot());
    }

    @Override
    public String getCompilationId() {
        return super.getCompilationId() + "#" + fragmentId;
    }

    public InliningPath getPathFromRoot() {
        return pathFromRoot;
    }

    @Override
    public TreePair loadTrees() throws ExperimentParserError {
        TreePair treePair = parentCompilationUnit.loadTrees();
        InliningTree inliningTree = treePair.getInliningTree().cloneSubtreeAt(pathFromRoot);
        OptimizationTree optimizationTree = new OptimizationTree(treePair.getOptimizationTree().getRoot().cloneMatchingPath(pathFromRoot));
        return new TreePair(optimizationTree, inliningTree);
    }
}
