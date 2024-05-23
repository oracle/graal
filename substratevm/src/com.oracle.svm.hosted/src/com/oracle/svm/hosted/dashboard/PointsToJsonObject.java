/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.dashboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.hosted.Feature.OnAnalysisExitAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.ActualParameterTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.AllSynchronizedTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayCopyTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.BoxTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.DynamicNewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FilterTypeFlow;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReceiverTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReturnTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow;
import com.oracle.graal.pointsto.flow.MergeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MonitorEnterTypeFlow;
import com.oracle.graal.pointsto.flow.NewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.NullCheckTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow;
import com.oracle.graal.pointsto.flow.ProxyTypeFlow;
import com.oracle.graal.pointsto.flow.SourceTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.dashboard.ToJson.JsonArray;
import com.oracle.svm.hosted.dashboard.ToJson.JsonNumber;
import com.oracle.svm.hosted.dashboard.ToJson.JsonObject;
import com.oracle.svm.hosted.dashboard.ToJson.JsonString;
import com.oracle.svm.hosted.dashboard.ToJson.JsonValue;

import jdk.graal.compiler.graphio.GraphOutput;
import jdk.graal.compiler.graphio.GraphStructure;
import jdk.vm.ci.code.BytecodePosition;

/**
 * Creates a JSON representation of the pointsto graph, in the format expected by the dashboard. The
 * generated graph is a JSON object of the form:
 *
 * <pre>
 *   {
 *     "type-flows": [
 *       ...
 *     ]
 *   }
 * </pre>
 *
 * where each node in the <code>type-flows</code> array has the following basic schema:
 *
 * <pre>
 *   {
 *     "id": method ID (int),
 *     "flowType": flow type (String, e.g. "method", "callsite", "formalReturn", ...),
 *     "info": {
 *       "inputs": [],
 *       "uses": [],
 *       "codeLocation": code location (String)
 *     }
 *   }
 * </pre>
 *
 * Depending on their flow type, some nodes can contain other fields in their <code>info</code>
 * object, such as their type state, their qualified name for methods and fields, or the ID of their
 * enclosing method for e.g. formal returns, formal parameters and callsites. Which flow types
 * contain which additional fields is subject to change - fields are added as required by the
 * dashboard.
 *
 * Example nodes:
 *
 * <pre>
 *   {
 *     "id": 1234,
 *     "flowType": "method",
 *     "info": {
 *       "inputs": [],
 *       "uses": [],
 *       "qualifiedName": "com.example.Foo.foo()"
 *     }
 *   }
 *   {
 *     "id": 2345,
 *     "flowType": "method",
 *     "info": {
 *       "inputs": [],
 *       "uses": [],
 *       "qualifiedName": "com.example.Bar.bar()"
 *     }
 *   }
 *   {
 *     "id": 3456,
 *     "flowType": "callsite",
 *     "info": {
 *       "inputs": [1234],
 *       "uses": [2345],
 *       "codeLocation": "at com.example.Foo.foo(Foo.java:54) [bci: 7]",
 *       "calleeNames": ["com.example.Bar.bar()"],
 *       "enclosingMethod": 1234
 *     }
 *   }
 * </pre>
 *
 * Example usage:
 *
 * <pre>
 *   BigBang bb = (...);
 *   SerializedTypeFlowGraph serializedGraph = new SerializedTypeFlowGraph(bb);
 *   serializedGraph.build();
 *   String json = serializedGraph.toJson();
 *   System.out.println(json);
 * </pre>
 */
class PointsToJsonObject extends JsonObject {

    private final OnAnalysisExitAccess access;
    private boolean built = false;

    static class InflatableArrayList<T> extends ArrayList<T> {

        private static final long serialVersionUID = 1L;

        @Override
        public T set(int index, T element) {
            if (size() <= index) {
                addAll(Collections.nCopies(index - size(), null));
                add(element);
                return null;
            }
            return super.set(index, element);
        }
    }

