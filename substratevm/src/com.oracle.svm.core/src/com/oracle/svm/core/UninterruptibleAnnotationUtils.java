/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class UninterruptibleAnnotationUtils {

    /**
     * Wraps a guest-level {@code com.oracle.svm.guest.staging.Uninterruptible} annotation.
     */
    public record UninterruptibleGuestValue(String reason, boolean callerMustBe, boolean calleeMustBe, boolean mayBeInlined) {

        static UninterruptibleGuestValue fromAnnotationValue(AnnotationValue annotationValue) {
            if (annotationValue == null) {
                return null;
            }
            return new UninterruptibleGuestValue(
                            annotationValue.getString("reason"),
                            annotationValue.getBoolean("callerMustBe"),
                            annotationValue.getBoolean("calleeMustBe"),
                            annotationValue.getBoolean("mayBeInlined"));
        }
    }

    /**
     * Wraps a guest-level {@code org.graalvm.nativeimage.c.function.CFunction} annotation.
     */
    public record CFunctionGuestValue(String value, CFunction.Transition transition) {

        public static CFunctionGuestValue fromAnnotationValue(AnnotationValue annotationValue) {
            if (annotationValue == null) {
                return null;
            }
            return new CFunctionGuestValue(
                            annotationValue.getString("value"),
                            annotationValue.getEnum(CFunction.Transition.class, "transition"));
        }
    }

    /**
     * Wraps a guest-level {@code org.graalvm.nativeimage.c.function.InvokeCFunctionPointer}
     * annotation.
     */
    public record InvokeCFunctionPointerGuestValue(CFunction.Transition transition) {

        public static InvokeCFunctionPointerGuestValue fromAnnotationValue(AnnotationValue annotationValue) {
            if (annotationValue == null) {
                return null;
            }
            return new InvokeCFunctionPointerGuestValue(
                            annotationValue.getEnum(CFunction.Transition.class, "transition"));
        }
    }

    /**
     * The {@code Uninterruptible} annotation returned for C function calls with NO_TRANSITION.
     */
    private static final AtomicReference<UninterruptibleGuestValue> NO_TRANSITION = new AtomicReference<>();

    /**
     * Returns the {@code Uninterruptible} annotation of the method. Note that there are certain
     * methods where the {@code Uninterruptible} annotation is injected due to other conditions, and
     * there are certain methods where the {@code Uninterruptible} is filtered out because the
     * method must not be uninterruptible. So always use this method and never look up the
     * annotation directly on a method.
     */
    public static UninterruptibleGuestValue getAnnotation(ResolvedJavaMethod method) {
        if (method.isSynthetic()) {
            /*
             * Java compilers differ how annotations are inherited for synthetic methods: javac
             * annotates synthetic bridge methods for covariant return types, but ECJ does not. To
             * get a consistent handling across Java compilers, we treat all synthetic methods as
             * not uninterruptible.
             */
            return null;
        }

        UninterruptibleGuestValue annotation = UninterruptibleGuestValue.fromAnnotationValue(AnnotationUtil.getDeclaredAnnotationValues(method).get(GuestAccess.elements().Uninterruptible));
        if (annotation != null) {
            /* Explicit annotated method. */
            return annotation;
        }

        CFunctionGuestValue cFunctionAnnotation = CFunctionGuestValue.fromAnnotationValue(AnnotationUtil.getDeclaredAnnotationValues(method).get(GuestAccess.elements().CFunction));
        InvokeCFunctionPointerGuestValue cFunctionPointerAnnotation = InvokeCFunctionPointerGuestValue
                        .fromAnnotationValue(AnnotationUtil.getDeclaredAnnotationValues(method).get(GuestAccess.elements().InvokeCFunctionPointer));
        if ((cFunctionAnnotation != null && cFunctionAnnotation.transition() == CFunction.Transition.NO_TRANSITION) ||
                        (cFunctionPointerAnnotation != null && cFunctionPointerAnnotation.transition() == CFunction.Transition.NO_TRANSITION)) {
            /*
             * If a method transfers from Java to C without a transition, then it is implicitly
             * treated as uninterruptible. This avoids annotating many methods with multiple
             * annotations.
             */
            if (NO_TRANSITION.get() == null) {
                NO_TRANSITION.compareAndExchange(null, new UninterruptibleGuestValue("@CFunction / @InvokeCFunctionPointer with Transition.NO_TRANSITION", false, true, false));
            }
            return NO_TRANSITION.get();
        }

        /* No relevant annotation found, so not uninterruptible. */
        return null;
    }

    /**
     * Returns whether the method is {@code Uninterruptible}, either by explicit annotation of the
     * method or implicitly due to other annotations.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isUninterruptible(ResolvedJavaMethod method) {
        return getAnnotation(method) != null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean inliningAllowed(ResolvedJavaMethod caller, ResolvedJavaMethod callee) {
        boolean callerUninterruptible = isUninterruptible(caller);
        boolean calleeUninterruptible = isUninterruptible(callee);

        if (callerUninterruptible) {
            /*
             * When a caller is uninterruptible, the callee must be too. Even when the calleeMustBe
             * flag is set to false by the caller, inlining is not allowed: after inlining that
             * callee would be uninterruptible too, which would e.g. mean no safepoints in loops of
             * the callee.
             */
            return calleeUninterruptible;
        } else {
            /*
             * When the caller is not uninterruptible, the callee must not be either: after inlining
             * the callee would no longer be uninterruptible. The mayBeInlined flag is specified as
             * an explicit exception to this rule.
             */
            if (!calleeUninterruptible) {
                return true;
            }
            UninterruptibleGuestValue calleeUninterruptibleAnnotation = UninterruptibleGuestValue
                            .fromAnnotationValue(AnnotationUtil.getDeclaredAnnotationValues(callee).get(GuestAccess.elements().Uninterruptible));
            if (calleeUninterruptibleAnnotation != null && calleeUninterruptibleAnnotation.mayBeInlined()) {
                return true;
            }
            return false;
        }
    }
}
