/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.oracle.graal.pointsto.flow.ActualParameterTypeFlow;
import org.graalvm.nativeimage.hosted.Feature.OnAnalysisExitAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.AllSynchronizedTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayCopyTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.BoxTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.DynamicNewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldSinkTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FilterTypeFlow;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReceiverTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReturnTypeFlow;
import com.oracle.graal.pointsto.flow.InitialParamTypeFlow;
import com.oracle.graal.pointsto.flow.InitialReceiverTypeFlow;
import com.oracle.graal.pointsto.flow.InstanceOfTypeFlow;
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
import com.oracle.graal.pointsto.flow.UnknownTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.shadowed.com.google.gson.JsonArray;
import com.oracle.shadowed.com.google.gson.JsonElement;
import com.oracle.shadowed.com.google.gson.JsonObject;
import com.oracle.shadowed.com.google.gson.JsonPrimitive;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.vm.ci.code.BytecodePosition;

class PointsToDumper {

    /**
     * Provides constants mapping the name of each subclass of
     * {@link com.oracle.graal.pointsto.flow.TypeFlow} to the corresponding type flow name expected
     * by the GraalVM Dashboard.
     */
    public static class DashboardTypeFlowNames {

        private static final HashMap<String, String> names;

        static {
            names = new HashMap<>();
            names.put(FormalReturnTypeFlow.class.getSimpleName(), "formalReturn");
            names.put(ActualReturnTypeFlow.class.getSimpleName(), "actualReturn");
            names.put(FormalParamTypeFlow.class.getSimpleName(), "formalParam");
            names.put(ActualParameterTypeFlow.class.getSimpleName(), "actualParameter");
            names.put(InvokeTypeFlow.class.getSimpleName(), "callsite");
            names.put(NewInstanceTypeFlow.class.getSimpleName(), "alloc");
            names.put(DynamicNewInstanceTypeFlow.class.getSimpleName(), "dynamicAlloc");
            names.put(LoadFieldTypeFlow.LoadInstanceFieldTypeFlow.class.getSimpleName(), "instanceFieldLoad");
            names.put(LoadFieldTypeFlow.LoadStaticFieldTypeFlow.class.getSimpleName(), "staticFieldLoad");
            names.put(StoreFieldTypeFlow.StoreInstanceFieldTypeFlow.class.getSimpleName(), "instanceFieldStore");
            names.put(StoreFieldTypeFlow.StoreStaticFieldTypeFlow.class.getSimpleName(), "staticFieldStore");
            names.put(FieldTypeFlow.class.getSimpleName(), "field");
            names.put(OffsetLoadTypeFlow.AtomicReadTypeFlow.class.getSimpleName(), "atomicRead");
            names.put(OffsetStoreTypeFlow.AtomicWriteTypeFlow.class.getSimpleName(), "atomicWrite");
            names.put(NullCheckTypeFlow.class.getSimpleName(), "nullCheck");
            names.put(ArrayCopyTypeFlow.class.getSimpleName(), "arrayCopy");
            names.put(BoxTypeFlow.class.getSimpleName(), "box");
            names.put(CloneTypeFlow.class.getSimpleName(), "clone");
            names.put(OffsetStoreTypeFlow.CompareAndSwapTypeFlow.class.getSimpleName(), "compareAndSwap");
            names.put(FilterTypeFlow.class.getSimpleName(), "filter");
            names.put(FormalReceiverTypeFlow.class.getSimpleName(), "formalReceiver");
            names.put(InstanceOfTypeFlow.class.getSimpleName(), "instanceOf");
            names.put(OffsetLoadTypeFlow.LoadIndexedTypeFlow.class.getSimpleName(), "loadIndexed");
            names.put(MergeTypeFlow.class.getSimpleName(), "merge");
            names.put(MonitorEnterTypeFlow.class.getSimpleName(), "monitorEnter");
            names.put(ProxyTypeFlow.class.getSimpleName(), "proxy");
            names.put(SourceTypeFlow.class.getSimpleName(), "source");
            names.put(OffsetStoreTypeFlow.StoreIndexedTypeFlow.class.getSimpleName(), "storeIndexed");
            names.put(OffsetLoadTypeFlow.UnsafeLoadTypeFlow.class.getSimpleName(), "unsafeLoad");
            names.put(OffsetStoreTypeFlow.UnsafeStoreTypeFlow.class.getSimpleName(), "unsafeStore");
            names.put(AllInstantiatedTypeFlow.class.getSimpleName(), "allInstantiated");
            names.put(AllSynchronizedTypeFlow.class.getSimpleName(), "allSynchronized");
            names.put(ArrayElementsTypeFlow.class.getSimpleName(), "arrayElements");
            names.put(FieldFilterTypeFlow.class.getSimpleName(), "fieldFilter");
            names.put(FieldSinkTypeFlow.class.getSimpleName(), "fieldSink");
            names.put(InitialParamTypeFlow.class.getSimpleName(), "initialParam");
            names.put(InitialReceiverTypeFlow.class.getSimpleName(), "initialReceiver");
            names.put(UnknownTypeFlow.class.getSimpleName(), "unknown");
        }

