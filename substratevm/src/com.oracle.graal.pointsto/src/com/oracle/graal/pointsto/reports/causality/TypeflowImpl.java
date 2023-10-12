package com.oracle.graal.pointsto.reports.causality;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualParameterTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.ConstantTypeFlow;
import com.oracle.graal.pointsto.flow.FilterTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.NullCheckTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvent;
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvents;
import com.oracle.graal.pointsto.typestate.TypeState;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.oracle.graal.pointsto.reports.causality.CausalityExport.*;

class TypeflowImpl extends BasicImpl<TypeflowImpl.ThreadContext> {
    private final ConcurrentHashMap<Pair<TypeFlow<?>, TypeFlow<?>>, Boolean> interflows = new ConcurrentHashMap<>();

    /**
     * Saves for each virtual invocation the receiver typeflow before it may have been replaced during saturation.
     */
    private final ConcurrentHashMap<AbstractVirtualInvokeTypeFlow, TypeFlow<?>> originalInvokeReceivers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Pair<CausalityEvent, TypeFlow<?>>, HashSet<AnalysisType>> flowingFromHeap = new ConcurrentHashMap<>();

    public static final class ThreadContext extends BasicImpl.ThreadContext {
        public int currentlySaturatingDepth; // Inhibits the registration of new typeflow edges

        public final class SaturationHappeningToken implements NonThrowingAutoCloseable {
            SaturationHappeningToken() {
                currentlySaturatingDepth++;
            }

            @Override
            public void close() {
                currentlySaturatingDepth--;
                if (currentlySaturatingDepth < 0)
                    throw new RuntimeException();
            }
        }
    }

    protected TypeflowImpl() {
        super(ThreadContext::new);
    }

    public static TypeflowImpl createWithTypeflowTracking() {
        return new TypeflowImpl();
    }

    @Override
    public void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
        if (from == to)
            return;

        if (from instanceof AllInstantiatedTypeFlow)
            if (getContext().currentlySaturatingDepth > 0)
                return;