    private final BitSet known = new BitSet();
    private List<AnalysisWrapper> flows = new InflatableArrayList<>();

    PointsToJsonObject(OnAnalysisExitAccess access) {
        this.access = access;
    }

    @Override
    Stream<String> getNames() {
        return Arrays.asList("type-flows").stream();
    }

    @Override
    JsonValue getValue(String name) {
        return JsonArray.get(transform(flows.stream()));
    }

    private static Stream<JsonValue> transform(Stream<AnalysisWrapper> flows) {
        return flows.filter(f -> f != null).map(FlowJsonObject::new);
    }

    private static class FlowJsonObject extends JsonObject {

        private final AnalysisWrapper flow;

        FlowJsonObject(AnalysisWrapper flow) {
            this.flow = flow;
        }

        private static final List<String> NAMES = Arrays.asList("id", "flowType", "info");

        @Override
        Stream<String> getNames() {
            return NAMES.stream();
        }

        @Override
        JsonValue getValue(String name) {
            return NAMES.get(0).equals(name) ? JsonNumber.get(flow.id) : (NAMES.get(1).equals(name) ? JsonString.get(flow.flowType) : new InfoJsonObject(flow));
        }
    }

    private static class InfoJsonObject extends JsonObject {

        private final AnalysisWrapper flow;

        InfoJsonObject(AnalysisWrapper flow) {
            this.flow = flow;
        }

        private static final String QUALIFIED_NAME = "qualifiedName";
        private static final String QUALIFIED_NAME_SIMPLE_PARAMS = "qualifiedNameSimpleParams";
        private static final String INPUTS = "inputs";
        private static final String USES = "uses";
        private static final String CODE_LOCATION = "codeLocation";
        private static final String CALLEE_NAMES = "calleeNames";
        private static final String TYPES = "types";
        private static final String ENCLOSING_METHOD = "enclosingMethod";
        private static final List<String> NAMES = Arrays.asList(QUALIFIED_NAME, QUALIFIED_NAME_SIMPLE_PARAMS, INPUTS, USES, CODE_LOCATION, CALLEE_NAMES, TYPES, ENCLOSING_METHOD);

        @Override
        Stream<String> getNames() {
            return NAMES.stream();
        }

        @Override
        JsonValue getValue(String name) {
            switch (name) {
                case QUALIFIED_NAME:
                    return JsonString.get(flow.qualifiedName);
                case QUALIFIED_NAME_SIMPLE_PARAMS:
                    return JsonString.get(flow.qualifiedNameSimpleParams);
                case INPUTS:
                    return JsonArray.get(flow.inputs.values().stream().map(JsonNumber::get));
                case USES:
                    return JsonArray.get(flow.uses.values().stream().map(JsonNumber::get));
                case CODE_LOCATION:
                    return JsonString.get(flow.codeLocation);
                case CALLEE_NAMES:
                    return flow.calleeNames == null ? null : JsonArray.get(flow.calleeNames.stream().map(JsonString::get));
                case TYPES:
                    return flow.types == null ? null : JsonArray.get(flow.types.stream().map(JsonString::get));
                case ENCLOSING_METHOD:
                    return JsonNumber.get(flow.enclosingMethod);
                default:
                    return null;
            }
        }
    }

    Map<? extends Object, ? extends Object> getProperties() {
        return Collections.emptyMap();
    }

    void dump(GraphOutput<?, ?> output) throws IOException {
        build();
        GraphOutput.newBuilder(new PointToStructure()).build(output).print(this, getProperties(), 0, "%s", "PointsTo Graph");
    }

    static class Port {
        final int size;
        final boolean input;

        Port(int size) {
            this(size, true);
        }

        Port(int size, boolean input) {
            this.size = size;
            this.input = input;
        }
    }

    private static class PointToStructure implements GraphStructure<PointsToJsonObject, AnalysisWrapper, WrapperClazz, Port> {
        enum EMPT {
            EDGE
        }