        /**
         * For a given TypeFlow, return the corresponding type flow name expected by the dashboard.
         *
         * @param flow TypeFlow for which to get the dashboard name
         * @return corresponding type flow name expected by the dashboard
         */
        public static String get(TypeFlow<?> flow) {
            String className = flow.getClass().getSimpleName();
            if (flow instanceof InvokeTypeFlow) {
                // We currently treat all subclasses of InvokeTypeFlow as the same.
                // Since we can't access some of its private subclasses, we rename all of them
                // to their common superclass here.
                className = InvokeTypeFlow.class.getSimpleName();
            }
            return DashboardTypeFlowNames.names.get(className);
        }
    }

    /**
     * Creates a JSON representation of the pointsto graph, in the format expected by the dashboard.
     * The generated graph is a JSON object of the form:
     *
     * <pre>
     *   {
     *     "nodes": [
     *       ...
     *     ]
     *   }
     * </pre>
     *
     * where each node in the <code>nodes</code> array has the following basic schema:
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
     * object, such as their type state, their qualified name for methods and fields, or the ID of
     * their enclosing method for e.g. formal returns, formal parameters and callsites. Which flow
     * types contain which additional fields is subject to change - fields are added as required by
     * the dashboard.
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
     *   JsonSerializedGraph serializedGraph = new JsonSerializedGraph(bb);
     *   serializedGraph.build();
     *   String json = serializedGraph.toJson();
     *   System.out.println(json);
     * </pre>
     */
    public static class JsonSerializedGraph {

        /**
         * A collection of names of type flows which require their enclosing method to be manually
         * added as an input to JSON type flow nodes of that flow type. The dashboard expects
         * certain flow types to have this information, in order to generate node- and edge labels.
         *
         * @see JsonSerializedGraph#connectFlowToEnclosingMethod(TypeFlow, int)
         */
        private static final Collection<String> REQUIRE_ENCLOSING_METHOD_INPUT = new ArrayList<>(Arrays.asList("callsite", "alloc"));

        /**
         * A collection of names of type flows which require the ID of their enclosing method to be
         * added as a field in the <code>info</code> object of JSON type flow nodes of that flow
         * type. The dashboard expects certain flow types to have this information, in order to
         * generate node- and edge labels.
         *
         * @see JsonSerializedGraph#connectFlowToEnclosingMethod(TypeFlow, int)
         */
        private static final Collection<String> REQUIRE_ENCLOSING_METHOD_ID = new ArrayList<>(Arrays.asList("formalParam", "formalReturn", "callsite"));

        /**
         * Map from TypeFlow node IDs to their corresponding JSON object.
         */
        private HashMap<Integer, JsonObject> nodeIndex;

        private BigBang bb;

        /**
         * Whether this graph has already been built.
         */
        private boolean isBuilt;

        JsonSerializedGraph(BigBang bb) {
            this.bb = bb;
            this.nodeIndex = new LinkedHashMap<>();
        }

        /**
         * Serialize all TypeFlows in the universe, prepare for JSON export.
         */
        public void build() {
            if (isBuilt) {
                // No need to re-build.
                return;
            }
            serializeMethods();
            connectFlowsToEnclosingMethods();
            matchInputsAndUses();
            isBuilt = true;
        }

        /**
         * Convenience data structure to extract relevant data from an analysis method. Provides
         * access to method ID, qualified name and method flows graph.
         */
        private static class AnalysisMethodWrapper {

            private int methodId;
            private String qualifiedName;
            private String qualifiedNameSimpleParams;
            private MethodFlowsGraph flowsGraph;

