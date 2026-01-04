/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.graphbuilderconf;

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.ClassPlugins;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Plugin for handling a specific method invocation.
 */
public abstract class InvocationPlugin implements GraphBuilderPlugin {

    /**
     * The receiver in a non-static method. The class literal for this interface must be used with
     * {@link InvocationPlugins#put(Type, InvocationPlugin, boolean)} to denote the receiver
     * argument for such a non-static method.
     */
    public interface Receiver {
        /**
         * Returns the receiver value, optionally null checking it first if necessary.
         *
         * Note that passing true for {@code performNullCheck} is only allowed if the
         * {@link InvocationPlugin} unconditionally intrinsifies the invocation in that code path,
         * i.e., returns true. Otherwise, a premature explicit null check remains in the graph.
         *
         * On the other hand, passing true for {@code performNullCheck} is required in any path
         * where a {@link InvocationPlugin} returns true, otherwise the null check would be missing.
         */
        ValueNode get(boolean performNullCheck);
    }

    /**
     * Name of the method.
     */
    public final String name;

    /**
     * Argument types of the method. If the method is non-static, element 0 of this array must be
     * {@link InvocationPlugin.Receiver} upon initialization and rewritten to declaring class after
     * registration.
     */
    final Type[] argumentTypes;

    /**
     * Determines if the method is static.
     */
    public final boolean isStatic;

    /**
     * Argument descriptor of the method.
     */
    public final String argumentsDescriptor;

    /**
     * Used for chaining a bucket of InvocationPlugins of the same method name in
     * {@link ClassPlugins}.
     */
    InvocationPlugin next;

    public InvocationPlugin(String name, Type... argumentTypes) {
        this.name = name;
        this.argumentTypes = argumentTypes;
        this.isStatic = argumentTypes.length == 0 || argumentTypes[0] != Receiver.class;

        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (int i = isStatic ? 0 : 1; i < argumentTypes.length; i++) {
            buf.append(MetaUtil.toInternalName(argumentTypes[i].getTypeName()));
        }
        buf.append(')');
        this.argumentsDescriptor = buf.toString();
    }

    /**
     * Determines if this plugin can only be used when inlining the method is it associated with.
     * That is, this plugin cannot be used when the associated method is the compilation root.
     */
    public boolean inlineOnly() {
        return false;
    }

    /**
     * Determines if this plugin only decorates the method is it associated with. That is, it
     * inserts nodes prior to the invocation (e.g. some kind of marker nodes) but still expects the
     * parser to process the invocation further.
     */
    public boolean isDecorator() {
        return false;
    }

    /**
     * Determines if this plugin requires the original method to be resolvable. For instance,
     * {@code Reference#refersTo0} is introduced in Java 16 and is optional in earlier versions in
     * case it may be backported.
     */
    public boolean isOptional() {
        return false;
    }

    /**
     * Determines if this plugin can be disabled. For instance, HotSpot intrinsics featuring better
     * performance with specific CPU features can be disabled; utility methods in GraalDirectives
     * can not be disabled. See {@link InvocationPlugins.Options#DisableIntrinsics}.
     */
    public boolean canBeDisabled() {
        return true;
    }

    /**
     * Determines if this plugin is only implemented in Graal. This is useful for adapting HotSpot
     * intrinsic related flags, which for intrinsics not yet implemented in HotSpot are off by
     * default and should not disable Graal plugins.
     */
    public boolean isGraalOnly() {
        return false;
    }

