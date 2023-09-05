package com.oracle.graal.pointsto.reports.causality;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
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
import com.oracle.graal.pointsto.typestate.TypeState;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import org.graalvm.collections.Pair;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class TypeflowImpl extends Impl {
    private final HashSet<Pair<TypeFlow<?>, TypeFlow<?>>> interflows = new HashSet<>();

    /**
     * Saves for each virtual invocation the receiver typeflow before it may have been replaced during saturation.
     */
    private final HashMap<AbstractVirtualInvokeTypeFlow, TypeFlow<?>> originalInvokeReceivers = new HashMap<>();
    private final HashMap<Pair<Event, TypeFlow<?>>, TypeState> flowingFromHeap = new HashMap<>();


    public TypeflowImpl() {
    }

    public TypeflowImpl(Iterable<TypeflowImpl> instances, PointsToAnalysis bb) {
        super(instances, bb);
        for (TypeflowImpl i : instances) {
            interflows.addAll(i.interflows);
            originalInvokeReceivers.putAll(i.originalInvokeReceivers);
            mergeTypeFlowMap(flowingFromHeap, i.flowingFromHeap, bb);
        }
    }

    @Override
    public void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
        if (from == to)
            return;

        if (currentlySaturatingDepth > 0)
            if (from instanceof AllInstantiatedTypeFlow)
                return;
            else
                assert to.isContextInsensitive() || from instanceof ActualReturnTypeFlow && to instanceof ActualReturnTypeFlow || to instanceof ActualParameterTypeFlow;

        interflows.add(Pair.create(from, to));
    }


    int currentlySaturatingDepth; // Inhibits the registration of new typeflow edges

    @Override
    public void beginSaturationHappening() {
        currentlySaturatingDepth++;
    }

    @Override
    public void endSaturationHappening() {
        currentlySaturatingDepth--;
        if (currentlySaturatingDepth < 0)
            throw new RuntimeException();
    }

    @Override
    public void registerTypesEntering(PointsToAnalysis bb, Event cause, TypeFlow<?> destination, TypeState types) {
        flowingFromHeap.compute(Pair.create(cause, destination), (p, state) -> state == null ? types : TypeState.forUnion(bb, state, types));
    }



    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
    }

    @Override
    public void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
        originalInvokeReceivers.put(invocation, invocation.method() == null ? null : invocation.getReceiver());
    }

    protected void forEachTypeflow(Consumer<TypeFlow<?>> callback) {
        for (var e : interflows) {
            callback.accept(e.getLeft());
            callback.accept(e.getRight());
        }
    }

    @Override
    protected void forEachEvent(Consumer<Event> callback) {
        super.forEachEvent(callback);

        flowingFromHeap.keySet().stream().map(Pair::getLeft).forEach(callback);

        // TODO: Unsure about this - whether it is necessary and whether it is correct/complete
        originalInvokeReceivers.keySet().stream().map(InvokeTypeFlow::getTargetMethod).flatMap(targetMethod -> Arrays.stream(targetMethod.getImplementations())).map(MethodImplementationInvoked::new).forEach(callback);

        forEachTypeflow(tf -> {
            if(tf != null) {
                Event e = getContainingEvent(tf);
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

    private static Event getContainingEvent(TypeFlow<?> f) {
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
            return new InlinedMethodCode(pos);
        } else {
            AnalysisMethod m = f.method();
            if (m != null) {
                return new InlinedMethodCode(m);
            } else {
                return null;
            }
        }
    }

    @Override
    public Graph createCausalityGraph(PointsToAnalysis bb) {
        Graph g = super.createCausalityGraph(bb);

        HashMap<TypeFlow<?>, Graph.RealFlowNode> flowMapping = new HashMap<>();

        Function<TypeFlow<?>, Graph.RealFlowNode> flowMapper = flow ->
        {
            if(flow.getState().typesCount() == 0 && !flow.isSaturated())
                return null;

            return flowMapping.computeIfAbsent(flow, f -> {
                Event reason = getContainingEvent(f);

                if(reason != null && reason.unused())
                    return null;

                return Graph.RealFlowNode.create(bb, f, reason);
            });
        };

        Map<AnalysisMethod, Pair<Set<AbstractVirtualInvokeTypeFlow>, TypeState>> virtual_invokes = new HashMap<>();

        for (var e : originalInvokeReceivers.entrySet()) {
            PointsToAnalysisMethod targetMethod = e.getKey().getTargetMethod();
            TypeState receiverState = bb.getAllInstantiatedTypeFlow().getState();
            if (e.getValue() != null)
                receiverState = e.getValue().filter(bb, receiverState);

            for (AnalysisType type : receiverState.types(bb)) {
                AnalysisMethod method = null;
                try {
                    method = type.resolveConcreteMethod(targetMethod);
                } catch (UnsupportedFeatureException ignored) {
                }

                if (method == null || Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }

                var calleeList = bb.getHostVM().getMultiMethodAnalysisPolicy().determineCallees(bb, PointsToAnalysis.assertPointsToAnalysisMethod(method), targetMethod, e.getKey().getCallerMultiMethodKey(), e.getKey());
                for (PointsToAnalysisMethod callee : calleeList) {
                    assert callee.getTypeFlow().getMethod().equals(callee);

                    /*
                     * Different receiver type can yield the same target method; although it is correct
                     * in a context insensitive analysis to link the callee only if it was not linked
                     * before, in a context sensitive analysis the callee should be linked for each
                     * different context.
                     */

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
                        return Pair.create(invokes, TypeState.forUnion(bb, targetReachingTypes, TypeState.forExactType(bb, type, false)));
                    });
                }
            }
        }

        for (var e : virtual_invokes.entrySet()) {
            Event reason = new MethodImplementationInvoked(e.getKey());

            if(reason.unused())
                continue;

            Graph.InvocationFlowNode invocationFlowNode = new Graph.InvocationFlowNode(reason, e.getValue().getRight());

            for (var invokeFlow : e.getValue().getLeft()) {
                TypeFlow<?> receiver = originalInvokeReceivers.get(invokeFlow);

                if (invokeFlow.isContextInsensitive()) {
                    // Root invocation
                    Graph.FlowNode rootCallFlow = new Graph.FlowNode(
                            "Root call to " + invokeFlow.getTargetMethod(),
                            new RootMethodRegistration(invokeFlow.getTargetMethod()),
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

        for (Pair<TypeFlow<?>, TypeFlow<?>> e : interflows) {
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
            Graph.FlowNode vfn = new Graph.FlowNode("Virtual Flow Node for reaching " + t.toJavaName(), new TypeInstantiated(t), state);
            g.add(new Graph.FlowEdge(null, vfn));

            t.forAllSuperTypes(t1 -> {
                g.add(new Graph.FlowEdge(vfn, flowMapper.apply(t1.instantiatedTypes)));
                g.add(new Graph.FlowEdge(vfn, flowMapper.apply(t1.instantiatedTypesNonNull)));
            });
        }

        for (Map.Entry<Pair<Event, TypeFlow<?>>, TypeState> e : flowingFromHeap.entrySet()) {
            Graph.RealFlowNode fieldNode = flowMapper.apply(e.getKey().getRight());

            if (fieldNode == null)
                continue;

            // The causality-query implementation saturates at 20 types.
            // Saturation will happen even if the types don't pass the filter
            // Therefore, as soon as a typeflow connected to the source (represented by null) allows for more than 20 types,
            // These will be added to allInstantiated immeadiatly.
            // In practice this only shows in big projects and rarely (e.g. 3 times in 170MB spring-petclinic)
            // Therefore we simply employ this quick fix:
            if(e.getValue().typesCount() <= 20) {
                Graph.FlowNode intermediate = new Graph.FlowNode("Virtual Flow from Heap", e.getKey().getLeft(), e.getValue());
                g.add(new Graph.FlowEdge(null, intermediate));
                g.add(new Graph.FlowEdge(intermediate, fieldNode));
            } else {
                AnalysisType[] types = e.getValue().typesStream(bb).toArray(AnalysisType[]::new);

                for(int i = 0; i < types.length; i += 20) {
                    TypeState state = TypeState.forEmpty();

                    for(int j = i; j < i + 20 && j < types.length; j++) {
                        state = TypeState.forUnion(bb, state, TypeState.forExactType(bb, types[j], false));
                    }

                    Graph.FlowNode intermediate = new Graph.FlowNode("Virtual Flow from Heap", e.getKey().getLeft(), state);
                    g.add(new Graph.FlowEdge(null, intermediate));
                    g.add(new Graph.FlowEdge(intermediate, fieldNode));
                }
            }
        }

        return g;
    }
}
