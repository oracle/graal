/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.util.Objects;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Any method with this annotation must not have a safepoint in it.
 * <p>
 * For example, a method that allocates raw memory must not contain a safepoint at least until it
 * has installed a DynamicHub on the memory and made sure that all the object references indicated
 * by that DynamicHub are valid object references.
 * <p>
 * Methods annotated with {@link Uninterruptible} can only call
 * <ul>
 * <li>Other methods annotated with {@link Uninterruptible}.</li>
 * <li>Methods annotated with {@link Fold}.</li>
 * <li>Methods from {@link KnownIntrinsics}.</li>
 * <li>Operations on {@link WordBase}.</li>
 * </ul>
 * <p>
 * When a method overrides an uninterruptible method, it must be annotated with
 * {@link Uninterruptible} too. This ensures that virtual calls do not violate uninterruptible
 * semantics. Covariant return types are not allowed when overriding a method, i.e., the base method
 * and the override must have the exact same declared return type. Covariant return types require a
 * synthetic bridge method that is generated automatically by the Java compiler, and not all Java
 * compilers put the {@link Uninterruptible} annotation on the bridge method too (for example ECJ).
 * For consistency reasons, synthetic methods are therefore never treated as uninterruptible.
 * <p>
 * Annotated methods give a terse {@link #reason} why they are annotated. Often the reason is that
 * the method is called from some other method annotated with {@link Uninterruptible}.
 * <p>
 * Annotated methods usually say that methods they call must be annotated with
 * {@link Uninterruptible}, though they can use {@link #calleeMustBe "calleeMustBe = false"} to
 * indicate that the callee does not need to be uninterruptible. I use that to avoid having to
 * annotate the whole stack printing mechanism when I am about to exit the virtual machine.
 * <p>
 * Annotated methods can use {@link #callerMustBe "callerMustBe = true"} to indicate that their
 * caller must also be annotated with {@link Uninterruptible}. I use that, for example when a method
 * allocates uninitialized storage and returns a Pointer to it. Such a method must only be called
 * from a method annotated with {@link Uninterruptible}.
 * <p>
 * Most methods annotated with {@link Uninterruptible} can not be inlined into interruptible code,
 * because their operations might be intermingled into code that can cause safepoints. If a method
 * is so simple that it can always be inlined into interruptible code, the method can be annotated
 * with {@link #mayBeInlined "mayBeInlined = true"}. Uninterruptible methods can always be inlined
 * into other uninterruptible methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Uninterruptible {
    /**
     * If a method is only uninterruptible because it is called by other uninterruptible code, then
     * this string value must be used for the reason, together with {@code mayBeInlined = true}.
     */
    String CALLED_FROM_UNINTERRUPTIBLE_CODE = "Called from uninterruptible code.";

    /**
     * Documents the reason why the annotated code must not be interruptible.
     */
    String reason();

    /**
     * When true, the caller of the method must be annotated with {@link Uninterruptible} too,
     * because the method returns a value that is unsafe to use in an interruptible method.
     */
    boolean callerMustBe() default false;

    /**
     * When true, the callee of the method must be annotated with {@link Uninterruptible} too,
     * because if this method is uninterruptible it should not call methods that are interruptible.
     */
    boolean calleeMustBe() default true;

    /**
     * When true, this method may be inlined into <b>interruptible</b> code. During inlining, the
     * compiler will treat the inlined method as if it was not annotated with
     * {@link Uninterruptible}. For the inlined copy, this therefore voids all properties that
     * uninterruptible code normally guarantees. Therefore, this flag may only be enabled for
     * methods that don't have an inherent reason for being uninterruptible, besides being called by
     * other uninterruptible code (see {@link #CALLED_FROM_UNINTERRUPTIBLE_CODE}).
     * <p>
     * If false, the compiler may still inline this method into uninterruptible code. If inlining of
     * a specific method is undesirable in general, refer to {@link NeverInline}.
     */
    boolean mayBeInlined() default false;

    class Utils {
        private static final int SYNTHETIC = 0x00001000;

        /**
         * Defines the {@link Uninterruptible} annotation returned for C function calls with
         * NO_TRANSITION.
         */
        @Uninterruptible(reason = "@CFunction / @InvokeCFunctionPointer with Transition.NO_TRANSITION")
        @SuppressWarnings("unused")
        private static void noTransitionHolder() {
        }

        private static final Uninterruptible NO_TRANSITION = Objects.requireNonNull(getAnnotation(ReflectionUtil.lookupMethod(Utils.class, "noTransitionHolder")));

        /**
         * Returns the {@link Uninterruptible} annotation of the method. Note that there are certain
         * methods where the {@link Uninterruptible} annotation is injected due to other conditions,
         * and there are certain methods where the {@link Uninterruptible} is filtered out because
         * the method must not be uninterruptible. So always use this method and never look up the
         * annotation directly on a method.
         */
        public static Uninterruptible getAnnotation(AnnotatedElement method) {
            boolean isSynthetic;
            if (method instanceof Executable) {
                isSynthetic = (((Executable) method).getModifiers() & SYNTHETIC) != 0;
            } else if (method instanceof ResolvedJavaMethod) {
                isSynthetic = ((ResolvedJavaMethod) method).isSynthetic();
            } else {
                throw VMError.shouldNotReachHere("Unexpected method implementation class: " + method.getClass().getTypeName());
            }
            if (isSynthetic) {
                /*
                 * Java compilers differ how annotations are inherited for synthetic methods: javac
                 * annotates synthetic bridge methods for covariant return types, but ECJ does not.
                 * To get a consistent handling across Java compilers, we treat all synthetic
                 * methods as not uninterruptible.
                 */
                return null;
            }

            Uninterruptible annotation = AnnotationAccess.getAnnotation(method, Uninterruptible.class);
            if (annotation != null) {
                /* Explicit annotated method. */
                return annotation;
            }

            CFunction cFunctionAnnotation = AnnotationAccess.getAnnotation(method, CFunction.class);
            InvokeCFunctionPointer cFunctionPointerAnnotation = AnnotationAccess.getAnnotation(method, InvokeCFunctionPointer.class);
            if ((cFunctionAnnotation != null && cFunctionAnnotation.transition() == CFunction.Transition.NO_TRANSITION) ||
                            (cFunctionPointerAnnotation != null && cFunctionPointerAnnotation.transition() == CFunction.Transition.NO_TRANSITION)) {
                /*
                 * If a method transfers from Java to C without a transition, then it is implicitly
                 * treated as uninterruptible. This avoids annotating many methods with multiple
                 * annotations.
                 */
                return NO_TRANSITION;
            }

            /* No relevant annotation found, so not uninterruptible. */
            return null;
        }

        /**
         * Returns whether the method is {@link Uninterruptible}, either by explicit annotation of
         * the method or implicitly due to other annotations.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        public static boolean isUninterruptible(AnnotatedElement method) {
            return getAnnotation(method) != null;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public static boolean inliningAllowed(AnnotatedElement caller, AnnotatedElement callee) {
            boolean callerUninterruptible = isUninterruptible(caller);
            boolean calleeUninterruptible = isUninterruptible(callee);

            if (callerUninterruptible) {
                /*
                 * When a caller is uninterruptible, the callee must be too. Even when the
                 * calleeMustBe flag is set to false by the caller, inlining is not allowed: after
                 * inlining that callee would be uninterruptible too, which would e.g. mean no
                 * safepoints in loops of the callee.
                 */
                return calleeUninterruptible;
            } else {
                /*
                 * When the caller is not uninterruptible, the callee must not be either: after
                 * inlining the callee would no longer be uninterruptible. The mayBeInlined flag is
                 * specified as an explicit exception to this rule.
                 */
                if (!calleeUninterruptible) {
                    return true;
                }
                Uninterruptible calleeUninterruptibleAnnotation = AnnotationAccess.getAnnotation(callee, Uninterruptible.class);
                if (calleeUninterruptibleAnnotation != null && calleeUninterruptibleAnnotation.mayBeInlined()) {
                    return true;
                }
                return false;
            }
        }
    }
}
