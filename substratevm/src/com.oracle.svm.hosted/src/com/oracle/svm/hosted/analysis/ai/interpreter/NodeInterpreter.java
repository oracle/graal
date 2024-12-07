package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;

public interface NodeInterpreter<Domain extends AbstractDomain<Domain>> {
    void execEdge(Node source, Node destination, AbstractStateMap<Domain> abstractStateMap);

    void exec(ConstantNode node, AbstractStateMap<Domain> abstractStateMap);

    void exec(UnaryArithmeticNode<?> node, AbstractStateMap<Domain> abstractStateMap);

    void exec(BinaryArithmeticNode<?> node, AbstractStateMap<Domain> abstractStateMap);

    void exec(ShiftNode<?> node, AbstractStateMap<Domain> abstractStateMap);

    void exec(CompareNode node, AbstractStateMap<Domain> abstractStateMap);

    void exec(ReturnNode node, AbstractStateMap<Domain> abstractStateMap);

    void exec(ValuePhiNode node, AbstractStateMap<Domain> abstractStateMap);

    void exec(FrameState state, AbstractStateMap<Domain> abstractStateMap);

    void exec(StoreFieldNode node);

    void exec(LoadFieldNode node);
}
