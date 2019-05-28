package org.graalvm.compiler.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.spi.NodeValueMap;

import java.util.function.Predicate;

/**
 * This class represents a vector value within the graph.
 */
@NodeInfo
public abstract class VectorValueNode extends org.graalvm.compiler.graph.Node implements VectorValueNodeInterface {

    public static final NodeClass<VectorValueNode> TYPE = NodeClass.create(VectorValueNode.class);

    protected Stamp stamp;

    public VectorValueNode(NodeClass<? extends VectorValueNode> c, Stamp stamp) {
        super(c);
        this.stamp = stamp;
    }

    public final Stamp stamp() {
        return stamp;
    }

    public final void setStamp(Stamp stamp) {
        this.stamp = stamp;
        assert !isAlive() || !inferStamp() : "setStamp called on a node that overrides inferStamp: " + this;
    }

    public boolean inferStamp() {
        return false;
    }

    public final JavaKind getStackKind() {
        return stamp().getStackKind();
    }

    @Override
    public VectorValueNode asNode() {
        return this;
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        if (getStackKind() != JavaKind.Void && type == InputType.Value) {
            return true;
        } else {
            return super.isAllowedUsageType(type);
        }
    }

    public boolean hasUsagesOtherThan(ValueNode node, NodeValueMap nodeValueMap) {
        for (Node usage : usages()) {
            if (usage != node && usage instanceof ValueNode && nodeValueMap.hasOperand(usage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void replaceAtUsages(Node other, Predicate<Node> filter, Node toBeDeleted) {
        super.replaceAtUsages(other, filter, toBeDeleted);
        assert checkReplaceAtUsagesInvariants(other);
    }

    private boolean checkReplaceAtUsagesInvariants(Node other) {
        assert other == null || other instanceof VectorValueNode;
        if (this.hasUsages() && !this.stamp().isEmpty() && other != null) {
            assert ((VectorValueNode) other).stamp().getClass() == stamp().getClass() : "stamp have to be of same class";
            boolean morePrecise = ((VectorValueNode) other).stamp().join(stamp()).equals(((VectorValueNode) other).stamp());
            assert morePrecise : "stamp can only get more precise " + toString(Verbosity.All) + " " +
                    other.toString(Verbosity.All);
        }
        return true;
    }
}
