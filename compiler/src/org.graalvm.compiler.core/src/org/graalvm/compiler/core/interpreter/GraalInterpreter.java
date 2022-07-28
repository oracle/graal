/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.interpreter;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.interpreter.value.InterpreterValue;
import org.graalvm.compiler.interpreter.value.InterpreterValueArray;
import org.graalvm.compiler.interpreter.value.InterpreterValueFactory;
import org.graalvm.compiler.interpreter.value.InterpreterValueMutableObject;
import org.graalvm.compiler.interpreter.value.InterpreterValueObject;
import org.graalvm.compiler.interpreter.value.InterpreterValuePrimitive;
import org.graalvm.compiler.interpreter.value.JVMContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.InterpreterState;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Interpreter for Graal Intermediate Representation (IR) graphs.
 *
 * The main method is <code>executeGraph(g,args...)</code>, which interprets the graph <code>g</code>
 * of a method, with the given arguments.
 *
 * TODO: treat Java runtime library differently?  Currently the interpreter interprets all methods
 *   and classes, including those in java.lang...  This may not be the best strategy, since some of
 *   those classes have special behavior and fields.  An alternative option might be to just use
 *   the existing classes/objects within certain packages, such as java.lang.
 *
 * TODO: the interpreter does not currently initialise all static fields - just the private static fields
 *   of the class containing the starting method, plus only the public or protected static fields of its
 *   superclasses.  Static fields of other classes are not initialised at all.
 *   Java VarHandles may be a better way of finding all static fields, rather than reflection.
 *
 * TODO: the interpreter heap representation needs reviewing and extending.
 *   1. Node pointers may not be the best index, once objects are passed between methods.
 *   2. Ideally, the interpreter heap should use weak references for its values, so that objects in that
 *      heap can be garbage collected once they are no longer accessible via any of the activation frames.
 *   3. The asObject() method, which converts the interpreter heap representation of an object into a
 *      native Java object, has not been implemented yet, and requires some tricky reflection.  Alternatively,
 *      we could try representing all objects by native Java objects and use reflection or VarHandles to
 *      read and write the fields of those objects.
 */
public class GraalInterpreter {
    private final InterpreterStateImpl myState;
    private final InterpreterValueFactory valueFactory;
    private final HighTierContext context;
    private final JVMContext jvmContext;
    private boolean verbose = false;

    /**
     * Create a new Graal IR graph interpreter.
     *
     * @param context
     */
    public GraalInterpreter(HighTierContext context, ClassLoader classLoader, MethodHandles.Lookup lookup) {
        this.context = context;
        this.jvmContext = new JVMContextImpl(classLoader, lookup);
        this.valueFactory = new InterpreterValueFactoryImpl(context, this.jvmContext);
        this.myState = new InterpreterStateImpl();
    }

    public boolean getVerbose() {
        return this.verbose;
    }

    /**
     * Set the verbose flag, to control printing of control-flow execution progress.
     *
     * @param verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Interprets the graph of a method, with the given arguments.
     *
     * It will throw a GraalError exception if the graph contains any nodes that
     * have not yet implemented the appropriate <code>interpret()</code> and/or
     * <code>interpretExpr()</code> methods.
     *
     * @param graph
     * @param args
     * @return the return value of the method or a wrapped exception.
     * @throws InvocationTargetException
     */
    public Object executeGraph(StructuredGraph graph, Object... args) throws InvocationTargetException {
        ArrayList<InterpreterValue> evaluatedParams = new ArrayList<>();
        for (Object arg : args) {
            evaluatedParams.add(valueFactory.createFromObject(arg));
        }

        // TODO: static class initialisation? (<clinit>): Does loadStaticFields handle this?
        InterpreterValue returnValue = myState.interpretGraph(graph, evaluatedParams);

        // TODO: extend this to return objects, by implementing InterpreterValueObject.asObject().
        Object returnObject = returnValue.asObject();
        if (returnValue.isUnwindException()) {
            GraalError.guarantee(returnObject instanceof Exception, "isException returned true but underlying interpreter value is not an Exception object");
            throw new InvocationTargetException((Exception) returnValue.asObject());
        } else {
            return returnObject;
        }
    }

