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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.util.DirectAnnotationAccess;
import org.graalvm.util.GuardedAnnotationAccess;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;

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
 * Annotated methods give a terse {@link #reason} why they are annotated. Often the reason is that
 * the method is called from some other method annotated with {@link Uninterruptible}.
 * <p>
 * Annotated methods usually say that methods they call must be annotated with
 * {@link Uninterruptible}, though they can use {@link #calleeMustBe "calleeMustBe = false"} to
 * indicate that the callee need not be so annotated. I use that to avoid having to annotate the
 * whole stack printing mechanism when I am about to exit the virtual machine.
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
 * <dl>
 * Some alternatives to annotation:
 * <dt>Code called from snippets</dt>
 * <dd>Snippet code is always inlined and runs to completion. Methods called only from snippets need
 * not be annotated.</dd>
 * <dt>Code called from VMOperations</dt>
 * <dd>VMOperation code runs single-threaded to completion. Public entry points that should only run
 * in VMOperations can be guarded with a call to
 * {@linkplain VMOperation#guaranteeInProgress(String)}.</dd>
 * </dl>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Uninterruptible {

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
     * When true, this method may be <b>inlined into interruptible code</b>. When false (the
     * default), this method may only be inlined into other uninterruptible code. If inlining of a
     * specific method is undesirable in general, refer to {@link NeverInline}.
     * <p>
     * The concern is that if an uninterruptible method is inlined, interruptible code such as
     * allocation could be hoisted between the operations of the uninterruptible code. Simple
     * uninterruptible methods like field accesses can be annotated to allow them to be inlined.
     */
    boolean mayBeInlined() default false;

    class Utils {
        /**
         * Returns whether the method is {@link Uninterruptible}, either by explicit annotation of
         * the method or implicitly due to other annotations.
         */
        public static boolean isUninterruptible(AnnotatedElement method) {
            if (DirectAnnotationAccess.isAnnotationPresent(method, Uninterruptible.class)) {
                /* Explicit annotated method, so definitely uninterruptible. */
                return true;
            }

            CFunction cFunctionAnnotation = DirectAnnotationAccess.getAnnotation(method, CFunction.class);
            InvokeCFunctionPointer cFunctionPointerAnnotation = DirectAnnotationAccess.getAnnotation(method, InvokeCFunctionPointer.class);
            if ((cFunctionAnnotation != null && cFunctionAnnotation.transition() == CFunction.Transition.NO_TRANSITION) ||
                            (cFunctionPointerAnnotation != null && cFunctionPointerAnnotation.transition() == CFunction.Transition.NO_TRANSITION)) {
                /*
                 * If a method transfers from Java to C without a transition, then it is implicitly
                 * treated as uninterruptible. This avoids annotating many methods with multiple
                 * annotations.
                 */
                return true;
            }

            return false;
        }

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
                Uninterruptible calleeUninterruptibleAnnotation = GuardedAnnotationAccess.getAnnotation(callee, Uninterruptible.class);
                if (calleeUninterruptibleAnnotation != null && calleeUninterruptibleAnnotation.mayBeInlined()) {
                    return true;
                }
                return false;
            }
        }
    }
}