        @Override
        public PointsToJsonObject graph(PointsToJsonObject currentGraph, Object obj) {
            return null; // no subgraphs
        }

        @Override
        public Iterable<? extends AnalysisWrapper> nodes(PointsToJsonObject graph) {
            return graph.flows.stream().filter(f -> f != null && graph.known.get(f.id)).collect(Collectors.toList());
        }

        @Override
        public int nodesCount(PointsToJsonObject graph) {
            return (int) graph.flows.stream().filter(f -> f != null && graph.known.get(f.id)).count();
        }

        @Override
        public int nodeId(AnalysisWrapper node) {
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(AnalysisWrapper node) {
            return node.inputs.size() > 0;
        }

        @Override
        public void nodeProperties(PointsToJsonObject graph, AnalysisWrapper node, Map<String, ? super Object> properties) {
            node.getProperties(properties);
        }

        @Override
        public AnalysisWrapper node(Object obj) {
            return (obj instanceof AnalysisWrapper) ? (AnalysisWrapper) obj : null;
        }

        @Override
        public WrapperClazz nodeClass(Object obj) {
            return (obj instanceof AnalysisWrapper) ? ((AnalysisWrapper) obj).getClazz() : ((obj instanceof WrapperClazz) ? (WrapperClazz) obj : null);
        }

        @Override
        public WrapperClazz classForNode(AnalysisWrapper node) {
            return node.getClazz();
        }

        @Override
        public String nameTemplate(WrapperClazz nodeClass) {
            return nodeClass.name;
        }

        @Override
        public Object nodeClassType(WrapperClazz nodeClass) {
            return nodeClass.wrappedClass;
        }

        @Override
        public Port portInputs(WrapperClazz nodeClass) {
            return nodeClass.inputs;
        }

        @Override
        public Port portOutputs(WrapperClazz nodeClass) {
            return nodeClass.uses;
        }

        @Override
        public int portSize(Port port) {
            return port.size;
        }

        @Override
        public boolean edgeDirect(Port port, int index) {
            return true;
        }

        @Override
        public String edgeName(Port port, int index) {
            return "";
        }

        @Override
        public Object edgeType(Port port, int index) {
            return EMPT.EDGE;
        }

        @Override
        public Collection<? extends AnalysisWrapper> edgeNodes(PointsToJsonObject graph, AnalysisWrapper node, Port port, int index) {
            return Collections.singleton(graph.flows.get(port.input ? node.inputs.get(index) : node.uses.get(index)));
        }
    }

    /**
     * Provides constants mapping the name of each subclass of
     * {@link com.oracle.graal.pointsto.flow.TypeFlow} to the corresponding type flow name expected
     * by the GraalVM Dashboard.
     */
    public static class DashboardTypeFlowNames {

        private static final HashMap<Class<?>, String> names;