    /**
     * Rewrite the first element of {@link #argumentTypes} to {@code receiverType} for non-static
     * method.
     */
    public void rewriteReceiverType(Type receiverType) {
        GraalError.guarantee(!isStatic, "Cannot rewrite receiver type for a static method.");
        argumentTypes[0] = receiverType;
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
        return defaultHandler(b, targetMethod, receiver);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
        return defaultHandler(b, targetMethod, receiver, arg);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9, ValueNode arg10) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9, ValueNode arg10, ValueNode arg11) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9, ValueNode arg10, ValueNode arg11, ValueNode arg12) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
    }

    /**
     * @see #execute
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9, ValueNode arg10, ValueNode arg11, ValueNode arg12, ValueNode arg13) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
    }

    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9, ValueNode arg10, ValueNode arg11, ValueNode arg12, ValueNode arg13, ValueNode arg14) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
    }

    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7, ValueNode arg8, ValueNode arg9, ValueNode arg10, ValueNode arg11, ValueNode arg12, ValueNode arg13, ValueNode arg14, ValueNode arg15) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }

    /**
     * Executes this plugin against a set of invocation arguments.
     *
     * The default implementation in {@link InvocationPlugin} dispatches to the {@code apply(...)}
     * method that matches the number of arguments.
     *
     * @param targetMethod the method for which this plugin is being applied
     * @param receiver access to the receiver, {@code null} if {@code targetMethod} is static
     * @param argsIncludingReceiver all arguments to the invocation include the receiver in position
     *            0 if {@code targetMethod} is not static
     * @return {@code true} if this plugin handled the invocation of {@code targetMethod}
     *         {@code false} if the graph builder should process the invoke further (e.g., by
     *         inlining it or creating an {@link Invoke} node). A plugin that does not handle an
     *         invocation must not modify the graph being constructed unless it is a
     *         {@linkplain InvocationPlugin#isDecorator() decorator}.
     */
    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver) {
        int n = argsIncludingReceiver.length;
        ValueNode[] a = argsIncludingReceiver;
        if (receiver != null) {
            assert !targetMethod.isStatic();
            assert n > 0;
            if (n == 1) {
                return apply(b, targetMethod, receiver);
            } else if (n == 2) {
                return apply(b, targetMethod, receiver, a[1]);
            } else if (n == 3) {
                return apply(b, targetMethod, receiver, a[1], a[2]);
            } else if (n == 4) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3]);
            } else if (n == 5) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4]);
            } else if (n == 6) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5]);
            } else if (n == 7) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6]);
            } else if (n == 8) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7]);
            } else if (n == 9) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]);
            } else if (n == 10) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9]);
            } else if (n == 11) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10]);
            } else if (n == 12) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11]);
            } else if (n == 13) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12]);
            } else if (n == 14) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13]);
            } else if (n == 15) {
                return apply(b, targetMethod, receiver, a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14]);
            } else {
                return defaultHandler(b, targetMethod, receiver, a);
            }
        } else {
            assert targetMethod.isStatic();
            if (n == 0) {
                return apply(b, targetMethod, null);
            } else if (n == 1) {
                return apply(b, targetMethod, null, a[0]);
            } else if (n == 2) {
                return apply(b, targetMethod, null, a[0], a[1]);
            } else if (n == 3) {
                return apply(b, targetMethod, null, a[0], a[1], a[2]);
            } else if (n == 4) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3]);
            } else if (n == 5) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4]);
            } else if (n == 6) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5]);
            } else if (n == 7) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
            } else if (n == 8) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7]);
            } else if (n == 9) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]);
            } else if (n == 10) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9]);
            } else if (n == 11) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10]);
            } else if (n == 12) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11]);
            } else if (n == 13) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12]);
            } else if (n == 14) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13]);
            } else if (n == 15) {
                return apply(b, targetMethod, null, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14]);
            } else {
                return defaultHandler(b, targetMethod, receiver, a);
            }

        }
    }

    /**
     * Handles an invocation when a specific {@code apply} method is not available.
     */
    public boolean defaultHandler(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod targetMethod, @SuppressWarnings("unused") InvocationPlugin.Receiver receiver,
                    ValueNode... args) {
        throw new GraalError("Invocation plugin for %s does not handle invocations with %d arguments", targetMethod.format("%H.%n(%p)"), args.length);
    }

    public String getSourceLocation() {
        Class<?> c = getClass();
        for (Method m : c.getMethods()) {
            if (m.getName().equals("apply") || m.getName().equals("defaultHandler")) {
                return String.format("%s.%s()", m.getDeclaringClass().getName(), m.getName());
            }
        }
        if (inRuntimeCode()) {
            return String.format("%s.%s()", c.getName(), "apply");
        }
        throw new GraalError("could not find method named \"apply\" or \"defaultHandler\" in " + c.getName());
    }

    public int getArgumentsSize() {
        return argumentTypes.length - (isStatic ? 0 : 1);
    }

    public String getMethodNameWithArgumentsDescriptor() {
        return name + argumentsDescriptor;
    }

    public boolean isSameType(InvocationPlugin other) {
        return isStatic == other.isStatic && name.equals(other.name) && argumentsDescriptor.equals(other.argumentsDescriptor);
    }

    public boolean isSameType(ResolvedJavaMethod method) {
        return isStatic == method.isStatic() && name.equals(method.getName()) && method.getSignature().toMethodDescriptor().startsWith(argumentsDescriptor);
    }

    private static boolean isSameType(Class<?> actualType, Type toMatch) {
        if (actualType == toMatch) {
            return true;
        } else if (toMatch instanceof InvocationPlugins.OptionalLazySymbol) {
            return actualType.getTypeName().equals(toMatch.getTypeName());
        }
        return false;
    }

    public boolean isSameType(Method method) {
        if (isStatic == Modifier.isStatic(method.getModifiers()) && name.equals(method.getName())) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            int offset = isStatic ? 0 : 1;
            if (parameterTypes.length == argumentTypes.length - offset) {
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (!isSameType(parameterTypes[i], argumentTypes[i + offset])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean isSameType(Constructor<?> c) {
        if (!isStatic && "<init>".equals(name)) {
            Class<?>[] parameterTypes = c.getParameterTypes();
            if (parameterTypes.length == argumentTypes.length - 1) {
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (!isSameType(parameterTypes[i], argumentTypes[i + 1])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return getClass().getName() + " {" + (isStatic ? "static " : "") + name + argumentsDescriptor + '}';
    }

    /**
     * Gets a string representation of the method associated with this plugin in the format used by
     * {@link jdk.graal.compiler.debug.DebugOptions#MethodFilter}.
     */
    public String asMethodFilterString() {
        return name + Stream.of(argumentTypes)//
                        .skip(isStatic ? 0 : 1)//
                        .map(Type::getTypeName)//
                        .collect(Collectors.joining(";", "(", ")"));
    }

    public abstract static class InlineOnlyInvocationPlugin extends InvocationPlugin {

        public InlineOnlyInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public final boolean inlineOnly() {
            return true;
        }
    }

    public abstract static class OptionalInvocationPlugin extends InvocationPlugin {

        public OptionalInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public final boolean isOptional() {
            return true;
        }
    }

    public abstract static class RequiredInvocationPlugin extends InvocationPlugin {

        public RequiredInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public final boolean canBeDisabled() {
            return false;
        }

        @Override
        public boolean isGraalOnly() {
            // We treat all required invocation plugins as Graal only. This will skip the return
            // type check in BytecodeParser.
            return true;
        }
    }

    public abstract static class RequiredInlineOnlyInvocationPlugin extends RequiredInvocationPlugin {

        public RequiredInlineOnlyInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public final boolean inlineOnly() {
            return true;
        }
    }

    public abstract static class OptionalInlineOnlyInvocationPlugin extends OptionalInvocationPlugin {

        public OptionalInlineOnlyInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public final boolean inlineOnly() {
            return true;
        }
    }

    /**
     * ConditionalInvocationPlugin enables additional checks (e.g., CPU feature detection) before
     * the plugin is applied. In a native image with runtime compilation, this plugin can help
     * identify plugins that cannot be applied during image building but may still be relevant when
     * running the image on more advanced architectures.
     *
     * Because supporting arbitrary plugins in the context of encoded graphs is complex, we disallow
     * ConditionalInvocationPlugin in snippets. See
     * {@code SymbolicSnippetEncoder$HotSpotSnippetBytecodeParser#tryInvocationPlugin}
     */
    public abstract static class ConditionalInvocationPlugin extends InvocationPlugin implements Cloneable {

        public ConditionalInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public final boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver) {
            if (!isApplicable(b.getLowerer().getTarget().arch)) {
                return false;
            }
            return super.execute(b, targetMethod, receiver, argsIncludingReceiver);
        }

        public boolean isRuntimeChecked(Architecture arch) {
            return !isApplicable(arch);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ConditionalInvocationPlugin clone() {
            try {
                ConditionalInvocationPlugin newInstance = (ConditionalInvocationPlugin) super.clone();
                newInstance.next = null;
                return newInstance;
            } catch (CloneNotSupportedException e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }

        /**
         * Determines if this plugin is applicable on the given {@code arch}.
         */
        public abstract boolean isApplicable(Architecture arch);
    }

    public abstract static class InlineOnlyConditionalInvocationPlugin extends ConditionalInvocationPlugin {

        public InlineOnlyConditionalInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public boolean inlineOnly() {
            return true;
        }
    }

    public abstract static class OptionalInlineOnlyConditionalInvocationPlugin extends InlineOnlyConditionalInvocationPlugin {

        public OptionalInlineOnlyConditionalInvocationPlugin(String name, Type... argumentTypes) {
            super(name, argumentTypes);
        }

        @Override
        public boolean isOptional() {
            return true;
        }
    }
}
