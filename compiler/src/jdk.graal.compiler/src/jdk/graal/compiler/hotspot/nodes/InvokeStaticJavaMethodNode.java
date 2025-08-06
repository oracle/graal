package jdk.graal.compiler.hotspot.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;
import static jdk.graal.compiler.nodes.Invoke.CYCLES_UNKNOWN_RATIONALE;
import static jdk.graal.compiler.nodes.Invoke.SIZE_UNKNOWN_RATIONALE;

import java.util.Map;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.Invokable;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * A node for invoking a Java method out of line from the surrounding Java bytecode. The current use
 * cases are:
 * <ul>
 * <li>Invoking a Java method from a Truffle safepoint ({@code TruffleSafePointNode})</li>
 * <li>Performing an {@code acmp} (equality) comparison for value objects in Valhalla by calling a
 * Java method. For future use in HotSpot, a {@link StateSplitProxyNode} must be inserted with the
 * state before the {@code acmp}'s pop operation. The call site resolution requires the BCI of the
 * {@code acmp} bytecode.</li>
 * </ul>
 * Compared to an {@link Invoke} node, the node is side effect free and the call does not originate
 * from the associated bci. The target method must be static and is bound to the call site to
 * override the bytecode based call site resolution.
 */
// @formatter:off
@NodeInfo(nameTemplate = "InvokeJavaMethod#{p#targetMethod/s}",
        cycles = CYCLES_UNKNOWN, cyclesRationale = CYCLES_UNKNOWN_RATIONALE,
        size   = SIZE_UNKNOWN,   sizeRationale   = SIZE_UNKNOWN_RATIONALE)
// @formatter:on
@NodeIntrinsicFactory
public class InvokeStaticJavaMethodNode extends DeoptimizingFixedWithNextNode implements Invokable, Lowerable {
    public static final NodeClass<InvokeStaticJavaMethodNode> TYPE = NodeClass.create(InvokeStaticJavaMethodNode.class);

    /**
     * Enables passing a method (to be invoked by this node) as a constant within an intrinsic
     * context. This is required when the specific method to invoke is known, but the corresponding
     * {@link ResolvedJavaMethod} instance is not available at the time of
     * {@link InvokeStaticJavaMethodNode} creation. See
     * {@code HotSpotTruffleSafepointLoweringSnippet#pollSnippet} for usage details.
     */
    public static final class MethodReference {
        private final String name;
        private ResolvedJavaMethod targetMethod;

        public MethodReference(String name) {
            this.name = name;
        }

        public void setTargetMethod(ResolvedJavaMethod targetMethod) {
            this.targetMethod = targetMethod;
        }

        public String getName() {
            return name;
        }
    }

    public static final MethodReference TRUFFLE_SAFEPOINT = new MethodReference("HotSpotThreadLocalHandshake.doHandshake");

    @Input protected NodeInputList<ValueNode> arguments;

    private int bci;
    private final ResolvedJavaMethod callerMethod;
    private ResolvedJavaMethod targetMethod;
    private final CallTargetNode.InvokeKind invokeKind;
    private final StampPair returnStamp;
    private MethodReference methodReference;

    @SuppressWarnings("this-escape")
    protected InvokeStaticJavaMethodNode(NodeClass<? extends InvokeStaticJavaMethodNode> c, ResolvedJavaMethod targetMethod, ResolvedJavaMethod callerMethod,
                    StampPair returnStamp, int bci, ValueNode... args) {
        super(c, returnStamp.getTrustedStamp());
        this.arguments = new NodeInputList<>(this, args);
        this.bci = bci;
        this.callerMethod = callerMethod;
        this.targetMethod = targetMethod;
        this.returnStamp = returnStamp;
        this.invokeKind = CallTargetNode.InvokeKind.Static;
    }

    public static InvokeStaticJavaMethodNode create(MethodReference methodReference, ResolvedJavaMethod callerMethod, int bci, ValueNode... args) {
        verifyTargetMethod(methodReference.targetMethod);
        ResolvedJavaMethod targetMethod = methodReference.targetMethod;
        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        return new InvokeStaticJavaMethodNode(TYPE, targetMethod, callerMethod, StampPair.createSingle(StampFactory.forKind(returnKind)), bci, args);
    }

