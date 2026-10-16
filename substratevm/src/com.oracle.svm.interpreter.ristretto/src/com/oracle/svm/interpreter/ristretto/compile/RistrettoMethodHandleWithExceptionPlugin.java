/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import static jdk.graal.compiler.core.common.GraalOptions.MaximumRecursiveInlining;

import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.Invokable;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.replacements.MethodHandleWithExceptionPlugin;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Ristretto-specific method-handle parser support.
 * <p>
 * Runtime-loaded {@code MethodHandle.invoke(...)} and {@code invokedynamic} sites can link to a
 * small AOT JDK adapter such as {@code Invokers$Holder.linkToTargetMethod(args..., appendix)}.
 * The adapter body just casts the appendix to a {@code MethodHandle} and calls
 * {@code appendix.invokeBasic(args...)}. Most AOT adapter bytecodes are intentionally unavailable
 * to Ristretto, so bypass the adapter when the appendix is already a compiler-visible constant.
 */
public final class RistrettoMethodHandleWithExceptionPlugin extends MethodHandleWithExceptionPlugin {
    /** JDK holder for linked-invoker adapters. */
    private static final String INVOKERS_HOLDER = "java.lang.invoke.Invokers$Holder";
    /** Linked-invoker adapter replaced when its appendix is constant. */
    private static final String LINK_TO_TARGET_METHOD = "linkToTargetMethod";

    /** Creates a Ristretto method-handle plugin with the generic plugin's deoptimization policy. */
    public RistrettoMethodHandleWithExceptionPlugin(MethodHandleAccessProvider methodHandleAccess, boolean safeForDeoptimization) {
        super(methodHandleAccess, safeForDeoptimization);
    }

    /** Handles Ristretto-specific linked invokers before generic parsing. */
    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (tryHandleLinkedTargetMethod(b, method, args)) {
            return true;
        }
        return super.handleInvoke(b, method, args);
    }

    /**
     * Replaces a linked target-method adapter with a direct invoke when its method-handle appendix
     * is a compiler-visible constant.
     */
    private boolean tryHandleLinkedTargetMethod(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!isLinkedTargetMethod(method) || args.length == 0) {
            return false;
        }

        ValueNode appendix = args[args.length - 1];
        if (!appendix.isConstant()) {
            return false;
        }

        ResolvedJavaMethod target = methodHandleAccess.resolveInvokeBasicTarget(appendix.asJavaConstant(), false);
        if (target == null || target.equals(method)) {
            return false;
        }

        ValueNode[] replacementArgs = toInvokeBasicArguments(args);
        if (replacementArgs.length != target.getSignature().getParameterCount(!target.isStatic())) {
            return false;
        }
        if (!canForceInline(b, target)) {
            return false;
        }

        maybeCastInvokeBasicArguments(b, target, replacementArgs);
        InvokeKind invokeKind = target.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        ensureForcedInlineSucceeded(b, target, b.handleReplacedInvoke(invokeKind, target, replacementArgs, true));
        adjustReturnKind(b, method, target);
        return true;
    }

    /** Checks the prerequisites for a forced inline. */
    private static boolean canForceInline(GraphBuilderContext b, ResolvedJavaMethod target) {
        if (!target.hasBytecodes() && !b.getReplacements().hasSubstitution(target, b.getOptions())) {
            return false;
        }
        return b.recursiveInliningDepth(target) <= MaximumRecursiveInlining.getValue(b.getOptions());
    }

    /** A failed forced inline has already changed parser state and must not be compiled as a call. */
    private static void ensureForcedInlineSucceeded(GraphBuilderContext b, ResolvedJavaMethod target, Invokable replacement) {
        if (replacement instanceof Invoke invoke && invoke.asFixedNode().isAlive()) {
            throw b.bailout("Could not force-inline Ristretto method-handle target " + target.format("%H.%n(%p)"));
        }
        assert replacement == null || replacement instanceof MacroInvokable : replacement;
    }

    /** Returns whether {@code method} is the JDK linked target-method adapter. */
    private static boolean isLinkedTargetMethod(ResolvedJavaMethod method) {
        return method.isStatic() &&
                        LINK_TO_TARGET_METHOD.equals(method.getName()) &&
                        INVOKERS_HOLDER.equals(method.getDeclaringClass().toJavaName(true));
    }

    /** Moves the linked-invoker appendix into the invoke-basic receiver position. */
    private static ValueNode[] toInvokeBasicArguments(ValueNode[] linkedInvokerArgs) {
        ValueNode[] replacementArgs = new ValueNode[linkedInvokerArgs.length];
        replacementArgs[0] = linkedInvokerArgs[linkedInvokerArgs.length - 1];
        System.arraycopy(linkedInvokerArgs, 0, replacementArgs, 1, linkedInvokerArgs.length - 1);
        return replacementArgs;
    }

    /**
     * Applies the same argument adaptation used by
     * {@link jdk.graal.compiler.replacements.MethodHandlePlugin} after resolving an invoke-basic
     * target. This path performs the adaptation explicitly because it bypasses the linked-invoker
     * adapter before delegating to the generic method-handle plugin.
     */
    private static void maybeCastInvokeBasicArguments(GraphBuilderContext b, ResolvedJavaMethod target, ValueNode[] args) {
        MethodHandleNode.GraphAdder adder = new MethodHandleNode.GraphAdder(b.getGraph()) {
            @Override
            public <T extends ValueNode> T add(T node) {
                return b.add(node);
            }
        };

        if (!target.isStatic()) {
            MethodHandleNode.maybeCastArgument(adder, args, 0, target.getDeclaringClass());
        }

        Signature signature = target.getSignature();
        int receiverSkip = target.isStatic() ? 0 : 1;
        for (int index = 0; index < signature.getParameterCount(false); index++) {
            JavaType parameterType = signature.getParameterType(index, target.getDeclaringClass());
            MethodHandleNode.maybeCastArgument(adder, args, receiverSkip + index, parameterType);
        }
    }

    /** Reconciles the direct target return kind with the bypassed linked-invoker signature. */
    private static void adjustReturnKind(GraphBuilderContext b, ResolvedJavaMethod linkedInvoker, ResolvedJavaMethod target) {
        if (b.hasParseTerminated()) {
            return;
        }

        JavaKind linkedInvokerReturnKind = linkedInvoker.getSignature().getReturnKind().getStackKind();
        JavaKind targetReturnKind = target.getSignature().getReturnKind().getStackKind();
        if (linkedInvokerReturnKind != targetReturnKind) {
            b.pop(targetReturnKind);
            if (linkedInvokerReturnKind != JavaKind.Void) {
                throw b.bailout("Cannot bypass method-handle linked invoker with a different return kind");
            }
        }
    }
}
