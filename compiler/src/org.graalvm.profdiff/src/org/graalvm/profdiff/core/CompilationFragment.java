package org.graalvm.profdiff.core;

import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.parser.experiment.ExperimentParserError;

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

    public int getFragmentId() {
        return fragmentId;
    }

    public CompilationUnit getParentCompilationUnit() {
        return parentCompilationUnit;
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
