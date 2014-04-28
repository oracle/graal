/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.java;

import static com.oracle.graal.api.meta.MetaUtil.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;

/**
 * Verifies that a class declaring one or more {@linkplain OptionValue options} has a class
 * initializer that only initializes the option(s). This sanity check mitigates the possibility of
 * an option value being used before the code that sets the value (e.g., from the command line) has
 * been executed.
 */
public class VerifyOptionsPhase extends Phase {

    public static boolean checkOptions(MetaAccessProvider metaAccess) {
        ServiceLoader<Options> sl = ServiceLoader.loadInstalled(Options.class);
        Set<ResolvedJavaType> checked = new HashSet<>();
        for (Options opts : sl) {
            for (OptionDescriptor desc : opts) {
                ResolvedJavaType holder = metaAccess.lookupJavaType(desc.getDeclaringClass());
                checkType(holder, desc, metaAccess, checked);
            }
        }
        return true;
    }

    private static void checkType(ResolvedJavaType type, OptionDescriptor option, MetaAccessProvider metaAccess, Set<ResolvedJavaType> checked) {
        if (!checked.contains(type)) {
            checked.add(type);
            ResolvedJavaType superType = type.getSuperclass();
            if (superType != null && !MetaUtil.isJavaLangObject(superType)) {
                checkType(superType, option, metaAccess, checked);
            }
            ResolvedJavaMethod clinit = type.getClassInitializer();
            if (clinit != null) {
                StructuredGraph graph = new StructuredGraph(clinit);
                new GraphBuilderPhase.Instance(metaAccess, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
                new VerifyOptionsPhase(type, metaAccess, option).apply(graph);
            }
        }
    }

    private final MetaAccessProvider metaAccess;
    private final ResolvedJavaType declaringClass;
    private final ResolvedJavaType optionValueType;
    private final Set<ResolvedJavaType> boxingTypes;
    private final OptionDescriptor option;

    public VerifyOptionsPhase(ResolvedJavaType declaringClass, MetaAccessProvider metaAccess, OptionDescriptor option) {
        this.metaAccess = metaAccess;
        this.declaringClass = declaringClass;
        this.optionValueType = metaAccess.lookupJavaType(OptionValue.class);
        this.option = option;
        this.boxingTypes = new HashSet<>();
        for (Class<?> c : new Class[]{Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Float.class, Long.class, Double.class}) {
            this.boxingTypes.add(metaAccess.lookupJavaType(c));
        }
    }

    /**
     * Checks whether a given method is allowed to be called.
     */
    private boolean checkInvokeTarget(ResolvedJavaMethod method) {
        ResolvedJavaType holder = method.getDeclaringClass();
        if (method.isConstructor()) {
            if (optionValueType.isAssignableFrom(holder)) {
                return true;
            }
        } else if (boxingTypes.contains(holder)) {
            return method.getName().equals("valueOf");
        } else if (method.getDeclaringClass().equals(metaAccess.lookupJavaType(Class.class))) {
            return method.getName().equals("desiredAssertionStatus");
        } else if (method.getDeclaringClass().equals(declaringClass)) {
            return (method.getName().equals("$jacocoInit"));
        }
        return false;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (ValueNode node : graph.getNodes().filter(ValueNode.class)) {
            if (node instanceof StoreFieldNode) {
                ResolvedJavaField field = ((StoreFieldNode) node).field();
                verify(field.getDeclaringClass().equals(declaringClass), node, "store to field " + format("%H.%n", field));
                verify(field.isStatic(), node, "store to field " + format("%H.%n", field));
                if (optionValueType.isAssignableFrom((ResolvedJavaType) field.getType())) {
                    verify(field.isFinal(), node, "option field " + format("%H.%n", field) + " not final");
                } else {
                    verify((field.isSynthetic()), node, "store to non-synthetic field " + format("%H.%n", field));
                }
            } else if (node instanceof Invoke) {
                Invoke invoke = (Invoke) node;
                MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
                ResolvedJavaMethod targetMethod = callTarget.targetMethod();
                verify(checkInvokeTarget(targetMethod), node, "invocation of " + format("%H.%n(%p)", targetMethod));
            }
        }
    }

    private void verify(boolean condition, Node node, String message) {
        if (!condition) {
            error(node, message);
        }
    }

    private void error(Node node, String message) {
        String loc = GraphUtil.approxSourceLocation(node);
        throw new GraalInternalError(String.format("The " + option.getName() + " option is declared in " + option.getDeclaringClass() +
                        " whose class hierarchy contains a class initializer (in %s) with a code pattern at or near %s implying an action other than initialization of an option:%n%n    %s%n%n" +
                        "The recommended solution is to move " + option.getName() + " into a separate class (e.g., " + option.getDeclaringClass().getName() + ".Options).%n",
                        toJavaName(declaringClass), loc, message));
    }
}
