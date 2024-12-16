package com.oracle.svm.hosted.analysis.ai.fixpoint.wto;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

public record WtoVertex(HIRBlock block) implements WtoComponent {
    @Override
    public String toString() {
        return block.toString();
    }
}