        static {
            names = new HashMap<>();
            names.put(FormalReturnTypeFlow.class, "formalReturn");
            names.put(ActualReturnTypeFlow.class, "actualReturn");
            names.put(FormalParamTypeFlow.class, "formalParam");
            names.put(ActualParameterTypeFlow.class, "actualParameter");
            names.put(InvokeTypeFlow.class, "callsite");
            names.put(NewInstanceTypeFlow.class, "alloc");
            names.put(DynamicNewInstanceTypeFlow.class, "dynamicAlloc");
            names.put(LoadFieldTypeFlow.LoadInstanceFieldTypeFlow.class, "instanceFieldLoad");
            names.put(LoadFieldTypeFlow.LoadStaticFieldTypeFlow.class, "staticFieldLoad");
            names.put(StoreFieldTypeFlow.StoreInstanceFieldTypeFlow.class, "instanceFieldStore");
            names.put(StoreFieldTypeFlow.StoreStaticFieldTypeFlow.class, "staticFieldStore");
            names.put(FieldTypeFlow.class, "field");
            names.put(NullCheckTypeFlow.class, "nullCheck");
            names.put(ArrayCopyTypeFlow.class, "arrayCopy");
            names.put(BoxTypeFlow.class, "box");
            names.put(CloneTypeFlow.class, "clone");
            names.put(FilterTypeFlow.class, "filter");
            names.put(FormalReceiverTypeFlow.class, "formalReceiver");
            names.put(OffsetLoadTypeFlow.LoadIndexedTypeFlow.class, "loadIndexed");
            names.put(MergeTypeFlow.class, "merge");
            names.put(MonitorEnterTypeFlow.class, "monitorEnter");
            names.put(ProxyTypeFlow.class, "proxy");
            names.put(SourceTypeFlow.class, "source");
            names.put(OffsetStoreTypeFlow.StoreIndexedTypeFlow.class, "storeIndexed");
            names.put(OffsetLoadTypeFlow.UnsafeLoadTypeFlow.class, "unsafeLoad");
            names.put(OffsetStoreTypeFlow.UnsafeStoreTypeFlow.class, "unsafeStore");
            names.put(AllInstantiatedTypeFlow.class, "allInstantiated");
            names.put(AllSynchronizedTypeFlow.class, "allSynchronized");
            names.put(ArrayElementsTypeFlow.class, "arrayElements");
            names.put(FieldFilterTypeFlow.class, "fieldFilter");
            names.put(ContextInsensitiveFieldTypeFlow.class, "fieldSink");
        }

        /**
         * For a given TypeFlow, return the corresponding type flow name expected by the dashboard.
         *
         * @param flow TypeFlow for which to get the dashboard name
         * @return corresponding type flow name expected by the dashboard
         */
        public static String get(TypeFlow<?> flow) {
            Class<?> clas;
            if (flow instanceof InvokeTypeFlow) {
                // We currently treat all subclasses of InvokeTypeFlow as the same.
                // Since we can't access some of its private subclasses, we rename all of them
                // to their common superclass here.
                clas = InvokeTypeFlow.class;
            } else {
                clas = flow.getClass();
            }
            return DashboardTypeFlowNames.names.get(clas);
        }
    }

    /**
     * A collection of names of type flows which require their enclosing method to be manually added
     * as an input to JSON type flow nodes of that flow type. The dashboard expects certain flow
     * types to have this information, in order to generate node- and edge labels.
     */
    private static final Collection<String> REQUIRE_ENCLOSING_METHOD_INPUT = Arrays.asList("callsite", "alloc");

    /**
     * A collection of names of type flows which require the ID of their enclosing method to be
     * added as a field in the <code>info</code> object of JSON type flow nodes of that flow type.
     * The dashboard expects certain flow types to have this information, in order to generate node-
     * and edge labels.
     */
    private static final Collection<String> REQUIRE_ENCLOSING_METHOD_ID = Arrays.asList("formalParam", "formalReturn", "callsite");

    /**
     * Serialize all TypeFlows in the universe, prepare for JSON export.
     */
    @Override
    protected void build() {
        if (built) {
            return;
        }
        FeatureImpl.OnAnalysisExitAccessImpl config = (FeatureImpl.OnAnalysisExitAccessImpl) access;
        BigBang bb = config.getBigBang();
        VMError.guarantee(bb instanceof PointsToAnalysis, "Printing points-to statistics only make sense when point-to analysis is on.");
        serializeMethods(bb);
        connectFlowsToEnclosingMethods(bb);
        matchInputsAndUses();
        built = true;
    }

    /**
     * Convenience data structure to extract relevant data from an analysis method. Provides access
     * to method ID, qualified name and method flows graph.
     */
    private static final class AnalysisWrapper {

        public final Class<?> wrappedClass;
        public final int id;
        public String qualifiedName = null;
        public String qualifiedNameSimpleParams = null;
        public MethodFlowsGraph flowsGraph = null;