    private final class InterpreterStateImpl implements InterpreterState {
        private final Map<Node, InterpreterValue> heap = new HashMap<>();
        private final Deque<ActivationRecord> activations = new ArrayDeque<>();
        private final Map<ResolvedJavaField, InterpreterValue> fieldMap = new HashMap<>();
        private final Set<Class<?>> typesAlreadyStaticallyLoaded = new HashSet<>();

        private void checkActivationsNotEmpty() {
            GraalError.guarantee(!activations.isEmpty(), "No activation records");
        }

        void addActivation(List<InterpreterValue> args) {
            activations.addLast(new ActivationRecord(args));
        }

        private ActivationRecord popActivation() {
            checkActivationsNotEmpty();
            return activations.pollLast();
        }

        @Override
        public void setHeapValue(Node node, InterpreterValue value) {
            heap.put(node, value);
        }

        @Override
        public InterpreterValue getHeapValue(Node node) {
            GraalError.guarantee(heap.containsKey(node), "No heap entry for node: %s", node);
            return heap.get(node);
        }

        @Override
        public void setNodeLookupValue(Node node, InterpreterValue value) {
            checkActivationsNotEmpty();
            if (verbose) {
                System.out.printf("    local[%s] := %s\n", node, value);
            }
            activations.peekLast().setNodeValue(node, value);
        }

        @Override
        public boolean hasNodeLookupValue(Node node) {
            checkActivationsNotEmpty();
            return activations.peekLast().hasNodeValue(node);
        }

        @Override
        public InterpreterValue getNodeLookupValue(Node node) {
            checkActivationsNotEmpty();
            return activations.peekLast().getNodeValue(node);
        }

        @Override
        public void setMergeNodeIncomingIndex(AbstractMergeNode node, int index) {
            checkActivationsNotEmpty();
            // System.out.printf("setMergeIndex(%s,%d)\n", node, index);
            activations.peekLast().setMergeIndex(node, index);
        }

        @Override
        public int getMergeNodeIncomingIndex(AbstractMergeNode node) {
            checkActivationsNotEmpty();
            return activations.peekLast().getMergeIndex(node);
        }

        @Override
        public InterpreterValue interpretMethod(CallTargetNode target, List<ValueNode> argumentNodes) {
            ResolvedJavaMethod meth = target.targetMethod();
            List<InterpreterValue> evaluatedArgs = argumentNodes.stream().map(this::interpretExpr).collect(Collectors.toList());
            StructuredGraph methodGraph = new StructuredGraph.Builder(target.getOptions(),
                            target.getDebug(), StructuredGraph.AllowAssumptions.YES).method(meth).build();
            context.getGraphBuilderSuite().apply(methodGraph, context);
            InterpreterValue result = interpretGraph(methodGraph, evaluatedArgs);
            return result;
        }

        @Override
        public InterpreterValueFactory getRuntimeValueFactory() {
            return valueFactory;
        }

        @Override
        public JVMContext getJVMContext() {
            return jvmContext;
        }

        @Override
        public InterpreterValue interpretExpr(Node node) {
            GraalError.guarantee(node != null, "Tried to interpret null dataflow node");
            GraalError.guarantee(node instanceof ValueNode, "Tried to interpret non ValueNode: %s", node);
            return ((ValueNode) node).interpretExpr(this);
        }

        @Override
        public InterpreterValue loadStaticFieldValue(ResolvedJavaField field) {
            return fieldMap.getOrDefault(field, null);
        }

        @Override
        public void storeStaticFieldValue(ResolvedJavaField field, InterpreterValue value) {
            fieldMap.put(field, value);
        }

        @Override
        public InterpreterValue getParameter(int index) {
            if (index < 0 || index >= activations.peekLast().evaluatedParams.size()) {
                throw new IllegalArgumentException("out-of-range parameter index: " + index);
            }
            return activations.peekLast().evaluatedParams.get(index);
        }

