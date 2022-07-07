/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK19OrLater;
import com.oracle.svm.core.jdk.LoomJDK;
import com.oracle.svm.core.util.VMError;

/**
 * `VirtualThread` is implemented in Java, so we do not need to modify this class very much.
 */
@TargetClass(className = "java.lang.VirtualThread", onlyWith = JDK19OrLater.class)
public final class Target_java_lang_VirtualThread {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    @TargetElement(onlyWith = LoomJDK.class)//
    private static boolean notifyJvmtiEvents;

    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    private static void registerNatives() {
    }

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    public static native Target_java_lang_ContinuationScope continuationScope();

    @Alias//
    @TargetElement(onlyWith = LoomJDK.class)//
    Target_java_lang_Continuation cont;

    @Alias//
    @TargetElement(onlyWith = LoomJDK.class)//
    Thread carrierThread;

    @Substitute//
    @TargetElement(onlyWith = LoomJDK.class)
    StackTraceElement[] asyncGetStackTrace() {
        if (carrierThread != null) {
            return carrierThread.getStackTrace();
        }
        return tryGetStackTrace();
    }

    @Alias//
    @TargetElement(onlyWith = LoomJDK.class)
    private native StackTraceElement[] tryGetStackTrace();

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    native boolean joinNanos(long nanos) throws InterruptedException;

    @Substitute
    @TargetElement(name = "continuationScope", onlyWith = JDK19OrLater.class)
    static Object continuationScopeJDK19() {
        throw VMError.unimplemented("JDK 19 continuations not yet supported");
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    @SuppressWarnings("static-method")
    private boolean yieldContinuation() {
        throw VMError.unimplemented("JDK 19 continuations not yet supported");
    }
}
