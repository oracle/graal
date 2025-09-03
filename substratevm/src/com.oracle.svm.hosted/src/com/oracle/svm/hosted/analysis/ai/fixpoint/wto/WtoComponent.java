package com.oracle.svm.hosted.analysis.ai.fixpoint.wto;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

public interface WtoComponent {

    String toString();

    HIRBlock getBlock();
}