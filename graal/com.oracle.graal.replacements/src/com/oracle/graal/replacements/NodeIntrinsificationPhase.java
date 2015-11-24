/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.InjectedNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ProxyNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValueProxyNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.UnboxNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.nodes.graphbuilderconf.NodeIntrinsicPluginFactory.InjectionProvider;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.word.WordTypes;

/**
 * Replaces calls to {@link NodeIntrinsic}s with nodes and calls to methods annotated with
 * {@link Fold} with the result of invoking the annotated method via reflection.
 */
public class NodeIntrinsificationPhase extends Phase implements InjectionProvider {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final SnippetReflectionProvider snippetReflection;
    private final ForeignCallsProvider foreignCalls;
    private final StampProvider stampProvider;
    private final WordTypes wordTypes;

    public NodeIntrinsificationPhase(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls,
                    StampProvider stampProvider, WordTypes wordTypes) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.snippetReflection = snippetReflection;
        this.foreignCalls = foreignCalls;
        this.stampProvider = stampProvider;
        this.wordTypes = wordTypes;
    }

    @Override
    public Stamp getReturnStamp(Class<?> type) {
        JavaKind kind = JavaKind.fromJavaClass(type);
        if (kind == JavaKind.Object) {
            ResolvedJavaType returnType = metaAccess.lookupJavaType(type);
            if (wordTypes.isWord(returnType)) {
                return wordTypes.getWordStamp(returnType);
            } else {
                return StampFactory.declared(returnType);
            }
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public <T> T getInjectedArgument(Class<T> type) {
        T injected = snippetReflection.getInjectedNodeIntrinsicParameter(type);
        if (injected != null) {
            return injected;
        } else if (type.equals(ForeignCallsProvider.class)) {
            return type.cast(foreignCalls);
        } else if (type.equals(SnippetReflectionProvider.class)) {
            return type.cast(snippetReflection);
        } else {
            throw new JVMCIError("Cannot handle injected argument of type %s.", type.getName());
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        ArrayList<Node> cleanUpReturnList = new ArrayList<>();
        for (MethodCallTargetNode node : graph.getNodes(MethodCallTargetNode.TYPE)) {
            tryIntrinsify(node, cleanUpReturnList);
        }

        for (Node node : cleanUpReturnList) {
            cleanUpReturnCheckCast(node);
        }
    }

    protected boolean tryIntrinsify(MethodCallTargetNode methodCallTargetNode, List<Node> cleanUpReturnList) {
        ResolvedJavaMethod target = methodCallTargetNode.targetMethod();
        StructuredGraph graph = methodCallTargetNode.graph();

        NodeIntrinsic intrinsic = getIntrinsic(target);
        if (intrinsic != null) {
            Stamp stamp = methodCallTargetNode.invoke().asNode().stamp();
            Node newInstance = createIntrinsicNode(methodCallTargetNode.arguments(), stamp, target, graph, intrinsic);
            if (newInstance == null) {
                return false;
            }

            // Replace the invoke with the new node.
            newInstance = graph.addOrUnique(newInstance);
            methodCallTargetNode.invoke().intrinsify(newInstance);

            // Clean up checkcast instructions inserted by javac if the return type is generic.
            cleanUpReturnList.add(newInstance);
        } else if (isFoldable(target)) {
            JavaConstant constant = tryFold(methodCallTargetNode.arguments(), target);
            if (constant != null && constant.equals(COULD_NOT_FOLD)) {
                return false;
            }

            if (constant != null) {
                // Replace the invoke with the result of the call
                ConstantNode node = ConstantNode.forConstant(constant, metaAccess, methodCallTargetNode.graph());
                methodCallTargetNode.invoke().intrinsify(node);

                // Clean up checkcast instructions inserted by javac if the return type is generic.
                cleanUpReturnList.add(node);
            } else {
                // Remove the invoke
                methodCallTargetNode.invoke().intrinsify(null);
            }
        }
        return true;
    }

    public static final JavaConstant COULD_NOT_FOLD = new PrimitiveConstant(JavaKind.Illegal, 100) {
        @Override
        public boolean equals(Object o) {
            return this == o;
        }
    };

    public JavaConstant tryFold(List<ValueNode> args, ResolvedJavaMethod target) {
        JavaConstant[] reflectArgs = prepareFoldingArguments(args);
        if (reflectArgs == null) {
            return COULD_NOT_FOLD;
        }
        JavaConstant receiver = null;
        if (!target.isStatic()) {
            receiver = reflectArgs[0];
            reflectArgs = Arrays.copyOfRange(reflectArgs, 1, reflectArgs.length);
        }

        // Call the method
        return target.invoke(receiver, reflectArgs);
    }

    /**
     * Select a constructor and prepare the injected arguments for intrinsification of a call to a
     * {@link NodeIntrinsic} annotated method.
     *
     * @param arguments the arguments of the call
     * @param stamp the stamp to use for the returned node
     * @param method the method annotated with {@link NodeIntrinsic}
     * @return an {@link NodeIntrinsicFactory} that can be used to
     *         {@link NodeIntrinsicFactory#intrinsify intrinsify} the call
     */
    public NodeIntrinsicFactory createIntrinsicFactory(List<ValueNode> arguments, Stamp stamp, ResolvedJavaMethod method, NodeIntrinsic intrinsic) {
        assert method.getAnnotation(Fold.class) == null;
        assert method.isStatic() : "node intrinsic must be static: " + method;

        Class<? extends ValueNode> nodeClass = getNodeClass(method, intrinsic);
        Class<?>[] parameterTypes = prepareIntrinsicArgumentTypes(method);

        return createIntrinsicFactory(nodeClass, parameterTypes, arguments, stamp, intrinsic.setStampFromReturnType());
    }

    /**
     * Attempts to create a node to replace a call to a {@link NodeIntrinsic} annotated method.
     *
     * @param arguments the arguments of the call
     * @param stamp the stamp to use for the returned node
     * @param method the method annotated with {@link NodeIntrinsic}
     * @param graph the graph into which the created node will be added
     * @return {@code null} if intrinsification could not (yet) be performed, otherwise the node
     *         representing the intrinsic
     */
    public ValueNode createIntrinsicNode(List<ValueNode> arguments, Stamp stamp, ResolvedJavaMethod method, StructuredGraph graph, NodeIntrinsic intrinsic) {
        NodeIntrinsicFactory factory = createIntrinsicFactory(arguments, stamp, method, intrinsic);
        return factory.intrinsify(graph, constantReflection, snippetReflection);
    }

    /**
     * Permits a subclass to override the default definition of "intrinsic".
     */
    public NodeIntrinsic getIntrinsic(ResolvedJavaMethod method) {
        return method.getAnnotation(Node.NodeIntrinsic.class);
    }

    /**
     * Permits a subclass to override the default definition of "foldable".
     */
    public boolean isFoldable(ResolvedJavaMethod method) {
        return method.getAnnotation(Fold.class) != null;
    }

    private Class<?>[] prepareIntrinsicArgumentTypes(ResolvedJavaMethod target) {
        Signature signature = target.getSignature();
        boolean hasReceiver = !target.isStatic();

        Class<?>[] argumentTypes = new Class<?>[signature.getParameterCount(hasReceiver)];
        for (int i = 0; i < argumentTypes.length; i++) {
            int parameterIndex = i;
            if (hasReceiver) {
                parameterIndex--;
            }
            if (target.getParameterAnnotation(ConstantNodeParameter.class, parameterIndex) != null) {
                ResolvedJavaType type = signature.getParameterType(parameterIndex, target.getDeclaringClass()).resolve(target.getDeclaringClass());
                Class<?> cls;
                if (type.isPrimitive()) {
                    cls = type.getJavaKind().toJavaClass();
                } else {
                    cls = snippetReflection.asObject(Class.class, type.getJavaClass());
                }
                /*
                 * If the node intrinsic method has a constant Class<?> argument, the node
                 * constructor wants the corresponding ResolvedJavaType.
                 */
                if (cls.equals(Class.class)) {
                    argumentTypes[i] = ResolvedJavaType.class;
                } else {
                    argumentTypes[i] = cls;
                }
            } else {
                argumentTypes[i] = ValueNode.class;
            }
        }

        return argumentTypes;
    }

    /**
     * Converts the arguments of an invoke node to object values suitable for use as the arguments
     * to a reflective invocation of a ResolvedJavaMethod.
     *
     * @return the arguments for the reflective invocation or null if an argument of {@code invoke}
     *         is not constant
     */
    private static JavaConstant[] prepareFoldingArguments(List<ValueNode> arguments) {
        JavaConstant[] reflectionCallArguments = new JavaConstant[arguments.size()];
        for (int i = 0; i < reflectionCallArguments.length; ++i) {
            ValueNode argument = arguments.get(i);
            if (!(argument instanceof ConstantNode)) {
                return null;
            }

            ConstantNode constantNode = (ConstantNode) argument;
            reflectionCallArguments[i] = (JavaConstant) constantNode.asConstant();
        }
        return reflectionCallArguments;
    }

    public Class<? extends ValueNode> getNodeClass(ResolvedJavaMethod target, NodeIntrinsic intrinsic) {
        Class<?> result;
        if (intrinsic.value() == NodeIntrinsic.class) {
            ResolvedJavaType type = target.getDeclaringClass();
            result = snippetReflection.asObject(Class.class, type.getJavaClass());
        } else {
            result = intrinsic.value();
        }
        assert ValueNode.class.isAssignableFrom(result) : "Node intrinsic class " + result + " derived from @" + NodeIntrinsic.class.getSimpleName() + " annotation on " + target.format("%H.%n(%p)") +
                        " is not a subclass of " + ValueNode.class;
        return result.asSubclass(ValueNode.class);
    }

    protected NodeIntrinsicFactory createIntrinsicFactory(Class<? extends ValueNode> nodeClass, Class<?>[] parameterTypes, List<ValueNode> arguments, Stamp invokeStamp, boolean setStampFromReturnType) {
        NodeIntrinsicFactory ret = null;

        for (Constructor<?> c : nodeClass.getDeclaredConstructors()) {
            NodeIntrinsicFactory match = match(invokeStamp, setStampFromReturnType, c, parameterTypes, arguments);

            if (match != null) {
                if (ret == null) {
                    ret = match;
                    if (!Debug.isEnabled()) {
                        // Don't verify there's a unique match in non-debug mode
                        break;
                    }
                } else {
                    throw new JVMCIError("Found multiple constructors in %s compatible with signature %s", nodeClass.getName(), sigString(parameterTypes));
                }
            }
        }

        if (ret == null) {
            throw new JVMCIError("Could not find constructor in %s compatible with signature %s", nodeClass.getName(), sigString(parameterTypes));
        }

        return ret;
    }

    protected Object invokeConstructor(ResolvedJavaMethod constructor, Object[] arguments) {
        return snippetReflection.invoke(constructor, null, arguments);
    }

    private static String sigString(Class<?>[] types) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(types[i].getSimpleName());
        }
        return sb.append(")").toString();
    }

    private static boolean checkNoMoreInjected(Constructor<?> c, int start) {
        int count = c.getParameterCount();
        for (int i = start; i < count; i++) {
            if (getParameterAnnotation(c, InjectedNodeParameter.class, i) != null) {
                throw new JVMCIError("Injected parameter %d of type %s must precede all non-injected parameters of %s", i, c.getParameterTypes()[i], c.toString());
            }
        }
        return true;
    }

    private static <T extends Annotation> T getParameterAnnotation(Executable e, Class<T> annotationClass, int parameterIndex) {
        if (parameterIndex >= 0) {
            Annotation[][] parameterAnnotations = e.getParameterAnnotations();
            for (Annotation a : parameterAnnotations[parameterIndex]) {
                if (a.annotationType() == annotationClass) {
                    return annotationClass.cast(a);
                }
            }
        }
        return null;
    }

    private NodeIntrinsicFactory match(Stamp invokeStamp, boolean setStampFromReturnType, Constructor<?> c, Class<?>[] parameterTypes, List<ValueNode> argumentNodes) {
        Class<?>[] signature = c.getParameterTypes();

        int injectedCount = 0;
        for (int i = 0; i < signature.length; i++) {
            if (getParameterAnnotation(c, InjectedNodeParameter.class, i) != null) {
                injectedCount++;
            } else {
                assert checkNoMoreInjected(c, i);
                break;
            }
        }

        Object[] injected = null;
        if (injectedCount > 0) {
            injected = new Object[injectedCount];
            for (int i = 0; i < injected.length; i++) {
                assert getParameterAnnotation(c, InjectedNodeParameter.class, i) != null;
                Object injectedParameter = snippetReflection.getInjectedNodeIntrinsicParameter(signature[i]);
                if (injectedParameter != null) {
                    injected[i] = injectedParameter;
                } else if (signature[i].equals(MetaAccessProvider.class)) {
                    injected[i] = metaAccess;
                } else if (signature[i].equals(StructuredGraph.class)) {
                    injected[i] = NodeIntrinsicFactory.GRAPH_MARKER;
                } else if (signature[i].equals(ForeignCallsProvider.class)) {
                    injected[i] = foreignCalls;
                } else if (signature[i].equals(SnippetReflectionProvider.class)) {
                    injected[i] = snippetReflection;
                } else if (signature[i].isAssignableFrom(Stamp.class)) {
                    injected[i] = invokeStamp;
                } else if (signature[i].isAssignableFrom(StampProvider.class)) {
                    injected[i] = stampProvider;
                } else {
                    throw new JVMCIError("Cannot handle injected argument of type %s in %s", signature[i].getName(), c.toString());
                }
            }

            // Chop injected arguments from signature
            signature = Arrays.copyOfRange(signature, injected.length, signature.length);
        }

        if (Arrays.equals(parameterTypes, signature)) {
            // Exact match
            return new NodeIntrinsicFactory(invokeStamp, metaAccess.lookupJavaMethod(c), setStampFromReturnType, injected, parameterTypes, argumentNodes);

        } else if (signature.length > 0 && signature[signature.length - 1].isArray()) {
            // Last constructor parameter is an array, so check if we have a vararg match
            int fixedArgs = signature.length - 1;
            if (parameterTypes.length < fixedArgs) {
                return null;
            }
            for (int i = 0; i < fixedArgs; i++) {
                if (!parameterTypes[i].equals(signature[i])) {
                    return null;
                }
            }

            Class<?> componentType = signature[fixedArgs].getComponentType();
            assert componentType != null;
            for (int i = fixedArgs; i < argumentNodes.size(); i++) {
                if (!parameterTypes[i].equals(componentType)) {
                    return null;
                }
            }

            List<ValueNode> fixed = argumentNodes.subList(0, fixedArgs);
            List<ValueNode> varargs = argumentNodes.subList(fixedArgs, argumentNodes.size());

            return new NodeIntrinsicFactory(invokeStamp, metaAccess.lookupJavaMethod(c), setStampFromReturnType, injected, parameterTypes, fixed, componentType, varargs);

        } else {
            return null;
        }
    }

    private static String sourceLocation(Node n) {
        String loc = GraphUtil.approxSourceLocation(n);
        return loc == null ? "<unknown>" : loc;
    }

    public void cleanUpReturnCheckCast(Node newInstance) {
        if (newInstance instanceof ValueNode && (((ValueNode) newInstance).getStackKind() != JavaKind.Object || ((ValueNode) newInstance).stamp() == StampFactory.forNodeIntrinsic())) {
            StructuredGraph graph = (StructuredGraph) newInstance.graph();
            for (CheckCastNode checkCastNode : newInstance.usages().filter(CheckCastNode.class).snapshot()) {
                for (Node checkCastUsage : checkCastNode.usages().snapshot()) {
                    checkCheckCastUsage(graph, newInstance, checkCastNode, checkCastUsage);
                }
                GraphUtil.unlinkFixedNode(checkCastNode);
                GraphUtil.killCFG(checkCastNode);
            }
        }
    }

    private static void checkCheckCastUsage(StructuredGraph graph, Node intrinsifiedNode, Node input, Node usage) {
        if (usage instanceof ValueAnchorNode) {
            ValueAnchorNode valueAnchorNode = (ValueAnchorNode) usage;
            valueAnchorNode.removeAnchoredNode();
            Debug.log("%s: Removed a ValueAnchor input", Debug.contextSnapshot(JavaMethod.class));
        } else if (usage instanceof UnboxNode) {
            UnboxNode unbox = (UnboxNode) usage;
            unbox.replaceAtUsages(intrinsifiedNode);
            graph.removeFixed(unbox);
            Debug.log("%s: Removed an UnboxNode", Debug.contextSnapshot(JavaMethod.class));
        } else if (usage instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) usage;
            store.replaceFirstInput(input, intrinsifiedNode);
        } else if (usage instanceof LoadFieldNode) {
            LoadFieldNode load = (LoadFieldNode) usage;
            load.replaceAtUsages(intrinsifiedNode);
            graph.removeFixed(load);
        } else if (usage instanceof MethodCallTargetNode) {
            MethodCallTargetNode checkCastCallTarget = (MethodCallTargetNode) usage;
            assert checkCastCallTarget.targetMethod().getAnnotation(NodeIntrinsic.class) != null : "checkcast at " + sourceLocation(input) +
                            " not used by an unboxing method or node intrinsic, but by a call at " + sourceLocation(checkCastCallTarget.usages().first()) + " to " + checkCastCallTarget.targetMethod();
            usage.replaceFirstInput(input, intrinsifiedNode);
            Debug.log("%s: Checkcast used in an other node intrinsic", Debug.contextSnapshot(JavaMethod.class));
        } else if (usage instanceof FrameState) {
            usage.replaceFirstInput(input, null);
            Debug.log("%s: Checkcast used in a FS", Debug.contextSnapshot(JavaMethod.class));
        } else if (usage instanceof ReturnNode && ((ValueNode) intrinsifiedNode).stamp() == StampFactory.forNodeIntrinsic()) {
            usage.replaceFirstInput(input, intrinsifiedNode);
            Debug.log("%s: Checkcast used in a return with forNodeIntrinsic stamp", Debug.contextSnapshot(JavaMethod.class));
        } else if (usage instanceof IsNullNode) {
            if (!usage.hasNoUsages()) {
                assert usage.getUsageCount() == 1 && usage.usages().first().predecessor() == input : usage + " " + input;
                graph.replaceFloating((FloatingNode) usage, LogicConstantNode.contradiction(graph));
                Debug.log("%s: Replaced IsNull with false", Debug.contextSnapshot(JavaMethod.class));
            } else {
                // Removed as usage of a GuardingPiNode
            }
        } else if (usage instanceof ProxyNode) {
            ProxyNode proxy = (ProxyNode) usage;
            assert proxy instanceof ValueProxyNode;
            ProxyNode newProxy = ProxyNode.forValue((ValueNode) intrinsifiedNode, proxy.proxyPoint(), graph);
            for (Node proxyUsage : usage.usages().snapshot()) {
                checkCheckCastUsage(graph, newProxy, proxy, proxyUsage);
            }
        } else if (usage instanceof PiNode) {
            for (Node piUsage : usage.usages().snapshot()) {
                checkCheckCastUsage(graph, intrinsifiedNode, usage, piUsage);
            }
        } else {
            DebugScope.forceDump(graph, "exception");
            assert false : sourceLocation(usage) + " has unexpected usage " + usage + " of checkcast " + input + " at " + sourceLocation(input);
        }
    }
}