        public String flowType = null;
        public HashMap<Integer, Integer> inputs = new HashMap<>();
        public HashMap<Integer, Integer> uses = new HashMap<>();
        public String codeLocation = null;
        public ArrayList<String> calleeNames = null;
        public ArrayList<String> types = null;
        public Integer enclosingMethod = null;

        private static final String METHOD_FLOW = "method";

        AnalysisWrapper(Class<?> clazz, PointsToAnalysisMethod method) {
            this(clazz, method.getTypeFlow().id());
            this.flowType = METHOD_FLOW;
            this.qualifiedName = method.format("%H.%n(%P)");
            this.qualifiedNameSimpleParams = method.format("%H.%n(%p)");
            this.flowsGraph = null;

            Collection<MethodFlowsGraph> flows = method.getTypeFlow().getFlows();
            if (!flows.isEmpty()) {
                // Expect to not have any cloned type flow graph.
                // TODO: Ensure that this works with cloned type flows (GR-21940).
                VMError.guarantee(flows.size() == 1, "Expect to have a single type flow graph.");
                // Have exactly one graph, so we can take the next element in the iterator.
                flowsGraph = flows.iterator().next();
            }
        }

        AnalysisWrapper(Class<?> clazz, int id) {
            this.id = id;
            wrappedClass = clazz;
        }

        void getProperties(Map<String, ? super Object> properties) {
            if (flowType != null) {
                properties.put("flowType", flowType);
            }
            if (qualifiedNameSimpleParams != null) {
                properties.put("qualifiedNameSimpleParams", qualifiedNameSimpleParams);
            }
            if (codeLocation != null) {
                properties.put("codeLocation", codeLocation);
            }
            if (calleeNames != null) {
                properties.put("calleeNames", calleeNames.toArray(new String[calleeNames.size()]));
            }
            if (types != null) {
                properties.put("types", types.toArray(new String[types.size()]));
            }
            if (enclosingMethod != null) {
                properties.put("enclosingMethod", enclosingMethod);
            }
        }

        WrapperClazz getClazz() {
            return WrapperClazz.get(wrappedClass, inputs.size(), uses.size(), qualifiedName != null ? qualifiedName : flowType);
        }
    }

    private static final class WrapperClazz {

        private static final Map<Class<?>, Map<Integer, Map<Integer, Map<String, WrapperClazz>>>> clazzes = new HashMap<>();

        static WrapperClazz get(Class<?> clazz, int inputs, int uses, String name) {
            return clazzes.computeIfAbsent(clazz, c -> new HashMap<>())
                            .computeIfAbsent(inputs, s -> new HashMap<>())
                            .computeIfAbsent(uses, s -> new HashMap<>())
                            .computeIfAbsent(name, n -> new WrapperClazz(clazz, inputs, uses, name));
        }

        private WrapperClazz(Class<?> wrappedClass, int inputs, int uses, String name) {
            this.inputs = new Port(inputs);
            this.uses = new Port(uses, false);
            this.name = name;
            this.wrappedClass = wrappedClass;
        }

        final Port inputs;
        final Port uses;
        final String name;
        final Class<?> wrappedClass;
    }

    /**
     * Serialize all {@link AnalysisMethod}s in universe.
     */
    private void serializeMethods(BigBang bb) {
        for (AnalysisMethod method : bb.getUniverse().getMethods()) {
            serializeMethod(bb, new AnalysisWrapper(method.getClass(), PointsToAnalysis.assertPointsToAnalysisMethod(method)));
        }
    }