            AnalysisMethodWrapper(AnalysisMethod method) {
                this.methodId = method.getTypeFlow().id();
                this.qualifiedName = method.getQualifiedName();
                this.qualifiedNameSimpleParams = method.format("%H.%n(%p)");
                this.flowsGraph = null;

                Collection<MethodFlowsGraph> flows = method.getTypeFlow().getFlows();
                if (flows.size() != 0) {
                    // Expect to not have any cloned type flow graph.
                    // TODO: Ensure that this works with cloned type flows (GR-21940).
                    VMError.guarantee(flows.size() == 1, "Expect to have a single type flow graph.");
                    // Have exactly one graph, so we can take the next element in the iterator.
                    flowsGraph = flows.iterator().next();
                }
                // Else, this method does not have type flows (e.g. interface methods).
            }

            /**
             * Wrapped {@link AnalysisMethod}'s ID.
             *
             * @return wrapped AnalysisMethod's ID
             * @see AnalysisMethod#getId()
             */
            int getMethodId() {
                return methodId;
            }

            /**
             * Wrapped AnalysisMethod's qualifiedName.
             *
             * @return wrapped AnalysisMethod's qualifiedName.
             * @see AnalysisMethod#getQualifiedName()
             */
            String getQualifiedName() {
                return qualifiedName;
            }

            /**
             * Wrapped AnalysisMethod's qualifiedName, but with simple type names in its parameter
             * list.
             *
             * @return wrapped AnalysisMethod's qualifiedName, with simple parameters
             */
            String getQualifiedNameSimpleParams() {
                return qualifiedNameSimpleParams;
            }

            /**
             * Wrapped method's {@link com.oracle.graal.pointsto.flow.MethodFlowsGraph}.
             *
             * @return Only clone of wrapped method's MethodFlowsGraph. Can be null for wrapped
             *         methods which don't have any TypeFlows (e.g. interface methods).
             */
            MethodFlowsGraph getFlowsGraph() {
                return flowsGraph;
            }
        }

        /**
         * Serialize all {@link AnalysisMethod}s in {@link JsonSerializedGraph#bb}'s universe.
         */
        private void serializeMethods() {
            for (AnalysisMethod method : bb.getUniverse().getMethods()) {
                this.serializeMethod(new AnalysisMethodWrapper(method));
            }
        }

        /**
         * Serialize an {@link AnalysisMethod} and all of its {@link TypeFlow}s and add them to the
         * {@link JsonSerializedGraph#nodeIndex}.
         *
         * @param methodWrapper wrapped AnalysisMethod whose type flows to serialize
         */
        private void serializeMethod(AnalysisMethodWrapper methodWrapper) {

            JsonObject methodNode = new JsonObject();
            methodNode.addProperty("id", methodWrapper.getMethodId());
            methodNode.addProperty("flowType", "method");

            // Register this new method node in the node index.
            nodeIndex.put(methodNode.get("id").getAsInt(), methodNode);

            JsonObject info = new JsonObject();
            info.addProperty("qualifiedName", methodWrapper.getQualifiedName());
            // We currently need this to match against the stdout dump of the method histogram.
            info.addProperty("qualifiedNameSimpleParams", methodWrapper.getQualifiedNameSimpleParams());
            info.add("inputs", new JsonArray());
            info.add("uses", new JsonArray());
            methodNode.add("info", info);

            if (methodWrapper.getFlowsGraph() == null) {
                // This method does not have type flows (e.g. interface methods).
                return;
            }

            // Serialize all type flows of this method, recursively serialize their inputs and uses.
            for (TypeFlow<?> node : methodWrapper.getFlowsGraph().linearizedGraph) {
                if (node == null) {
                    // Can have null-nodes - skip them.
                    continue;
                }

                // Serialize this type flow to a JSON object, add it to the node index.
                serializeTypeFlow(node);
            }
        }