    public static InvokeStaticJavaMethodNode create(GraphBuilderContext b, ResolvedJavaMethod targetMethod, int bci, ValueNode... args) {
        Signature signature = targetMethod.getSignature();
        JavaType returnType = signature.getReturnType(null);
        StructuredGraph graph = b.getGraph();
        StampPair returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, false);
        return create(b, targetMethod, b.getMethod(), returnStamp, bci, args);
    }

    private static InvokeStaticJavaMethodNode createWithoutTargetMethod(GraphBuilderContext b, MethodReference methodReference, Stamp returnStamp, int bci, ValueNode... args) {
        InvokeStaticJavaMethodNode invoke = create(b, null, b.getMethod(), StampPair.createSingle(returnStamp), bci, args);
        invoke.methodReference = methodReference;
        return invoke;
    }

    private static InvokeStaticJavaMethodNode create(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ResolvedJavaMethod callerMethod,
                    StampPair returnStamp, int bci, ValueNode... args) {
        GraalError.guarantee(b.getInvokeKind() == CallTargetNode.InvokeKind.Static, "can only invoke static methods");
        return new InvokeStaticJavaMethodNode(TYPE, targetMethod, callerMethod, returnStamp, bci, args);
    }

    @Override
    public ResolvedJavaMethod getContextMethod() {
        return callerMethod;
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    public ValueNode[] toArgumentArray() {
        return arguments.toArray(ValueNode.EMPTY_ARRAY);
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        this.bci = bci;
    }

    @Override
    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    private static void verifyTargetMethod(ResolvedJavaMethod targetMethod) {
        GraalError.guarantee(targetMethod != null, "method shouldn't be null");
        GraalError.guarantee(targetMethod.isStatic(), "can only invoke static methods");
    }

    public void setTargetMethod(ResolvedJavaMethod targetMethod) {
        verifyTargetMethod(targetMethod);
        this.targetMethod = targetMethod;
    }

    public CallTargetNode.InvokeKind getInvokeKind() {
        return invokeKind;
    }

    public StampPair getReturnStamp() {
        return returnStamp;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString(verbosity));
            sb.append("#");
            if (targetMethod == null) {
                sb.append(methodReference.getName());
            } else {
                sb.append(targetMethod.format("%h.%n"));
            }
            return sb.toString();
        }
        return super.toString(verbosity);
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        debugProperties.put("targetMethod", targetMethod == null ? methodReference.getName() : targetMethod.format("%h.%n"));
        return debugProperties;
    }

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter Stamp returnStamp, MethodReference methodReference, ValueNode... args) {
        GraphBuilderContext nonIntrinsicAncestor = b.getNonIntrinsicAncestor();
        int bci = nonIntrinsicAncestor == null ? BytecodeFrame.UNKNOWN_BCI : b.bci();
        InvokeStaticJavaMethodNode invoke = InvokeStaticJavaMethodNode.createWithoutTargetMethod(b, methodReference, returnStamp, bci, args);
        JavaKind returnKind = returnStamp.getStackKind();
        if (returnKind == JavaKind.Void) {
            b.add(invoke);
        } else {
            b.addPush(returnKind, invoke);
        }
        return true;
    }

    /**
     * Calls a static Java method with one argument and no return value. The target method has to be
     * set in the {@link MethodReference}, otherwise the node cannot be lowered.
     */
    @NodeIntrinsic
    public static native void invoke(@ConstantNodeParameter MethodReference methodReference, Object arg1);

    @SuppressWarnings("try")
    public Invoke replaceWithInvoke() {
        try (DebugCloseable context = withNodeSourcePosition(); InliningLog.UpdateScope updateScope = InliningLog.openUpdateScopeTrackingReplacement(graph().getInliningLog(), this)) {
            InvokeNode invoke = createInvoke(graph());
            graph().replaceFixedWithFixed(this, invoke);
            assert invoke.verify();
            return invoke;
        }
    }

    public InvokeNode createInvoke(StructuredGraph graph) {
        MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(getInvokeKind(), getTargetMethod(), toArgumentArray(), getReturnStamp(), null));
        InvokeNode in = new InvokeNode(callTarget, bci(), MemoryKill.NO_LOCATION);
        // this node has no side effect, propagate it to the invoke node
        in.setSideEffect(false);
        InvokeNode invoke = graph.add(in);
        invoke.setStateDuring(stateBefore());
        return invoke;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            if (targetMethod == null) {
                GraalError.guarantee(methodReference != null, "method reference shouldn't be null");
                setTargetMethod(methodReference.targetMethod);
            }
            Invoke invoke = replaceWithInvoke();
            assert invoke.asNode().verify();
            invoke.lower(tool);
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

}