    /**
     * Serialize an {@link AnalysisMethod} and all of its {@link TypeFlow}s.
     *
     * @param methodWrapper wrapped AnalysisMethod whose type flows to serialize
     */
    private void serializeMethod(BigBang bb, AnalysisWrapper methodWrapper) {
        assert !known.get(methodWrapper.id);
        known.set(methodWrapper.id);
        flows.set(methodWrapper.id, methodWrapper);
        if (methodWrapper.flowsGraph == null) {
            // This method does not have type flows (e.g. interface methods).
            return;
        }

        // Serialize all type flows of this method, recursively serialize their inputs and uses.
        for (TypeFlow<?> flow : methodWrapper.flowsGraph.flows()) {
            if (flow == null) {
                // Can have null-nodes - skip them.
                continue;
            }

            // Serialize this type flow to a JSON object, add it to the node index.
            serializeTypeFlow(bb, flow);
        }
    }

    /**
     * JSON-serialize a given {@link TypeFlow}. This method creates a new JSON object with the given
     * flow's ID, and adds the new JSON object to the node index. It then extracts additional
     * information, such flow type, code location and qualified name out of the flow, and fills this
     * information into the JSON object, whereby not all flow types have the same information
     * extracted. Finally, it goes through all of the flows input, observee, use and observer type
     * flows and recursively calls this method on these flows. This approach ensures that all type
     * flows will be serialized, even if they are not associated with an enclosing method (applies
     * to e.g. fields).
     *
     * @param flow TypeFlow to serialize
     */
    private void serializeTypeFlow(BigBang bb, TypeFlow<?> flow) {
        int flowId = flow.id();

        if (known.get(flowId)) {
            // Done. No need to process this flow further, since we have already collected it.
            return;
        }
        AnalysisWrapper flowWrapper = new AnalysisWrapper(flow.getClass(), flowId);
        flowWrapper.flowType = serializeTypeFlowName(flow);
        flowWrapper.codeLocation = getCodeLocation(flow);
        known.set(flowId);
        flows.set(flowId, flowWrapper);
        // Perform flow-type specific tasks for certain flow-types.
        if (flow instanceof InvokeTypeFlow) {
            // A callsite gets its callees as uses.
            Collection<AnalysisMethod> callees = ((InvokeTypeFlow) flow).getAllCallees();
            flowWrapper.calleeNames = new ArrayList<>();
            for (AnalysisMethod callee : callees) {
                int calleeId = PointsToAnalysis.assertPointsToAnalysisMethod(callee).getTypeFlow().id();
                addUnique(flowWrapper.uses, calleeId);
                flowWrapper.calleeNames.add(callee.getQualifiedName());
            }
        } else if (flow instanceof NewInstanceTypeFlow || flow instanceof DynamicNewInstanceTypeFlow) {
            flowWrapper.types = serializeTypeState(bb, flow.getState());
        } else if (flow instanceof LoadFieldTypeFlow.LoadInstanceFieldTypeFlow || flow instanceof LoadFieldTypeFlow.LoadStaticFieldTypeFlow) {
            LoadFieldTypeFlow loadFlow = (LoadFieldTypeFlow) flow;
            flowWrapper.qualifiedName = fieldName(loadFlow.field());
        } else if (flow instanceof StoreFieldTypeFlow.StoreInstanceFieldTypeFlow || flow instanceof StoreFieldTypeFlow.StoreStaticFieldTypeFlow) {
            TypeState typeState = flow.getState();
            flowWrapper.types = serializeTypeState(bb, typeState);
            StoreFieldTypeFlow storeFlow = (StoreFieldTypeFlow) flow;
            flowWrapper.qualifiedName = fieldName(storeFlow.field());
        } else if (flow instanceof FieldTypeFlow) {
            FieldTypeFlow fieldFlow = (FieldTypeFlow) flow;
            flowWrapper.qualifiedName = fieldName(fieldFlow.getSource());
        } else if (flow instanceof FormalReceiverTypeFlow) {
            flowWrapper.qualifiedName = flow.getDeclaredType().toJavaName();
        }

        // Set inputs and uses for this node.
        collectInputs(bb, flow, flowWrapper.inputs);
        collectUses(bb, flow, flowWrapper.uses);
    }