        /**
         * JSON-serialize a given {@link TypeFlow} and add the result to the
         * {@link JsonSerializedGraph#nodeIndex}. The JSON schema is documented in
         * {@link JsonSerializedGraph}. This method creates a new JSON object with the given flow's
         * ID, and adds the new JSON object to the node index. It then extracts additional
         * information, such flow type, code location and qualified name out of the flow, and fills
         * this information into the JSON object, whereby not all flow types have the same
         * information extracted. Finally, it goes through all of the flows input, observee, use and
         * observer type flows and recursively calls this method on these flows. This approach
         * ensures that all type flows will be serialized, even if they are not associated with an
         * enclosing method (applies to e.g. fields).
         *
         * @param flow TypeFlow to serialize
         */
        private void serializeTypeFlow(TypeFlow<?> flow) {
            int flowId = flow.id();

            if (nodeIndex.containsKey(flowId)) {
                // Done. No need to process this flow further, since we have already collected it.
                return;
            }

            // Create a new node for this flow, set its ID, and register the node in the node index
            // to avoid infinite recursion.
            JsonObject newFlowNode = new JsonObject();
            newFlowNode.addProperty("id", flowId);
            nodeIndex.put(flowId, newFlowNode);

            // Translate this type flow's class name into the flow type nomenclature expected
            // by the dashboard.
            newFlowNode.addProperty("flowType", serializeTypeFlowName(flow));

            // Add info object with inputs, uses and code location (inputs and uses empty so far).
            JsonObject info = new JsonObject();
            newFlowNode.add("info", info);
            JsonArray inputs = new JsonArray();
            JsonArray uses = new JsonArray();
            info.add("inputs", inputs);
            info.add("uses", uses);
            info.addProperty("codeLocation", getCodeLocation(flow));

            // Get type state, for flow types that require this information to be dumped.
            TypeState typeState = flow.getState();

            // Perform flow-type specific tasks for certain flow-types.
            if (flow instanceof InvokeTypeFlow) {
                // A callsite gets its callees as uses.
                Collection<AnalysisMethod> callees = ((InvokeTypeFlow) flow).getCallees();
                JsonArray calleeNamesJson = new JsonArray();
                for (AnalysisMethod callee : callees) {
                    int calleeId = callee.getTypeFlow().id();
                    addIntUnique(uses, calleeId);
                    calleeNamesJson.add(callee.getQualifiedName());
                }
                info.add("calleeNames", calleeNamesJson);
            } else if (flow instanceof NewInstanceTypeFlow || flow instanceof DynamicNewInstanceTypeFlow) {
                JsonArray types = serializeTypeState(flow.getState());
                info.add("types", types);
            } else if (flow instanceof LoadFieldTypeFlow.LoadInstanceFieldTypeFlow || flow instanceof LoadFieldTypeFlow.LoadStaticFieldTypeFlow) {
                LoadFieldTypeFlow loadFlow = (LoadFieldTypeFlow) flow;
                String qualifiedName = fieldName(loadFlow.field());
                info.add("qualifiedName", new JsonPrimitive(qualifiedName));
            } else if (flow instanceof StoreFieldTypeFlow.StoreInstanceFieldTypeFlow || flow instanceof StoreFieldTypeFlow.StoreStaticFieldTypeFlow) {
                info.add("types", serializeTypeState(typeState));
                StoreFieldTypeFlow storeFlow = (StoreFieldTypeFlow) flow;
                String qualifiedName = fieldName(storeFlow.field());
                info.add("qualifiedName", new JsonPrimitive(qualifiedName));
            } else if (flow instanceof FieldTypeFlow) {
                FieldTypeFlow fieldFlow = (FieldTypeFlow) flow;
                String qualifiedName = fieldName(fieldFlow.getSource());
                info.add("qualifiedName", new JsonPrimitive(qualifiedName));
            } else if (flow instanceof FormalReceiverTypeFlow) {
                String receiverType = ((FormalReceiverTypeFlow) flow).getDeclaredType().toJavaName();
                info.addProperty("qualifiedName", receiverType);
            }

            // Set inputs and uses for this node.
            collectInputs(flow, inputs);
            collectUses(flow, uses);
        }

        /**
         * Translate a given class name of a {@link TypeFlow} subclass into the nomenclature
         * expected by the dashboard.
         *
         * @param flow {@link TypeFlow} subclass for which to get the dashboard name
         * @return name of that flow type within the dashboard's nomenclature.
         */
        private static String serializeTypeFlowName(TypeFlow<?> flow) {
            String name = DashboardTypeFlowNames.get(flow);
            if (name == null) {
                throw new IllegalArgumentException("Unknown flow type: " + flow.getClass().getSimpleName());
            }
            return name;
        }

        /**
         * Connect all type flows to their enclosing method.
         *
         * @see JsonSerializedGraph#connectFlowToEnclosingMethod(TypeFlow, int)
         */
        private void connectFlowsToEnclosingMethods() {
            for (AnalysisMethod method : bb.getUniverse().getMethods()) {
                AnalysisMethodWrapper methodWrapper = new AnalysisMethodWrapper(method);
                if (methodWrapper.getFlowsGraph() == null) {
                    // Some methods (such as interface methods) don't have any flows. This
                    // field is null in that case. Can skip them.
                    continue;
                }
                for (TypeFlow<?> flow : methodWrapper.getFlowsGraph().linearizedGraph) {
                    this.connectFlowToEnclosingMethod(flow, methodWrapper.getMethodId());
                }
            }
        }

