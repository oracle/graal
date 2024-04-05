package jdk.graal.compiler.nodes;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

@NodeInfo
public final class IncompatibleStateMergeNode extends AbstractMergeNode {
    public static final NodeClass<IncompatibleStateMergeNode> TYPE = NodeClass.create(IncompatibleStateMergeNode.class);

    public static AbstractMergeNode convertAndReplace(AbstractMergeNode toConvert) {
        IncompatibleStateMergeNode converted = toConvert.graph().add(new IncompatibleStateMergeNode());
        for (EndNode forwardEnd : toConvert.forwardEnds()) {
            converted.addForwardEnd(forwardEnd);
        }
        toConvert.graph().replaceFixedWithFixed(toConvert, converted);
        return converted;
    }

    protected IncompatibleStateMergeNode() {
        super(TYPE);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        assert ends.size() > 0 : "IncompatibleStateMergeNode has 0 ends but expects at least one end!";
        if (ends.size() == 1) {
            EndNode end = ends.get(0);
            setStateAfter(null);
            FixedNode sux = next();
            setNext(null);
            end.replaceAtPredecessor(sux);
            deleteEnd(end);
            end.safeDelete();
            safeDelete();
        } else {
            // TODO revert to initial node? loopbegin?
        }
    }

    @Override
    public boolean verifyNode() {
        return true;
    }
}
