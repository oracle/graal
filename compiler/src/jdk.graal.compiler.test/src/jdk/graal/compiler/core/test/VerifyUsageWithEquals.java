/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.Objects;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.UncheckedInterfaceProvider;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * For certain types, object identity should not be used for object equality check. This phase
 * checks the correct usage of the given type. Equality checks with == or != (except null checks)
 * results in an {@link AssertionError}. Optionally, a singleton value with which == and != checks
 * are allowed as an exception may be provided.
 */
public class VerifyUsageWithEquals extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    /**
     * The type of values that must not use identity for testing object equality.
     */
    private final Class<?> restrictedClass;

    /**
     * The value besides {@code null} for which equality checks with == or != are allowed.
     */
    private final Object safeSingletonValue;

    public VerifyUsageWithEquals(Class<?> restrictedClass) {
        checkRestrictedClass(restrictedClass);
        this.restrictedClass = restrictedClass;
        this.safeSingletonValue = null;
    }

    /**
     * Constructs a verifier to check that object identity is not used for equality checks except
     * with {@code null} and the given singleton value.
     *
     * @param restrictedClass the class for which equality checks are restricted
     * @param singletonValue the non-null value for which equality checks with == or != are allowed
     *            as an exception
     */
    public <T> VerifyUsageWithEquals(Class<T> restrictedClass, T singletonValue) {
        checkRestrictedClass(restrictedClass);
        this.restrictedClass = restrictedClass;
        Objects.requireNonNull(singletonValue);
        this.safeSingletonValue = singletonValue;
    }

    private static void checkRestrictedClass(Class<?> restrictedClass) {
        assert !restrictedClass.isInterface() || isTrustedInterface(restrictedClass) : "the restricted class must not be an untrusted interface";
    }

    private static final Class<?>[] trustedInterfaceTypes = {JavaType.class, JavaField.class, JavaMethod.class};

    private static boolean isTrustedInterface(Class<?> cls) {
        for (Class<?> trusted : trustedInterfaceTypes) {
            if (trusted.isAssignableFrom(cls)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether the type of {@code node} is assignable to the {@link #restrictedClass}.
     */
    private boolean isAssignableToRestrictedType(ValueNode node, MetaAccessProvider metaAccess) {
        if (node.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
            ResolvedJavaType restrictedType = metaAccess.lookupJavaType(restrictedClass);
            JavaType nodeType = StampTool.typeOrNull(node);
            if (nodeType == null && node instanceof LoadFieldNode load) {
                nodeType = load.field().getType();
            }
            if (nodeType == null && node instanceof Invoke invoke) {
                ResolvedJavaMethod target = invoke.callTarget().targetMethod();
                nodeType = target.getSignature().getReturnType(target.getDeclaringClass());
            }
            if (nodeType == null && node instanceof UncheckedInterfaceProvider uip) {
                nodeType = StampTool.typeOrNull(uip.uncheckedStamp());
            }

            return nodeType instanceof ResolvedJavaType resolved && restrictedType.isAssignableFrom(resolved);
        }
        return false;
    }

    private boolean isSafeConstant(ValueNode node, ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection) {
        return isNullConstant(node) || isSafeSingletonValue(node, constantReflection, snippetReflection);
    }

    private static boolean isNullConstant(ValueNode node) {
        return node.isConstant() && node.isNullConstant();
    }

    private boolean isSafeSingletonValue(ValueNode node, ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection) {
        if (safeSingletonValue == null) {
            return false;
        }
        JavaConstant javaConstant = node.asJavaConstant();
        if (node instanceof LoadFieldNode loadField && loadField.isStatic() && loadField.field().isFinal()) {
            javaConstant = constantReflection.readFieldValue(loadField.field(), null);
        }
        if (javaConstant == null) {
            return false;
        }
        return safeSingletonValue == snippetReflection.asObject(Object.class, javaConstant);
    }

    private static boolean isEqualsMethod(ResolvedJavaMethod method) {
        if (method.getName().equals("equals")) {
            Signature sig = method.getSignature();
            if (sig.getReturnKind() == JavaKind.Boolean) {
                if (sig.getParameterCount(false) == 1) {
                    ResolvedJavaType ptype = (ResolvedJavaType) sig.getParameterType(0, method.getDeclaringClass());
                    return ptype.isJavaLangObject();

                }
            }
        }
        return false;
    }

    private static boolean isThisParameter(ValueNode node) {
        return node instanceof ParameterNode && ((ParameterNode) node).index() == 0;
    }

    /**
     * Checks whether the type of {@code x} or {@code y} is assignable to the restricted type and
     * that {@code x} and {@code y} are not safe constants.
     */
    private boolean isIllegalUsage(ResolvedJavaMethod method, ValueNode x, ValueNode y, CoreProviders context) {
        if ((isAssignableToRestrictedType(x, context.getMetaAccess()) || isAssignableToRestrictedType(y, context.getMetaAccess())) &&
                        !isSafeConstant(x, context.getConstantReflection(), context.getSnippetReflection()) &&
                        !isSafeConstant(y, context.getConstantReflection(), context.getSnippetReflection())) {
            return !isEqualsMethod(method) || (!isThisParameter(x) && !isThisParameter(y));
        }
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        for (ObjectEqualsNode cn : graph.getNodes().filter(ObjectEqualsNode.class)) {
            // bail out if we compare an object of type klass with == or != (except null checks)
            ResolvedJavaMethod method = graph.method();
            ResolvedJavaType restrictedType = context.getMetaAccess().lookupJavaType(restrictedClass);

            if (restrictedType.isAssignableFrom(method.getDeclaringClass())) {
                // Allow violation in methods of the restricted type itself and its subclasses.
            } else if (isIllegalUsage(method, cn.getX(), cn.getY(), context)) {
                throw new VerificationError("Verification of " + restrictedClass.getName() + " usage failed: Comparing " + cn.getX() + " and " + cn.getY() + " in " + method +
                                " must use .equals() for object equality, not '==' or '!='");
            }
        }
    }
}
