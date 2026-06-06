/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.graalvm.nativeimage.MissingReflectionRegistrationError;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.SubstrateUtil;

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
     * avoids missing reflection registration errors while still preserving the original
     * cross-thread wrapping behavior when a suitable public constructor is available.
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
        } else if (a.thread != Thread.currentThread()) {
            Constructor<?> oneArgCtor = null;
            /*
             * MissingReflectionRegistrationError is a LinkageError. Avoid catching that concrete
             * type in substituted bytecode: the catch type would be preserved in the substituted
             * method's exception table and can fail universe building. Catch LinkageError and
             * filter it explicitly instead.
             */
            try {
                oneArgCtor = ex.getClass().getConstructor(Throwable.class);
            } catch (Exception ignore) {
            } catch (LinkageError e) {
                if (!(e instanceof MissingReflectionRegistrationError)) {
                    throw e;
                }
            }
            if (oneArgCtor != null) {
                try {
                    ex = SubstrateUtil.cast(oneArgCtor.newInstance(ex), Throwable.class);
                } catch (Exception ignore) {
                } catch (LinkageError e) {
                    if (!(e instanceof MissingReflectionRegistrationError)) {
                        throw e;
                    }
                }
            } else {
                Constructor<?> noArgCtor = null;
                try {
                    noArgCtor = ex.getClass().getConstructor();
                } catch (Exception ignore) {
                } catch (LinkageError e) {
                    if (!(e instanceof MissingReflectionRegistrationError)) {
                        throw e;
                    }
                }
                if (noArgCtor != null) {
                    try {
                        Throwable rx = SubstrateUtil.cast(noArgCtor.newInstance(), Throwable.class);
                        rx.initCause(ex);
                        ex = rx;
                    } catch (Exception ignore) {
                    } catch (LinkageError e) {
                        if (!(e instanceof MissingReflectionRegistrationError)) {
                            throw e;
                        }
                    }
                }
            }
        }
        return (asExecutionException) ? new ExecutionException(ex) : ex;
        // Checkstyle: resume
        // @formatter:on
    }
}

@TargetClass(value = java.util.concurrent.ForkJoinTask.class, innerClass = "Aux")
final class Target_java_util_concurrent_ForkJoinTask_Aux {
    @Alias Thread thread;
    @Alias Throwable ex;
}

@TargetClass(value = java.util.concurrent.ForkJoinTask.class, innerClass = "InterruptibleTask")
final class Target_java_util_concurrent_ForkJoinTask_InterruptibleTask {
}
