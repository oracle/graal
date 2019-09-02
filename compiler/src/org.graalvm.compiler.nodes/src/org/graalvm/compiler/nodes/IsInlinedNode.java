package org.graalvm.compiler.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

@NodeInfo(shortName = "Inlined?", nameTemplate = "Inlined?")
public class IsInlinedNode extends FloatingNode implements Lowerable {
    public static final NodeClass<IsInlinedNode> TYPE = NodeClass.create(IsInlinedNode.class);

    protected IsInlinedNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
    }

    public static IsInlinedNode create() {
        return new IsInlinedNode();
    }

    public void handleInlined() {
        replaceAtUsagesAndDelete(graph().unique(ConstantNode.forBoolean(true)));
    }
    @Override
    public void lower(LoweringTool tool) {
        replaceAtUsagesAndDelete(graph().unique(ConstantNode.forBoolean(false)));
    }
}