        private InterpreterValue interpretGraph(StructuredGraph graph, List<InterpreterValue> evaluatedParams) {
            if (verbose) {
                System.out.printf("interpret graph %s with params %s\n", graph.method(), evaluatedParams);
            }
            addActivation(evaluatedParams);
            loadStaticFields(graph);
            FixedNode next = graph.start();
            InterpreterValue returnVal = null;
            while (next != null) {
                if (verbose) {
                    System.out.printf("  %s...\n", next);
                }
                if (next instanceof ReturnNode || next instanceof UnwindNode) {
                    next.interpret(myState);
                    returnVal = myState.getNodeLookupValue(next);
                    break;
                }
                next = next.interpret(myState);
                GraalError.guarantee(next != null, "interpret() returned null");
            }
            popActivation();
            if (verbose) {
                System.out.printf("interpreted result of %s is %s\n", graph.method(), returnVal);
            }
            return returnVal;
        }

        // TODO: surely this method can be made nicer
        private void loadStaticFields(StructuredGraph graph) {
            Class<?> actualDeclaringClass = null;
            try {
                JavaType hotSpotClassObjectType = graph.asJavaMethod().getDeclaringClass();
                Field classMirror = hotSpotClassObjectType.getClass().getDeclaredField("mirror");
                classMirror.setAccessible(true);
                Object hotSpotClassObjectConstant = classMirror.get(hotSpotClassObjectType);

                Field objectField = hotSpotClassObjectConstant.getClass().getDeclaredField("object");
                objectField.setAccessible(true);
                actualDeclaringClass = (Class<?>) objectField.get(hotSpotClassObjectConstant);

                GraalError.guarantee(actualDeclaringClass != null, "actualDeclaringClass is null");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                GraalError.shouldNotReachHere(e, "loadStaticFields");
            }
            if (!typesAlreadyStaticallyLoaded.add(actualDeclaringClass)) {
                return;
            }
            initStaticFields(actualDeclaringClass.getDeclaredFields(), "");
            // we add public superclass fields too, just in case.
            initStaticFields(actualDeclaringClass.getFields(), "SUPERCLASS");
        }

        private void initStaticFields(Field[] fields, String where) {
            for (Field currentField : fields) {
                try {
                    currentField.setAccessible(true);
                    ResolvedJavaField resolvedField = context.getMetaAccess().lookupJavaField(currentField);
                    if (resolvedField.isStatic()) {
                        if (verbose) {
                            System.out.println("  initializing " + where + " static field: " +
                                    resolvedField.getDeclaringClass().getName() + "." + resolvedField.getName() +
                                    " := " + currentField.get(null));
                        }
                        fieldMap.put(resolvedField, valueFactory.createFromObject(currentField.get(null)));
                    }
                } catch (java.lang.RuntimeException ex) {
                    // we cannot catch java.lang.reflect.InaccessibleObjectException, so must catch its superclass
                    // we skip any fields that raise this error
                } catch (IllegalAccessException e) {
                    GraalError.shouldNotReachHere(e, "initStaticFields IllegalAccessException");
                }
            }
        }
    }

    /** Stores all the data associated with one stack frame. */
    private static final class ActivationRecord {
        private final List<InterpreterValue> evaluatedParams;
        private final Map<Node, InterpreterValue> localState = new HashMap<>();
        private final Map<AbstractMergeNode, Integer> mergeIndexes = new HashMap<>();

        ActivationRecord(List<InterpreterValue> evaluatedParams) {
            this.evaluatedParams = evaluatedParams;
        }

        void setNodeValue(Node node, InterpreterValue value) {
            localState.put(node, value);
        }

        boolean hasNodeValue(Node node) {
            return localState.containsKey(node);
        }

        InterpreterValue getNodeValue(Node node) {
            if (!localState.containsKey(node)) {
                throw new IllegalArgumentException("missing localState for node: " + node.toString() + " keys=" + localState.keySet());
            }
            return localState.getOrDefault(node, null); // WAS: .get(node);
        }

        int getMergeIndex(AbstractMergeNode node) {
            if (!mergeIndexes.containsKey(node)) {
                throw new IllegalArgumentException("missing mergeIndex for merge node: " + node.toString());
            }
            return mergeIndexes.get(node);
        }

        void setMergeIndex(AbstractMergeNode node, int index) {
            mergeIndexes.put(node, index);
        }
    }

