package com.oracle.svm.hosted.analysis.ai.fixpoint.wto;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.List;

public record WtoCycle(HIRBlock block, List<WtoComponent> components) implements WtoComponent {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(block);
        for (WtoComponent component : components) {
            sb.append(" ").append(component);
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public HIRBlock getBlock() {
        return block;
    }
}