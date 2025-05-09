/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

@TargetClass(java.util.concurrent.ForkJoinTask.class)
final class Target_java_util_concurrent_ForkJoinTask {
    @Alias private transient volatile Target_java_util_concurrent_ForkJoinTask_Aux aux;

    @Alias volatile int status;
    @Alias static int ABNORMAL;
    @Alias static int THROWN;

    /**
     * Returns a rethrowable exception for this task, if available. The original method may attempt
     * to wrap the exception in another, reflectively constructed one. This can lead to an unhandled
     * MissingReflectionRegistrationError (GR-51083). Even if the NI tracing agent is used, the
     * reflective access might go unregistered, since it is executed unpredictably (depending on
     * {@code a.thread != Thread.currentThread()} in the context of a thread pool). This substitute
     * avoids this problem by never wrapping the exception, which is documented as correct behavior
     * by the original method.
     */
    @Substitute
    @SuppressWarnings("all")
    @SuppressFBWarnings(value = "BC_IMPOSSIBLE_INSTANCEOF", justification = "Check for @TargetClass")
    private Throwable getException(boolean asExecutionException) {
        // @formatter:off   Code copied from the original JDK method
        // Checkstyle: stop
        int s; Throwable ex; Target_java_util_concurrent_ForkJoinTask_Aux a;
        if ((s = status) >= 0 || (s & ABNORMAL) == 0)
            return null;
        else if ((s & THROWN) == 0 || (a = aux) == null || (ex = a.ex) == null) {
            ex = new CancellationException();
            if (!asExecutionException || !((Object) this instanceof Target_java_util_concurrent_ForkJoinTask_InterruptibleTask))
                return ex;
        }
        return (asExecutionException) ? new ExecutionException(ex) : ex;
        // Checkstyle: resume
        // @formatter:on
    }
}

@TargetClass(value = java.util.concurrent.ForkJoinTask.class, innerClass = "Aux")
final class Target_java_util_concurrent_ForkJoinTask_Aux {
    @Alias Throwable ex;
}

@TargetClass(value = java.util.concurrent.ForkJoinTask.class, innerClass = "InterruptibleTask")
final class Target_java_util_concurrent_ForkJoinTask_InterruptibleTask {
}
