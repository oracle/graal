package org.graalvm.compiler.nodes.util;

import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.interpreter.value.InterpreterValue;
import org.graalvm.compiler.interpreter.value.InterpreterValueFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ValueNode;

import java.util.List;

public interface InterpreterState {
    // TODO: proper JavaDocs

    // Used for nodes such NewArrayNode and NewInstanceNode
    void setHeapValue(Node node, InterpreterValue value);

    InterpreterValue getHeapValue(Node node);

    // Used to associate a control flow node with a value (when its looked up in a dataflow context)
    // after interpreting.
    void setNodeLookupValue(Node node, InterpreterValue value);

    InterpreterValue getNodeLookupValue(Node node);

    void setMergeNodeIncomingIndex(AbstractMergeNode node, int index);

    void visitMerge(AbstractMergeNode node);

    // Used for LoadFieldNode and StoreFieldNodes
    InterpreterValue loadStaticFieldValue(ResolvedJavaField field);

    void storeStaticFieldValue(ResolvedJavaField field, InterpreterValue value);

    // Used for ParameterNode
    InterpreterValue getParameter(int index);

    // Called by any node that needs to get the dataflow value of another node to use in its own
    // interpretation
    InterpreterValue interpretDataflowNode(Node node);

    // used by InvokeNode to evaluate a call target.
    InterpreterValue interpretMethod(CallTargetNode target, List<ValueNode> argumentNodes);

    InterpreterValueFactory getRuntimeValueFactory();
}
