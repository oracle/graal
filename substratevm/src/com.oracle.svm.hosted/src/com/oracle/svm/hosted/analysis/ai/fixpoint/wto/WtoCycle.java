package com.oracle.svm.hosted.analysis.ai.fixpoint.wto;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.List;

public record WtoCycle(HIRBlock block, List<WtoComponent> components) implements WtoComponent {
}