    /**
     * Translate a given class name of a {@link TypeFlow} subclass into the nomenclature expected by
     * the dashboard.
     *
     * @param flow {@link TypeFlow} subclass for which to get the dashboard name
     * @return name of that flow type within the dashboard's nomenclature.
     */
    private static String serializeTypeFlowName(TypeFlow<?> flow) {
        String name = DashboardTypeFlowNames.get(flow);
        if (name == null) {
            return "unhandled";
        }
        return name;
    }

    /**
     * Connect all type flows to their enclosing method.
     */
    private void connectFlowsToEnclosingMethods(BigBang bb) {
        for (AnalysisMethod method : bb.getUniverse().getMethods()) {
            AnalysisWrapper methodWrapper = new AnalysisWrapper(method.getClass(), PointsToAnalysis.assertPointsToAnalysisMethod(method));
            if (methodWrapper.flowsGraph == null) {
                // Some methods (such as interface methods) don't have any flows. This
                // field is null in that case. Can skip them.
                continue;
            }
            for (TypeFlow<?> flow : methodWrapper.flowsGraph.flows()) {
                if (flow != null) {
                    connectFlowToEnclosingMethod(flow.id(), methodWrapper.id);
                }
            }
        }
    }

    /**
     * Add enclosing method as an input to the JSON node corresponding to this type flow, and/or as
     * a field in its <code>info</code> object, if needed. The dashboard expects certain flow types
     * to have their enclosing method as an input, or to have the ID of their enclosing method
     * listed as a field in their <code>info</code> object. The affected flow types are listed in
     * {@link PointsToJsonObject#REQUIRE_ENCLOSING_METHOD_INPUT} and
     * {@link PointsToJsonObject#REQUIRE_ENCLOSING_METHOD_ID} respectively. If a type flow node gets
     * its enclosing method added as an input, it also gets added as a use to its enclosing method's
     * node.
     * <p>
     * If the flow type of the given type flow node is in neither of these collections, this method
     * does not apply any changes.
     *
     * @param flowId the ID of flow for which to add enclosing method input or ID if needed.
     * @param parentId the ID of this type flow's enclosing method.
     */
    private void connectFlowToEnclosingMethod(int flowId, int parentId) {
        AnalysisWrapper parent = flows.get(parentId);
        AnalysisWrapper flowWrapper = flows.get(flowId);
        assert flowWrapper != null;
        if (parent == null) {
            return;
        }
        if (REQUIRE_ENCLOSING_METHOD_INPUT.contains(flowWrapper.flowType)) {
            addUnique(flowWrapper.inputs, parent.id);
            addUnique(parent.uses, flowWrapper.id);
        }

        // Add enclosing method's ID to flow node's info object if applicable.
        if (REQUIRE_ENCLOSING_METHOD_ID.contains(flowWrapper.flowType)) {
            flowWrapper.enclosingMethod = parent.id;
        }
    }

    /**
     * Get a String representation of this type flow's code location, or null, if no code location
     * is available.
     *
     * @param flow the type flow for which to get the code location
     * @return String representation of flow's code location, or null if not available
     */
    private static String getCodeLocation(TypeFlow<?> flow) {
        // Return source position String if not null. Can be null due to exception,
        // or because node's source position is set to null.
        if (flow.getSource() instanceof BytecodePosition) {
            return flow.getSource().toString();
        } else {
            return null;
        }
    }

    /**
     * Add the IDs of the given {@link TypeFlow}'s inputs and observees to the given list, as uses
     * of the given {@link TypeFlow}. Indirectly-recursively serialize the inputs and observees of
     * this type flow, where observees are objects that notify this flow when they change.
     *
     * @param flow TypeFlow who's inputs and observees to collect
     * @param targetList target array for input use and observee IDs to
     */
    private void collectInputs(BigBang bb, TypeFlow<?> flow, Map<Integer, Integer> targetList) {
        for (Object input : flow.getInputs()) {
            TypeFlow<?> inputFlow = (TypeFlow<?>) input;
            addUnique(targetList, inputFlow.id());
            // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
            // we don't know the method ID of the parent methods of the input flows.
            serializeTypeFlow(bb, inputFlow);
        }
        for (Object observee : flow.getObservees()) {
            TypeFlow<?> observeeFlow = (TypeFlow<?>) observee;
            addUnique(targetList, observeeFlow.id());
            // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
            // we don't know the method ID of the parent methods of the observee flows.
            serializeTypeFlow(bb, observeeFlow);
        }
    }

