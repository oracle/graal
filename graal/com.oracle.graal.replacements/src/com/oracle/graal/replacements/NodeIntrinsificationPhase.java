/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.jvmci.meta.MetaUtil.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.InjectedNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.debug.internal.*;
import com.oracle.jvmci.meta.*;

/**
 * Replaces calls to {@link NodeIntrinsic}s with nodes and calls to methods annotated with
 * {@link Fold} with the result of invoking the annotated method via reflection.
 */
public class NodeIntrinsificationPhase extends Phase {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final SnippetReflectionProvider snippetReflection;
    private final ForeignCallsProvider foreignCalls;
    private final StampProvider stampProvider;

    public NodeIntrinsificationPhase(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls,
                    StampProvider stampProvider) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.snippetReflection = snippetReflection;
        this.foreignCalls = foreignCalls;
        this.stampProvider = stampProvider;
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
        ResolvedJavaType declaringClass = target.getDeclaringClass();
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
            ResolvedJavaType[] parameterTypes = resolveJavaTypes(target.toParameterTypes(), declaringClass);
            JavaConstant constant = tryFold(methodCallTargetNode.arguments(), parameterTypes, target);
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

    public static final JavaConstant COULD_NOT_FOLD = new PrimitiveConstant(Kind.Illegal, 100) {
        @Override
        public boolean equals(Object o) {
            return this == o;
        }
    };