        /**
         * Add enclosing method as an input to the JSON node corresponding to this type flow, and/or
         * as a field in its <code>info</code> object, if needed. The dashboard expects certain flow
         * types to have their enclosing method as an input, or to have the ID of their enclosing
         * method listed as a field in their <code>info</code> object. The affected flow types are
         * listed in {@link JsonSerializedGraph#REQUIRE_ENCLOSING_METHOD_INPUT} and
         * {@link JsonSerializedGraph#REQUIRE_ENCLOSING_METHOD_ID} respectively. If a type flow node
         * gets its enclosing method added as an input, it also gets added as a use to its enclosing
         * method's node.
         * <p>
         * If the flow type of the given type flow node is in neither of these collections, this
         * method does not apply any changes.
         *
         * @param flow type flow for which to add enclosing method input or ID if needed. The
         *            {@link JsonSerializedGraph#nodeIndex} must contain an entry for this flow's
         *            ID. If null, method returns immediately.
         * @param enclosingMethodId the ID of this type flow's enclosing method.
         */
        private void connectFlowToEnclosingMethod(TypeFlow<?> flow, int enclosingMethodId) {
            if (flow == null) {
                // Some AnalysisMethods can contain null TypeFlows. Ignore them.
                return;
            }

            JsonObject flowJson = nodeIndex.get(flow.id());
            assert flowJson != null;

            String flowType = flowJson.getAsJsonPrimitive("flowType").getAsString();

            // Add enclosing method as input to this type flow node and vice versa, if applicable.
            if (REQUIRE_ENCLOSING_METHOD_INPUT.contains(flowType)) {
                // Get node's parent method and inputs of node.
                JsonArray inputsOfNode = flowJson
                                .getAsJsonObject("info")
                                .getAsJsonArray("inputs");
                JsonObject parentMethodJson = nodeIndex.get(enclosingMethodId);

                // Parent method must already be found in the node index.
                assert parentMethodJson != null;

                // Add enclosing method as input to this callsite.
                addIntUnique(inputsOfNode, parentMethodJson.get("id").getAsInt());
                parentMethodJson
                                .getAsJsonObject("info")
                                .getAsJsonArray("uses")
                                .add(flowJson.getAsJsonPrimitive("id").getAsInt());
            }

            // Add enclosing method's ID to flow node's info object if applicable.
            if (REQUIRE_ENCLOSING_METHOD_ID.contains(flowType)) {
                if (!flowJson.getAsJsonObject("info").has("enclosingMethod")) {
                    flowJson.getAsJsonObject("info")
                                    .addProperty("enclosingMethod", enclosingMethodId);
                }
            }
        }

        /**
         * Get a String representation of this type flow's code location, or null, if no code
         * location is available.
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
         * Add the IDs of the given {@link TypeFlow}'s inputs and observees to the given JSON array,
         * as uses of the given {@TypeFlow}. Indirectly-recursively serialize the inputs and
         * observees of this type flow, where observees are objects that notify this flow when they
         * change ({@link JsonSerializedGraph#serializeTypeFlow(TypeFlow)}).
         *
         * @param flow TypeFlow who's inputs and observees to collect
         * @param jsonArray target array for input use and observee IDs to
         */
        private void collectInputs(TypeFlow<?> flow, JsonArray jsonArray) {
            for (Object input : flow.getInputs()) {
                TypeFlow<?> inputFlow = (TypeFlow<?>) input;
                addIntUnique(jsonArray, inputFlow.id());
                // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
                // we don't know the method ID of the parent methods of the input flows.
                serializeTypeFlow(inputFlow);
            }
            for (Object observee : flow.getObservees()) {
                TypeFlow<?> observeeFlow = (TypeFlow<?>) observee;
                addIntUnique(jsonArray, observeeFlow.id());
                // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
                // we don't know the method ID of the parent methods of the observee flows.
                serializeTypeFlow(observeeFlow);
            }
        }