    /**
     * Add the IDs of the given {@link TypeFlow}'s uses and observers to the given list, as uses of
     * the given {@link TypeFlow}. Indirectly-recursively serialize the uses and observers of this
     * type flow, where observers are objects that are notified when this flow changes.
     *
     * @param flow TypeFlow who's uses and observers to collect
     * @param targetList target list for adding use and observer IDs to
     */
    private void collectUses(BigBang bb, TypeFlow<?> flow, Map<Integer, Integer> targetList) {
        for (Object use : flow.getUses()) {
            TypeFlow<?> useFlow = (TypeFlow<?>) use;
            addUnique(targetList, useFlow.id());
            // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
            // we don't know the method ID of the parent methods of the use flows.
            serializeTypeFlow(bb, useFlow);
        }
        for (Object observer : flow.getObservers()) {
            TypeFlow<?> observerFlow = (TypeFlow<?>) observer;
            addUnique(targetList, observerFlow.id());
            // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
            // we don't know the method ID of the parent methods of the observer flows.
            serializeTypeFlow(bb, observerFlow);
        }
    }

    /**
     * Add an integer to a list, if and only if the array does not already contain that integer.
     *
     * @param list target list
     * @param element integer to add
     */
    private static <T> void addUnique(Map<Integer, T> list, T element) {
        list.put(list.size(), element);
    }

    /**
     * Serialize a given {@link TypeState} into a JSON array containing the formatted class name of
     * {@link AnalysisType} in the TypeState.
     *
     * @param typeState the TypeState to be serialized.
     * @return a list of the formatted class names of the classes included in the given TypeState.
     */
    private static ArrayList<String> serializeTypeState(BigBang bb, TypeState typeState) {
        ArrayList<String> types = new ArrayList<>();
        if (typeState.isPrimitive()) {
            /* No types available in primitive type states */
            return types;
        }
        for (AnalysisType type : typeState.types(bb)) {
            types.add(type.toJavaName());
        }
        return types;
    }

    /**
     * Return the name of a given {@link AnalysisField} in the format
     * <code>com.example.package.SomeClass.someField</code>.
     *
     * @param field AnalysisField of which to get the name
     * @return formatted name of the given AnalysisField
     */
    private static String fieldName(AnalysisField field) {
        return field.format("%H.%n");
    }

    /**
     * Ensure symmetric relations between uses and inputs across all nodes. That is, if node A lists
     * node B as an input, ensure that node B lists node A as a use, and vice versa.
     */
    private void matchInputsAndUses() {
        for (AnalysisWrapper node : flows) {
            if (node != null) {
                matchFromTo(node, true);
                matchFromTo(node, false);
            }
        }
    }

    /**
     * Ensure symmetric references between inputs or uses for a given node.
     *
     * @param fromNode node for which to ensure symmetric inputs or uses
     * @param inputs true for "inputs" - the array to match
     */
    private void matchFromTo(AnalysisWrapper fromNode, boolean inputs) {
        Map<Integer, Integer> fromIds = inputs ? fromNode.inputs : fromNode.uses;
        for (Integer fromId : fromIds.values()) {
            AnalysisWrapper referencedNode = flows.get(fromId);
            if (referencedNode == null) {
                continue;
            }
            Map<Integer, Integer> usesList = inputs ? referencedNode.uses : referencedNode.inputs;
            addUnique(usesList, fromNode.id);
        }
    }
}