        assert getContext().currentlySaturatingDepth == 0 || to.isContextInsensitive() || from instanceof ActualReturnTypeFlow && to instanceof ActualReturnTypeFlow || to instanceof ActualParameterTypeFlow;
        interflows.put(Pair.create(from, to), Boolean.TRUE);
    }

    @Override
    public NonThrowingAutoCloseable setSaturationHappening() {
        return getContext().new SaturationHappeningToken();
    }

    @Override
    public void registerTypeEntering(PointsToAnalysis bb, CausalityEvent cause, TypeFlow<?> destination, AnalysisType type) {
        flowingFromHeap.computeIfAbsent(Pair.create(cause, destination), p -> new HashSet<>()).add(type);
    }

    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
    }

    @Override
    public void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
        if (invocation.method() != null)
            originalInvokeReceivers.put(invocation, invocation.getReceiver());
    }

    protected void forEachTypeflow(Consumer<TypeFlow<?>> callback) {
        for (var e : interflows.keySet()) {
            callback.accept(e.getLeft());
            callback.accept(e.getRight());
        }
    }

    @Override
    protected void forEachEvent(Consumer<CausalityEvent> callback) {
        super.forEachEvent(callback);

        flowingFromHeap.keySet().stream().map(Pair::getLeft).forEach(callback);

        // TODO: Unsure about this - whether it is necessary and whether it is correct/complete
        originalInvokeReceivers.keySet().stream().map(InvokeTypeFlow::getTargetMethod).flatMap(targetMethod -> Arrays.stream(targetMethod.getImplementations())).map(CausalityEvents.MethodImplementationInvoked::create).forEach(callback);

        forEachTypeflow(tf -> {
            if(tf != null) {
                CausalityEvent e = getContainingEvent(tf);
                if (e != null) {
                    callback.accept(e);
                }
            }
        });
    }

    private static BytecodePosition chopUnwindFrames(BytecodePosition p) {
        while (p != null && p.getBCI() == BytecodeFrame.UNWIND_BCI)
            p = p.getCaller();
        return p;
    }

    private static BytecodePosition takePlausibleFramesFromBottom(BytecodePosition p) {
        if (p == null)
            return null;

        BytecodePosition callerPos = takePlausibleFramesFromBottom(p.getCaller());

        if (callerPos != p.getCaller())
            return callerPos;

        if (!((AnalysisMethod) p.getMethod()).isInlined()) {
            return callerPos != null ? callerPos : new BytecodePosition(null, p.getMethod(), p.getBCI());
        }

        return p;
    }

    private static BytecodePosition takePlausibleFramesFromTop(BytecodePosition p) {
        if (p == null)
            return null;

        if (!((AnalysisMethod) p.getMethod()).isInlined())
            return new BytecodePosition(null, p.getMethod(), p.getBCI());

        BytecodePosition callerPos = takePlausibleFramesFromTop(p.getCaller());
        if (callerPos != p.getCaller())
            return new BytecodePosition(callerPos, p.getMethod(), p.getBCI());

        return p;
    }

    private static CausalityEvent getContainingEvent(TypeFlow<?> f) {
        if (f.getSource() instanceof BytecodePosition pos) {
            /*
             * For some reason the BytecodePosition assigned to a TypeFlow isn't always what you would expect from browsing the code.
             * We query AnalysisMethod.isInlined() in order to improve implausible sources.
             * Incorrect sources that are not corrected will lead to underapproximation in the Causality Graph.
             * TODO: Fix the underlying problem
             */
            if (f instanceof FilterTypeFlow || f instanceof NullCheckTypeFlow) {
                pos = takePlausibleFramesFromBottom(chopUnwindFrames(pos));
            } else if (f instanceof ConstantTypeFlow) {
                pos = takePlausibleFramesFromTop(chopUnwindFrames(pos));
            }
            return CausalityEvents.InlinedMethodCode.create(pos);
        } else {
            AnalysisMethod m = f.method();
            if (m != null) {
                return CausalityEvents.InlinedMethodCode.create(m);
            } else {
                return null;
            }
        }
    }

    private static void addSubtypeImplementations(PointsToAnalysis bb, Map<AnalysisMethod, TypeState> result, AnalysisMethod base, AnalysisType type) {
        AnalysisMethod override = type.resolveConcreteMethod(base);

        if (override != null && override.isImplementationInvoked()) {
            result.compute(override, (m, existingTypes) -> {
                if (existingTypes == null)
                    existingTypes = TypeState.forEmpty();
                return TypeState.forUnion(bb, existingTypes, TypeState.forExactType(bb, type, false));
            });
        }

        for (AnalysisType subType : type.getSubTypes()) {
            if (subType == type)
                continue;
            addSubtypeImplementations(bb, result, base, subType);
        }
    }

    /**
     * In order to save calls to {@link AnalysisType#resolveConcreteMethod(ResolvedJavaMethod)}, we precompute the mapping
     * Type -> Implementation.
     */
    private static Map<AnalysisMethod, TypeState> collectImplementationWithTypes(PointsToAnalysis bb, AnalysisMethod baseMethod) {
        Map<AnalysisMethod, TypeState> result = new HashMap<>();
        addSubtypeImplementations(bb, result, baseMethod, baseMethod.getDeclaringClass());
        return result;
    }

    @Override
    protected Graph createCausalityGraph(PointsToAnalysis bb) {
        Graph g = super.createCausalityGraph(bb);

        HashMap<TypeFlow<?>, Graph.RealFlowNode> flowMapping = new HashMap<>();

        Function<TypeFlow<?>, Graph.RealFlowNode> flowMapper = flow ->
        {
            if(flow.getState().typesCount() == 0 && !flow.isSaturated())
                return null;

            return flowMapping.computeIfAbsent(flow, f -> {
                CausalityEvent reason = getContainingEvent(f);

                if(reason != null && reason.unused())
                    return null;

                return Graph.RealFlowNode.create(bb, f, reason);
            });
        };

        Map<AnalysisMethod, Pair<Set<AbstractVirtualInvokeTypeFlow>, TypeState>> virtual_invokes = new HashMap<>();

        Map<AnalysisMethod, Map<AnalysisMethod, TypeState>> implementationsAndTheirTypes = new HashMap<>();
        for (var e : originalInvokeReceivers.keySet()) {
            implementationsAndTheirTypes.computeIfAbsent(e.getTargetMethod(), targetMethod -> collectImplementationWithTypes(bb, targetMethod));
        }

        for (var e : originalInvokeReceivers.entrySet()) {
            PointsToAnalysisMethod targetMethod = e.getKey().getTargetMethod();
            TypeState receiverState = bb.getAllInstantiatedTypeFlow().getState();
            if (e.getValue() != null && !e.getValue().isSaturated())
                receiverState = e.getValue().filter(bb, receiverState);

            for (var implementationWithTypes : implementationsAndTheirTypes.get(targetMethod).entrySet()) {
                TypeState and = TypeState.forIntersection(bb, receiverState, implementationWithTypes.getValue());
                if (and.typesCount() == 0)
                    continue;

                var calleeList = bb.getHostVM().getMultiMethodAnalysisPolicy().determineCallees(bb, PointsToAnalysis.assertPointsToAnalysisMethod(implementationWithTypes.getKey()), targetMethod, e.getKey().getCallerMultiMethodKey(), e.getKey());
                for (PointsToAnalysisMethod callee : calleeList) {
                    assert callee.getTypeFlow().getMethod().equals(callee);

                    virtual_invokes.compute(callee, (m, pair) -> {
                        Set<AbstractVirtualInvokeTypeFlow> invokes;
                        TypeState targetReachingTypes;

                        if (pair == null) {
                            invokes = new HashSet<>();
                            targetReachingTypes = TypeState.forEmpty();
                        } else {
                            invokes = pair.getLeft();
                            targetReachingTypes = pair.getRight();
                        }

                        invokes.add(e.getKey());
                        return Pair.create(invokes, TypeState.forUnion(bb, targetReachingTypes, and));
                    });
                }
            }
        }

        for (var e : virtual_invokes.entrySet()) {
            CausalityEvent reason = CausalityEvents.MethodImplementationInvoked.create(e.getKey());

            if(reason.unused())
                continue;

            Graph.InvocationFlowNode invocationFlowNode = new Graph.InvocationFlowNode(reason, e.getValue().getRight());

            for (var invokeFlow : e.getValue().getLeft()) {
                TypeFlow<?> receiver = originalInvokeReceivers.get(invokeFlow);

                if (invokeFlow.isContextInsensitive()) {
                    // Root invocation
                    Graph.FlowNode rootCallFlow = new Graph.FlowNode(
                            "Root call to " + invokeFlow.getTargetMethod(),
                            CausalityEvents.RootMethodRegistration.create(invokeFlow.getTargetMethod()),
                            bb.getAllInstantiatedTypeFlow().getState());

                    g.add(new Graph.FlowEdge(
                            flowMapper.apply(invokeFlow.getTargetMethod().getDeclaringClass().instantiatedTypes),
                            rootCallFlow
                    ));
                    g.add(new Graph.FlowEdge(
                            rootCallFlow,
                            invocationFlowNode
                    ));
                } else  {
                    assert receiver != null;
                    Graph.FlowNode receiverNode = flowMapper.apply(receiver);
                    if(receiverNode != null) {
                        g.add(new Graph.FlowEdge(
                                receiverNode,
                                invocationFlowNode
                        ));
                    }
                }
            }
        }

        for (Pair<TypeFlow<?>, TypeFlow<?>> e : interflows.keySet()) {
            Graph.RealFlowNode left = null;

            if(e.getLeft() != null) {
                left = flowMapper.apply(e.getLeft());
                if(left == null)
                    continue;
            }

            Graph.RealFlowNode right = flowMapper.apply(e.getRight());
            if(right == null)
                continue;

            g.add(new Graph.FlowEdge(left, right));
        }

        for (AnalysisType t : bb.getAllInstantiatedTypes()) {
            TypeState state = TypeState.forExactType(bb, t, false);
            Graph.FlowNode vfn = new Graph.FlowNode("Virtual Flow Node for reaching " + t.toJavaName(), CausalityEvents.TypeInstantiated.create(t), state);
            g.add(new Graph.FlowEdge(null, vfn));

            t.forAllSuperTypes(t1 -> {
                g.add(new Graph.FlowEdge(vfn, flowMapper.apply(t1.instantiatedTypes)));
                g.add(new Graph.FlowEdge(vfn, flowMapper.apply(t1.instantiatedTypesNonNull)));
            });
        }

        for (Map.Entry<Pair<CausalityEvent, TypeFlow<?>>, HashSet<AnalysisType>> e : flowingFromHeap.entrySet()) {
            Graph.RealFlowNode fieldNode = flowMapper.apply(e.getKey().getRight());

            if (fieldNode == null)
                continue;

            // The causality-query implementation saturates at 20 types.
            // Saturation will happen even if the types don't pass the filter
            // Therefore, as soon as a typeflow connected to the source (represented by null) allows for more than 20 types,
            // These will be added to allInstantiated immeadiatly.
            // In practice this only shows in big projects and rarely (e.g. 3 times in 170MB spring-petclinic)
            // Therefore we simply employ this quick fix:
            var typeIter = e.getValue().iterator();
            while (typeIter.hasNext()) {
                TypeState state = TypeState.forEmpty();

                for(int j = 0; j < 20 && typeIter.hasNext(); j++) {
                    state = TypeState.forUnion(bb, state, TypeState.forExactType(bb, typeIter.next(), false));
                }

                Graph.FlowNode intermediate = new Graph.FlowNode("Virtual Flow from Heap", e.getKey().getLeft(), state);
                g.add(new Graph.FlowEdge(null, intermediate));
                g.add(new Graph.FlowEdge(intermediate, fieldNode));
            }
        }

        this.interflows.clear();
        this.flowingFromHeap.clear();
        this.originalInvokeReceivers.clear();

        return g;
    }
}
