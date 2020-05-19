/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.snippets;

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.util.DirectAnnotationAccess;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SnippetRuntime {

    public static final SubstrateForeignCallDescriptor UNSUPPORTED_FEATURE = findForeignCall(SnippetRuntime.class, "unsupportedFeature", true, LocationIdentity.any());

    /* Implementation of runtime calls defined in a VM-independent way by Graal. */
    public static final SubstrateForeignCallDescriptor REGISTER_FINALIZER = findForeignCall(SnippetRuntime.class, "registerFinalizer", true);

    /*
     * Graal-defined math functions where we have optimized machine code sequences: We just register
     * the original Math function as the foreign call. The backend will emit the machine code
     * sequence.
     */
    public static final SubstrateForeignCallDescriptor ARITHMETIC_SIN = findForeignCall(UnaryOperation.SIN.foreignCallSignature.getName(), Math.class, "sin", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_COS = findForeignCall(UnaryOperation.COS.foreignCallSignature.getName(), Math.class, "cos", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_TAN = findForeignCall(UnaryOperation.TAN.foreignCallSignature.getName(), Math.class, "tan", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_LOG = findForeignCall(UnaryOperation.LOG.foreignCallSignature.getName(), Math.class, "log", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_LOG10 = findForeignCall(UnaryOperation.LOG10.foreignCallSignature.getName(), Math.class, "log10", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_EXP = findForeignCall(UnaryOperation.EXP.foreignCallSignature.getName(), Math.class, "exp", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_POW = findForeignCall(BinaryOperation.POW.foreignCallSignature.getName(), Math.class, "pow", true);

    /*
     * These methods are intrinsified as nodes at first, but can then lowered back to a call. Ensure
     * they are seen as reachable.
     */
    public static final SubstrateForeignCallDescriptor OBJECT_CLONE = findForeignCall(Object.class, "clone", false, LocationIdentity.any());

    public static List<SubstrateForeignCallDescriptor> getRuntimeCalls() {
        List<SubstrateForeignCallDescriptor> result = new ArrayList<>();
        try {
            for (Field field : SnippetRuntime.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == SubstrateForeignCallDescriptor.class) {
                    result.add(((SubstrateForeignCallDescriptor) field.get(null)));
                }
            }
        } catch (IllegalAccessException ex) {
            throw new Error(ex);
        }
        return result;
    }

    public static SubstrateForeignCallDescriptor findForeignCall(Class<?> declaringClass, String methodName, boolean isReexecutable, LocationIdentity... killedLocations) {
        return findForeignCall(methodName, declaringClass, methodName, isReexecutable, killedLocations);
    }

    public static SubstrateForeignCallDescriptor findForeignCall(Class<?> declaringClass, String methodName, boolean isReexecutable, boolean needsDebugInfo, LocationIdentity... killedLocations) {
        return findForeignCall(methodName, declaringClass, methodName, isReexecutable, needsDebugInfo, killedLocations);
    }

    private static SubstrateForeignCallDescriptor findForeignCall(String descriptorName, Class<?> declaringClass, String methodName, boolean isReexecutable, LocationIdentity... killedLocations) {
        return findForeignCall(descriptorName, declaringClass, methodName, isReexecutable, true, killedLocations);
    }

    private static SubstrateForeignCallDescriptor findForeignCall(String descriptorName, Class<?> declaringClass, String methodName, boolean isReexecutable, boolean needsDebugInfo,
                    LocationIdentity... killedLocations) {
        Method foundMethod = null;
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                assert foundMethod == null : "found more than one method " + declaringClass.getName() + "." + methodName;
                foundMethod = method;
            }
        }
        assert foundMethod != null : "did not find method " + declaringClass.getName() + "." + methodName;

        /*
         * We cannot annotate methods from the JDK, but all other foreign call targets we want to be
         * annotated for documentation, and to avoid stripping.
         */
        VMError.guarantee(declaringClass.getName().startsWith("java.lang") || DirectAnnotationAccess.isAnnotationPresent(foundMethod, SubstrateForeignCallTarget.class),
                        "Add missing @SubstrateForeignCallTarget to " + declaringClass.getName() + "." + methodName);

        boolean isGuaranteedSafepoint = needsDebugInfo && !DirectAnnotationAccess.isAnnotationPresent(foundMethod, Uninterruptible.class);
        return new SubstrateForeignCallDescriptor(descriptorName, foundMethod, isReexecutable, killedLocations, needsDebugInfo, isGuaranteedSafepoint);
    }

    public static class SubstrateForeignCallDescriptor extends ForeignCallDescriptor {

        private final Class<?> declaringClass;
        private final String methodName;

        SubstrateForeignCallDescriptor(String descriptorName, Method method, boolean isReexecutable, LocationIdentity[] killedLocations, boolean needsDebugInfo, boolean isGuaranteedSafepoint) {
            super(descriptorName, method.getReturnType(), method.getParameterTypes(), isReexecutable, killedLocations, needsDebugInfo, isGuaranteedSafepoint);
            this.declaringClass = method.getDeclaringClass();
            this.methodName = method.getName();
        }

        public Class<?> getDeclaringClass() {
            return declaringClass;
        }

        public ResolvedJavaMethod findMethod(MetaAccessProvider metaAccess) {
            for (Method method : declaringClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return metaAccess.lookupJavaMethod(method);
                }
            }
            throw VMError.shouldNotReachHere("method " + methodName + " not found");
        }

        public boolean needsDebugInfo() {
            return canDeoptimize();
        }
    }

    /** Foreign call: {@link #UNSUPPORTED_FEATURE}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void unsupportedFeature(String msg) {
        throw VMError.unsupportedFeature(msg);
    }

    /** Foreign call: {@link #REGISTER_FINALIZER}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void registerFinalizer(@SuppressWarnings("unused") Object obj) {
        // We do not support finalizers, so nothing to do.
    }
}
