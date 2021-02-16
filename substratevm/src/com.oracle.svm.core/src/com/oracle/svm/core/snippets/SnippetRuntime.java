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

import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_LOCATIONS;

//Checkstyle: allow reflection

import java.lang.reflect.Method;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.util.DirectAnnotationAccess;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SnippetRuntime {
    public static final SubstrateForeignCallDescriptor UNSUPPORTED_FEATURE = findForeignCall(SnippetRuntime.class, "unsupportedFeature", true, LocationIdentity.any());

    /* Implementation of runtime calls defined in a VM-independent way by Graal. */
    private static final SubstrateForeignCallDescriptor REGISTER_FINALIZER = findForeignCall(SnippetRuntime.class, "registerFinalizer", true);

    /*
     * Graal-defined math functions where we have optimized machine code sequences: We just register
     * the original Math function as the foreign call. The backend will emit the machine code
     * sequence.
     */
    private static final SubstrateForeignCallDescriptor ARITHMETIC_SIN = findForeignJdkCall(UnaryOperation.SIN.foreignCallSignature.getName(), Math.class, "sin", true, true, true);
    private static final SubstrateForeignCallDescriptor ARITHMETIC_COS = findForeignJdkCall(UnaryOperation.COS.foreignCallSignature.getName(), Math.class, "cos", true, true, true);
    private static final SubstrateForeignCallDescriptor ARITHMETIC_TAN = findForeignJdkCall(UnaryOperation.TAN.foreignCallSignature.getName(), Math.class, "tan", true, true, true);
    private static final SubstrateForeignCallDescriptor ARITHMETIC_LOG = findForeignJdkCall(UnaryOperation.LOG.foreignCallSignature.getName(), Math.class, "log", true, true, true);
    private static final SubstrateForeignCallDescriptor ARITHMETIC_LOG10 = findForeignJdkCall(UnaryOperation.LOG10.foreignCallSignature.getName(), Math.class, "log10", true, true, true);
    private static final SubstrateForeignCallDescriptor ARITHMETIC_EXP = findForeignJdkCall(UnaryOperation.EXP.foreignCallSignature.getName(), Math.class, "exp", true, true, true);
    private static final SubstrateForeignCallDescriptor ARITHMETIC_POW = findForeignJdkCall(BinaryOperation.POW.foreignCallSignature.getName(), Math.class, "pow", true, true, true);

    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{UNSUPPORTED_FEATURE, REGISTER_FINALIZER, ARITHMETIC_SIN, ARITHMETIC_COS, ARITHMETIC_TAN,
                    ARITHMETIC_LOG, ARITHMETIC_LOG10, ARITHMETIC_EXP, ARITHMETIC_POW};

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        SubstrateAllocationSnippets.registerForeignCalls(providers, foreignCalls);
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    public static SubstrateForeignCallDescriptor findForeignCall(Class<?> declaringClass, String methodName, boolean isReexecutable, LocationIdentity... additionalKilledLocations) {
        return findForeignCall(methodName, declaringClass, methodName, isReexecutable, additionalKilledLocations);
    }

    private static SubstrateForeignCallDescriptor findForeignCall(String descriptorName, Class<?> declaringClass, String methodName, boolean isReexecutable,
                    LocationIdentity... additionalKilledLocations) {
        Method method = findMethod(declaringClass, methodName);
        SubstrateForeignCallTarget foreignCallTargetAnnotation = DirectAnnotationAccess.getAnnotation(method, SubstrateForeignCallTarget.class);
        VMError.guarantee(foreignCallTargetAnnotation != null, "Add missing @SubstrateForeignCallTarget to " + declaringClass.getName() + "." + methodName);

        boolean isUninterruptible = DirectAnnotationAccess.isAnnotationPresent(method, Uninterruptible.class);
        boolean isFullyUninterruptible = foreignCallTargetAnnotation.fullyUninterruptible();
        return findForeignCall(descriptorName, method, isReexecutable, isUninterruptible, isFullyUninterruptible, additionalKilledLocations);
    }

    private static SubstrateForeignCallDescriptor findForeignJdkCall(String descriptorName, Class<?> declaringClass, String methodName, boolean isReexecutable, boolean isUninterruptible,
                    boolean isFullyUninterruptible, LocationIdentity... additionalKilledLocations) {
        Method method = findMethod(declaringClass, methodName);
        SubstrateForeignCallTarget foreignCallTargetAnnotation = DirectAnnotationAccess.getAnnotation(method, SubstrateForeignCallTarget.class);
        VMError.guarantee(foreignCallTargetAnnotation == null, declaringClass.getName() + "." + methodName + " must not be annotated with @SubstrateForeignCallTarget.");

        return findForeignCall(descriptorName, method, isReexecutable, isUninterruptible, isFullyUninterruptible, additionalKilledLocations);
    }

    private static SubstrateForeignCallDescriptor findForeignCall(String descriptorName, Method method, boolean isReexecutable, boolean isUninterruptible, boolean isFullyUninterruptible,
                    LocationIdentity... additionalKilledLocations) {
        /*
         * The safepoint slowpath needs to kill the TLAB locations (see note in Safepoint.java). We
         * therefore assume that the TLAB locations must be killed by every foreign call that is not
         * fully uninterruptible.
         */
        LocationIdentity[] killedLocations;
        if (isFullyUninterruptible) {
            VMError.guarantee(isUninterruptible, method.toString() + " is fully uninterruptible but not annotated with @Uninterruptible.");
            killedLocations = additionalKilledLocations;
        } else if (additionalKilledLocations.length == 0 || additionalKilledLocations == TLAB_LOCATIONS) {
            killedLocations = TLAB_LOCATIONS;
        } else if (containsAny(additionalKilledLocations)) {
            killedLocations = additionalKilledLocations;
        } else {
            killedLocations = new LocationIdentity[TLAB_LOCATIONS.length + additionalKilledLocations.length];
            System.arraycopy(TLAB_LOCATIONS, 0, killedLocations, 0, TLAB_LOCATIONS.length);
            System.arraycopy(additionalKilledLocations, 0, killedLocations, TLAB_LOCATIONS.length, additionalKilledLocations.length);
        }

        boolean needsDebugInfo = !isFullyUninterruptible;
        boolean isGuaranteedSafepoint = !isUninterruptible;
        return new SubstrateForeignCallDescriptor(descriptorName, method, isReexecutable, killedLocations, needsDebugInfo, isGuaranteedSafepoint);
    }

    private static Method findMethod(Class<?> declaringClass, String methodName) {
        Method foundMethod = null;
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                assert foundMethod == null : "found more than one method " + declaringClass.getName() + "." + methodName;
                foundMethod = method;
            }
        }
        assert foundMethod != null : "did not find method " + declaringClass.getName() + "." + methodName;
        return foundMethod;
    }

    private static boolean containsAny(LocationIdentity[] locations) {
        for (LocationIdentity location : locations) {
            if (location.isAny()) {
                return true;
            }
        }
        return false;
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