    public JavaConstant tryFold(List<ValueNode> args, ResolvedJavaType[] parameterTypes, ResolvedJavaMethod target) {
        JavaConstant[] reflectArgs = (JavaConstant[]) prepareArguments(args, parameterTypes, target, true);
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

    private static boolean areAllConstant(List<ValueNode> arguments) {
        for (ValueNode arg : arguments) {
            if (!arg.isConstant()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Attempts to create a node to replace a call to a {@link NodeIntrinsic} annotated method.
     *
     * @param arguments the arguments of the call
     * @param stamp the stamp to use for the returned node
     * @param method the method annotated with {@link NodeIntrinsic}
     * @param graph the graph into which the created node will be added
     * @return a {@link ConstantNode} if the intrinsic could be
     *         {@linkplain NodeIntrinsic#foldable() folded}, {@code null} if intrinsification could
     *         not (yet) be performed, otherwise the node representing the intrinsic
     */
    public ValueNode createIntrinsicNode(List<ValueNode> arguments, Stamp stamp, ResolvedJavaMethod method, StructuredGraph graph, NodeIntrinsic intrinsic) {
        assert method.getAnnotation(Fold.class) == null;
        assert method.isStatic() : "node intrinsic must be static: " + method;

        ResolvedJavaType[] parameterTypes = resolveJavaTypes(method.toParameterTypes(), method.getDeclaringClass());

        if (intrinsic.foldable() && areAllConstant(arguments)) {
            JavaConstant res = tryFold(arguments, parameterTypes, method);
            if (!res.equals(COULD_NOT_FOLD)) {
                return ConstantNode.forConstant(res, metaAccess);
            }
        }

        // Prepare the arguments for the reflective constructor call on the node class.
        Object[] nodeConstructorArguments = prepareArguments(arguments, parameterTypes, method, false);
        if (nodeConstructorArguments == null) {
            return null;
        }

        // Create the new node instance.
        ResolvedJavaType c = getNodeClass(method, intrinsic);
        return createNodeInstance(graph, c, parameterTypes, stamp, intrinsic.setStampFromReturnType(), nodeConstructorArguments);
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

    /**
     * Converts the arguments of an invoke node to object values suitable for use as the arguments
     * to a reflective invocation of a Java constructor or method.
     *
     * @param folding specifies if the invocation is for handling a {@link Fold} annotation
     * @return the arguments for the reflective invocation or null if an argument of {@code invoke}
     *         that is expected to be constant isn't
     */
    private Object[] prepareArguments(List<ValueNode> arguments, ResolvedJavaType[] parameterTypes, ResolvedJavaMethod target, boolean folding) {
        Object[] reflectionCallArguments = folding ? new JavaConstant[arguments.size()] : new Object[arguments.size()];
        for (int i = 0; i < reflectionCallArguments.length; ++i) {
            int parameterIndex = i;
            if (!target.isStatic()) {
                parameterIndex--;
            }
            ValueNode argument = arguments.get(i);
            if (folding || target.getParameterAnnotation(ConstantNodeParameter.class, parameterIndex) != null) {
                if (!(argument instanceof ConstantNode)) {
                    return null;
                }
                ConstantNode constantNode = (ConstantNode) argument;
                Constant constant = constantNode.asConstant();
                /*
                 * For intrinsification (but not for folding) if we have a Class<?> object we want
                 * the corresponding ResolvedJavaType.
                 */
                ResolvedJavaType type = folding ? null : constantReflection.asJavaType(constant);
                Object arg;
                if (type != null) {
                    /* If we found such a type then it's our arg */
                    arg = type;
                    parameterTypes[i] = metaAccess.lookupJavaType(ResolvedJavaType.class);
                } else {
                    JavaConstant javaConstant = (JavaConstant) constant;
                    if (folding) {
                        /* For folding we want JavaConstants */
                        arg = javaConstant;
                    } else {
                        /* For intrinsification we want want corresponding objects */
                        if (parameterTypes[i].getKind() == Kind.Boolean) {
                            arg = Boolean.valueOf(javaConstant.asInt() != 0);
                        } else if (parameterTypes[i].getKind() == Kind.Byte) {
                            arg = Byte.valueOf((byte) javaConstant.asInt());
                        } else if (parameterTypes[i].getKind() == Kind.Short) {
                            arg = Short.valueOf((short) javaConstant.asInt());
                        } else if (parameterTypes[i].getKind() == Kind.Char) {
                            arg = Character.valueOf((char) javaConstant.asInt());
                        } else if (parameterTypes[i].getKind() == Kind.Object) {
                            arg = snippetReflection.asObject(parameterTypes[i], javaConstant);
                        } else {
                            arg = javaConstant.asBoxedPrimitive();
                        }
                    }
                }

                assert folding || !(arg instanceof JavaConstant);
                reflectionCallArguments[i] = arg;
            } else {
                reflectionCallArguments[i] = argument;
                parameterTypes[i] = metaAccess.lookupJavaType(ValueNode.class);
            }
        }
        return reflectionCallArguments;
    }

    public ResolvedJavaType getNodeClass(ResolvedJavaMethod target, NodeIntrinsic intrinsic) {
        ResolvedJavaType result;
        if (intrinsic.value() == NodeIntrinsic.class) {
            result = target.getDeclaringClass();
        } else {
            result = metaAccess.lookupJavaType(intrinsic.value());
        }
        assert metaAccess.lookupJavaType(ValueNode.class).isAssignableFrom(result) : "Node intrinsic class " + result.toJavaName(false) + " derived from @" + NodeIntrinsic.class.getSimpleName() +
                        " annotation on " + target.format("%H.%n(%p)") + " is not a subclass of " + ValueNode.class;
        return result;
    }

    protected ValueNode createNodeInstance(StructuredGraph graph, ResolvedJavaType nodeClass, ResolvedJavaType[] parameterTypes, Stamp invokeStamp, boolean setStampFromReturnType,
                    Object[] nodeConstructorArguments) {
        ResolvedJavaMethod constructor = null;
        Object[] arguments = null;

        for (ResolvedJavaMethod c : nodeClass.getDeclaredConstructors()) {
            Object[] match = match(graph, invokeStamp, c, parameterTypes, nodeConstructorArguments);

            if (match != null) {
                if (constructor == null) {
                    constructor = c;
                    arguments = match;
                    if (!Debug.isEnabled()) {
                        // Don't verify there's a unique match in non-debug mode
                        break;
                    }
                } else {
                    throw new JVMCIError("Found multiple constructors in %s compatible with signature %s: %s, %s", nodeClass.toJavaName(), sigString(parameterTypes), constructor, c);
                }
            }
        }
        if (constructor == null) {
            throw new JVMCIError("Could not find constructor in %s compatible with signature %s", nodeClass.toJavaName(), sigString(parameterTypes));
        }

        try {
            ValueNode intrinsicNode = (ValueNode) invokeConstructor(constructor, arguments);

            if (setStampFromReturnType) {
                intrinsicNode.setStamp(invokeStamp);
            }
            return intrinsicNode;
        } catch (Exception e) {
            throw new RuntimeException(constructor + Arrays.toString(nodeConstructorArguments), e);
        }
    }

    protected Object invokeConstructor(ResolvedJavaMethod constructor, Object[] arguments) {
        return snippetReflection.invoke(constructor, null, arguments);
    }

    private static String sigString(ResolvedJavaType[] types) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(types[i].toJavaName());
        }
        return sb.append(")").toString();
    }

    private static boolean checkNoMoreInjected(ResolvedJavaMethod c, int start) {
        int count = c.getSignature().getParameterCount(false);
        for (int i = start; i < count; i++) {
            if (c.getParameterAnnotation(InjectedNodeParameter.class, i) != null) {
                throw new JVMCIError("Injected parameter %d of type %s must precede all non-injected parameters of %s", i,
                                c.getSignature().getParameterType(i, c.getDeclaringClass()).toJavaName(false), c.format("%H.%n(%p)"));
            }
        }
        return true;
    }

    private Object[] match(StructuredGraph graph, Stamp invokeStamp, ResolvedJavaMethod c, ResolvedJavaType[] parameterTypes, Object[] nodeConstructorArguments) {
        Object[] arguments = null;
        Object[] injected = null;

        ResolvedJavaType[] signature = resolveJavaTypes(c.getSignature().toParameterTypes(null), c.getDeclaringClass());
        for (int i = 0; i < signature.length; i++) {
            if (c.getParameterAnnotation(InjectedNodeParameter.class, i) != null) {
                injected = injected == null ? new Object[1] : Arrays.copyOf(injected, injected.length + 1);
                Object injectedParameter = snippetReflection.getInjectedNodeIntrinsicParameter(signature[i]);
                if (injectedParameter != null) {
                    injected[injected.length - 1] = injectedParameter;
                } else if (signature[i].equals(metaAccess.lookupJavaType(MetaAccessProvider.class))) {
                    injected[injected.length - 1] = metaAccess;
                } else if (signature[i].equals(metaAccess.lookupJavaType(StructuredGraph.class))) {
                    injected[injected.length - 1] = graph;
                } else if (signature[i].equals(metaAccess.lookupJavaType(ForeignCallsProvider.class))) {
                    injected[injected.length - 1] = foreignCalls;
                } else if (signature[i].equals(metaAccess.lookupJavaType(SnippetReflectionProvider.class))) {
                    injected[injected.length - 1] = snippetReflection;
                } else if (signature[i].isAssignableFrom(metaAccess.lookupJavaType(Stamp.class))) {
                    injected[injected.length - 1] = invokeStamp;
                } else if (signature[i].isAssignableFrom(metaAccess.lookupJavaType(StampProvider.class))) {
                    injected[injected.length - 1] = stampProvider;
                } else {
                    throw new JVMCIError("Cannot handle injected argument of type %s in %s", signature[i].toJavaName(), c.format("%H.%n(%p)"));
                }
            } else {
                assert checkNoMoreInjected(c, i);
                break;
            }
        }
        if (injected != null) {
            // Chop injected arguments from signature
            signature = Arrays.copyOfRange(signature, injected.length, signature.length);
        }

        if (Arrays.equals(parameterTypes, signature)) {
            // Exact match
            arguments = nodeConstructorArguments;

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

            ResolvedJavaType componentType = signature[fixedArgs].getComponentType();
            assert componentType != null;
            for (int i = fixedArgs; i < nodeConstructorArguments.length; i++) {
                if (!parameterTypes[i].equals(componentType)) {
                    return null;
                }
            }
            arguments = Arrays.copyOf(nodeConstructorArguments, fixedArgs + 1);
            arguments[fixedArgs] = snippetReflection.newArray(componentType, nodeConstructorArguments.length - fixedArgs);

            Object varargs = arguments[fixedArgs];
            for (int i = fixedArgs; i < nodeConstructorArguments.length; i++) {
                if (componentType.isPrimitive()) {
                    Array.set(varargs, i - fixedArgs, nodeConstructorArguments[i]);
                } else {
                    ((Object[]) varargs)[i - fixedArgs] = nodeConstructorArguments[i];
                }
            }
        } else {
            return null;
        }

        if (injected != null) {
            Object[] copy = new Object[injected.length + arguments.length];
            System.arraycopy(injected, 0, copy, 0, injected.length);
            System.arraycopy(arguments, 0, copy, injected.length, arguments.length);
            arguments = copy;
        }
        return arguments;
    }

    private static String sourceLocation(Node n) {
        String loc = GraphUtil.approxSourceLocation(n);
        return loc == null ? "<unknown>" : loc;
    }

    public void cleanUpReturnCheckCast(Node newInstance) {
        if (newInstance instanceof ValueNode && (((ValueNode) newInstance).getKind() != Kind.Object || ((ValueNode) newInstance).stamp() == StampFactory.forNodeIntrinsic())) {
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
        } else if (usage instanceof GuardingPiNode) {
            GuardingPiNode pi = (GuardingPiNode) usage;
            for (Node piUsage : pi.usages().snapshot()) {
                checkCheckCastUsage(graph, intrinsifiedNode, usage, piUsage);
            }
            graph.removeFixed(pi);
        } else {
            DebugScope.forceDump(graph, "exception");
            assert false : sourceLocation(usage) + " has unexpected usage " + usage + " of checkcast " + input + " at " + sourceLocation(input);
        }
    }
}
