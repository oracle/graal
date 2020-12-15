package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Checks that {@link Node} implementing {@link IterableNodeType} use
 * {@link StructuredGraph#getNodes(org.graalvm.compiler.graph.NodeClass)} and not
 * {@linkplain NodeIterable#filter(Class)}.
 */
public class VerifyIterableNodeTypes extends VerifyPhase<CoreProviders> {

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaMethod caller = graph.method();
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType nodeIterableType = metaAccess.lookupJavaType(NodeIterable.class);
        final ResolvedJavaType classType = metaAccess.lookupJavaType(Class.class);
        final ResolvedJavaType graphType = metaAccess.lookupJavaType(Graph.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ValueNode receiver = t.receiver();
            if (receiver instanceof Invoke) {
                CallTargetNode receiverCallTarget = ((Invoke) receiver).callTarget();
                ResolvedJavaMethod receiverMethod = receiverCallTarget.targetMethod();
                if (receiverMethod.getDeclaringClass().equals(graphType) && receiverMethod.getName().equals("getNodes") && receiverMethod.getParameters().length == 0) {
                    ResolvedJavaMethod callee = t.targetMethod();
                    if (callee.getDeclaringClass().equals(nodeIterableType)) {
                        if (callee.getName().equals("filter")) {
                            ResolvedJavaMethod.Parameter params[] = callee.getParameters();
                            if (params.length == 1 && params[0].getType().equals(classType)) {
                                // call to filter
                                ValueNode v = t.arguments().get(1);
                                assert classType.isAssignableFrom(v.stamp(NodeView.DEFAULT).javaType(metaAccess)) : "Need to have a class type parameter.";
                                if (v instanceof ConstantNode) {
                                    ConstantNode c = (ConstantNode) v;
                                    JavaConstant cj = c.asJavaConstant();
                                    if (cj instanceof HotSpotObjectConstant) {
                                        HotSpotObjectConstant hob = (HotSpotObjectConstant) cj;
                                        Object classConstant = hob.asObject(classType);
                                        Class<?> clazz = (Class<?>) classConstant;
                                        if (IterableNodeType.class.isAssignableFrom(clazz)) {
                                            throw new VerificationError(
                                                            "Call to %s at callsite %s is prohibited. Argument node class %s implements IterableNodeType. Use graph.getNodes(IterableNodeType.TYPE) instead.",
                                                            callee.format("%H.%n(%p)"),
                                                            caller.format("%H.%n(%p)"), clazz);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
