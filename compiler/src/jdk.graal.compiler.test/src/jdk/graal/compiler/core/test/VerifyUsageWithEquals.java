/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * results in an {@link AssertionError}.
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

    public VerifyUsageWithEquals(Class<?> restrictedClass) {
        this.restrictedClass = restrictedClass;
        assert !restrictedClass.isInterface() || isTrustedInterface(restrictedClass);
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
            ResolvedJavaType nodeType = StampTool.typeOrNull(node);
            if (nodeType == null && node instanceof LoadFieldNode) {
                nodeType = (ResolvedJavaType) ((LoadFieldNode) node).field().getType();
            }
            if (nodeType == null && node instanceof Invoke) {
                ResolvedJavaMethod target = ((Invoke) node).callTarget().targetMethod();
                nodeType = (ResolvedJavaType) target.getSignature().getReturnType(target.getDeclaringClass());
            }
            if (nodeType == null && node instanceof UncheckedInterfaceProvider) {
                nodeType = StampTool.typeOrNull(((UncheckedInterfaceProvider) node).uncheckedStamp());
            }

            if (nodeType != null && restrictedType.isAssignableFrom(nodeType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullConstant(ValueNode node) {
        return node.isConstant() && node.isNullConstant();
    }

    private static boolean isEqualsMethod(ResolvedJavaMethod method) {
        if (method.getName().equals("equals")) {
            Signature sig = method.getSignature();
            if (sig.getReturnKind() == JavaKind.Boolean) {
                if (sig.getParameterCount(false) == 1) {
                    ResolvedJavaType ptype = (ResolvedJavaType) sig.getParameterType(0, method.getDeclaringClass());
                    if (ptype.isJavaLangObject()) {
                        return true;
                    }

                }
            }
        }
        return false;
    }

    private static boolean isThisParameter(ValueNode node) {
        return node instanceof ParameterNode && ((ParameterNode) node).index() == 0;
    }

    /**
     * Checks whether the type of {@code x} is assignable to the restricted type and that {@code y}
     * is not a null constant.
     */
    private boolean isIllegalUsage(ResolvedJavaMethod method, ValueNode x, ValueNode y, MetaAccessProvider metaAccess) {
        if (isAssignableToRestrictedType(x, metaAccess) && !isNullConstant(y)) {
            if (isEqualsMethod(method) && isThisParameter(x) || isThisParameter(y)) {
                return false;
            }
            return true;
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
            } else if (isIllegalUsage(method, cn.getX(), cn.getY(), context.getMetaAccess()) || isIllegalUsage(method, cn.getY(), cn.getX(), context.getMetaAccess())) {
                throw new VerificationError("Verification of " + restrictedClass.getName() + " usage failed: Comparing " + cn.getX() + " and " + cn.getY() + " in " + method +
                                " must use .equals() for object equality, not '==' or '!='");
            }
        }
    }
}