    private static final class InterpreterValueFactoryImpl implements InterpreterValueFactory {
        private final HighTierContext context;
        private final JVMContext jvmContext;

        private InterpreterValueFactoryImpl(HighTierContext context, JVMContext jvmContext) {
            this.context = context;
            this.jvmContext = jvmContext;
        }

        @Override
        public InterpreterValueObject createObject(ResolvedJavaType type) {
            return new InterpreterValueMutableObject(jvmContext, type);
        }

        public InterpreterValueObject createObject(ResolvedJavaType type, Object obj) {
            return new InterpreterValueMutableObject(type, obj);
        }

        private InterpreterValueArray createArray(ResolvedJavaType componentType, Object nativeArray) {
            return new InterpreterValueArray(jvmContext, componentType, nativeArray);
        }

        @Override
        public InterpreterValueArray createArray(ResolvedJavaType componentType, int length) {
            return new InterpreterValueArray(jvmContext, componentType, length);
        }

        @Override
        public InterpreterValueArray createMultiArray(ResolvedJavaType elementalType, int[] dimensions) {
            // The current logic only makes sense if the type given is the elemental type - that is,
            // the non-array type in the last dimension
            if (!elementalType.getElementalType().equals(elementalType)) {
                throw new IllegalArgumentException("unimplemented elementalType: " + elementalType);
            }
            // Get the overall type for a multidimensional array with this many dimensions
            ResolvedJavaType overallType = elementalType;
            for (int i = 0; i < dimensions.length; i++) {
                overallType = overallType.getArrayClass();
            }
            return createMultiArray(overallType.getComponentType(), dimensions, 0);
        }

        private InterpreterValueArray createMultiArray(ResolvedJavaType componentType, int[] dimensions, int dimensionIndex) {
            if (dimensionIndex >= dimensions.length || dimensions[dimensionIndex] < 0) {
                throw new IllegalArgumentException("out-of-range dimensionIndex: " + dimensionIndex);
            }
            if (dimensionIndex == dimensions.length - 1) {
                if (componentType.isArray()) {
                    throw new IllegalArgumentException("bad multiarray componentType: " + componentType);
                }
                if (dimensions[dimensionIndex] < 0) {
                    throw new IllegalArgumentException("negative multiarray size: " + dimensions[dimensionIndex]);
                }
                return createArray(componentType, dimensions[dimensionIndex]);
            }
            InterpreterValueArray array = new InterpreterValueArray(jvmContext, componentType, dimensions[dimensionIndex]);
            for (int i = 0; i < dimensions[dimensionIndex]; i++) {
                array.setAtIndex(i, createMultiArray(componentType.getComponentType(), dimensions,
                        dimensionIndex + 1));
            }
            return array;
        }

        @Override
        public InterpreterValue createFromObject(Object value) {
            if (value == null) {
                return InterpreterValue.InterpreterValueNullPointer.INSTANCE;
            }
            PrimitiveConstant unboxed = JavaConstant.forBoxedPrimitive(value);
            if (unboxed != null) {
                return InterpreterValuePrimitive.ofPrimitiveConstant(unboxed);
            }
            if (value.getClass().isArray()) {
                ResolvedJavaType type = context.getMetaAccess().lookupJavaType(value.getClass().getComponentType());
                InterpreterValueArray createdArray = createArray(type, value);
                return createdArray;
            }
            ResolvedJavaType resolvedType = context.getMetaAccess().lookupJavaType(value.getClass());
            InterpreterValueObject createdObject = createObject(resolvedType, value);
            // for (ResolvedJavaField field : resolvedType.getInstanceFields(true)) {
                // TODO: how to get this field out of object and stick into createdObject
            // }
            return createdObject;
        }
    }

    private static final class JVMContextImpl implements JVMContext {
        private final ClassLoader classLoader;
        private final MethodHandles.Lookup lookup;

        JVMContextImpl(ClassLoader classLoader, MethodHandles.Lookup lookup) {
            this.classLoader = classLoader;
            this.lookup = lookup;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public MethodHandles.Lookup getLookup() {
            return lookup;
        }
    }
}