        /**
         * Add the IDs of the given {@link TypeFlow}'s uses and observers to the given JSON array,
         * as uses of the given {@TypeFlow}. Indirectly-recursively serialize the uses and observers
         * of this type flow, where observers are objects that are notified when this flow changes.
         * {@link JsonSerializedGraph#serializeTypeFlow(TypeFlow)}.
         *
         * @param flow TypeFlow who's uses and observers to collect
         * @param jsonArray target array for adding use and observer IDs to
         */
        private void collectUses(TypeFlow<?> flow, JsonArray jsonArray) {
            for (Object use : flow.getUses()) {
                TypeFlow<?> useFlow = (TypeFlow<?>) use;
                addIntUnique(jsonArray, useFlow.id());
                // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
                // we don't know the method ID of the parent methods of the use flows.
                serializeTypeFlow(useFlow);
            }
            for (Object observer : flow.getObservers()) {
                TypeFlow<?> observerFlow = (TypeFlow<?>) observer;
                addIntUnique(jsonArray, observerFlow.id());
                // Indirect recursive call. Call with methodId = -1 to indicate that, at this point,
                // we don't know the method ID of the parent methods of the observer flows.
                serializeTypeFlow(observerFlow);
            }
        }

        /**
         * Add an integer to a JSON array, if and only if the array does not already contain that
         * integer.
         *
         * @param jsonArray target JSON array
         * @param element integer to add
         */
        private static void addIntUnique(JsonArray jsonArray, int element) {
            if (!jsonArray.contains(new JsonPrimitive(element))) {
                jsonArray.add(element);
            }
        }

        /**
         * Serialize a given {@link TypeState} into a JSON array containing the formatted class name
         * of {@link AnalysisType} in the TypeState.
         *
         * @param typeState the TypeState to be serialized.
         * @return a JSON array of the formatted class names of the classes included in the given
         *         TypeState.
         */
        private static JsonArray serializeTypeState(TypeState typeState) {
            JsonArray types = new JsonArray();
            if (typeState.getClass().getSimpleName().equals("UnknownTypeState")) {
                return types;
            }
            for (AnalysisType type : typeState.types()) {
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
         * Ensure symmetric relations between uses and inputs across all nodes. That is, if node A
         * lists node B as an input, ensure that node B lists node A as a use, and vice versa.
         */
        private void matchInputsAndUses() {
            Collection<JsonObject> nodes = nodeIndex.values();
            for (JsonObject node : nodes) {
                matchFromTo(node, "inputs");
                matchFromTo(node, "uses");
            }
        }

        /**
         * Ensure symmetric references between inputs or uses for a given node.
         *
         * @param fromNode node for which to ensure symmetric inputs or uses
         * @param from "inputs" or "uses" - the array to match
         * @see JsonSerializedGraph#matchInputsAndUses()
         */
        private void matchFromTo(JsonObject fromNode, String from) {
            assert (from.equals("inputs") || from.equals("uses"));
            String to = from.equals("inputs") ? "uses" : "inputs";
            int nodeId = fromNode.get("id").getAsInt();
            JsonArray fromIds = fromNode.getAsJsonObject("info").getAsJsonArray(from);
            for (JsonElement fromIdJson : fromIds) {
                int fromId = fromIdJson.getAsInt();
                JsonObject referencedNode = nodeIndex.get(fromId);
                if (referencedNode == null) {
                    continue;
                }
                JsonArray usesJsonArray = referencedNode.getAsJsonObject("info").getAsJsonArray(to);
                addIntUnique(usesJsonArray, nodeId);
            }
        }

        /**
         * Export pointsto graph into a JSON string, in the format expected by the dashboard.
         *
         * @return JSON serialization of pointsto graph
         * @see JsonSerializedGraph
         */
        public JsonObject toJson() {
            if (!isBuilt) {
                throw new IllegalStateException("Tried exporting to JSON before building.");
            }
            JsonArray nodes = new JsonArray();
            for (JsonObject node : nodeIndex.values()) {
                nodes.add(node);
            }
            JsonObject root = new JsonObject();
            root.add("type-flows", nodes);
            return root;
        }
    }

    JsonObject dump(OnAnalysisExitAccess access) {
        try {
            FeatureImpl.OnAnalysisExitAccessImpl config = (FeatureImpl.OnAnalysisExitAccessImpl) access;
            BigBang bigbang = config.getBigBang();
            JsonSerializedGraph serializedGraph = new JsonSerializedGraph(bigbang);
            serializedGraph.build();
            return serializedGraph.toJson();
        } catch (Exception e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
