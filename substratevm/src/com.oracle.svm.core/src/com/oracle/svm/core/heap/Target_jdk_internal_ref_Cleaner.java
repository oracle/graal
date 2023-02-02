/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.lang.ref.Cleaner;
import java.lang.ref.ReferenceQueue;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.thread.VMThreads;

import jdk.internal.misc.InnocuousThread;

@TargetClass(className = "jdk.internal.ref.Cleaner")
public final class Target_jdk_internal_ref_Cleaner {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    static Target_jdk_internal_ref_Cleaner first;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    static ReferenceQueue<Object> dummyQueue = new ReferenceQueue<>();

    @Alias
    native void clean();
}

/**
 * On JDK11+, the cleaner infrastructure is quite different from JDK8:
 * <ul>
 * <li>java.lang.ref.Cleaner: starts a new thread to process its reference queue.</li>
 * <li>jdk.internal.ref.CleanerFactory: provides a common cleaner with a shared cleaner thread. In
 * native-image, we do not necessarily spawn a separate thread for processing references, but may
 * drain the queue after garbage collection.</li>
 * <li>jdk.internal.ref.Cleaner: this only seems to be used by DirectByteBuffer but at least the
 * handling is the same as on JDK 8.
 * </ul>
 */
@TargetClass(className = "jdk.internal.ref.CleanerFactory")
final class Target_jdk_internal_ref_CleanerFactory {
    @Alias
    public static native Target_java_lang_ref_Cleaner cleaner();
}

@TargetClass(className = "java.lang.ref.Cleaner")
final class Target_java_lang_ref_Cleaner {
    @Alias//
    public Target_jdk_internal_ref_CleanerImpl impl;
}

@TargetClass(className = "java.lang.ref.Cleaner$Cleanable")
final class Target_java_lang_ref_Cleaner_Cleanable {
    @Alias
    native void clean();
}

@TargetClass(className = "jdk.internal.ref.CleanerImpl")
final class Target_jdk_internal_ref_CleanerImpl {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$PhantomCleanableRef")//
    Target_jdk_internal_ref_PhantomCleanable phantomCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$WeakCleanableRef")//
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
    Target_jdk_internal_ref_WeakCleanable weakCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$SoftCleanableRef")//
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
    Target_jdk_internal_ref_SoftCleanable softCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "java.lang.ref.ReferenceQueue")//
    public ReferenceQueue<Object> queue;

    /** @see #run() */
    @Substitute
    @TargetElement(name = "run", onlyWith = JDK11OrEarlier.class)
    public void runJDK11() {
        Thread t = Thread.currentThread();
        InnocuousThread mlThread = (t instanceof InnocuousThread) ? (InnocuousThread) t : null;
        while (!phantomCleanableList.isListEmpty() || !weakCleanableList.isListEmpty() || !softCleanableList.isListEmpty()) {
            if (mlThread != null) {
                mlThread.eraseThreadLocals();
            }
            try {
                Cleaner.Cleanable ref = (Cleaner.Cleanable) queue.remove(60 * 1000L);
                if (ref != null) {
                    ref.clean();
                }
            } catch (Throwable e) {
                if (VMThreads.isTearingDown()) {
                    return;
                }
            }
        }
    }

    /**
     * This loop executes in a daemon thread and waits until there are no more cleanables (including
     * the {@code Cleaner} itself), ignoring {@link InterruptedException}. This blocks VM tear-down,
     * so we add a check if the VM is tearing down here.
     */
    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public void run() {
        Thread t = Thread.currentThread();
        InnocuousThread mlThread = (t instanceof InnocuousThread) ? (InnocuousThread) t : null;
        while (!phantomCleanableList.isListEmpty()) {
            if (mlThread != null) {
                mlThread.eraseThreadLocals();
            }
            try {
                Cleaner.Cleanable ref = (Cleaner.Cleanable) queue.remove(60 * 1000L);
                if (ref != null) {
                    ref.clean();
                }
            } catch (Throwable e) {
                if (VMThreads.isTearingDown()) {
                    return;
                }
            }
        }
    }
}

@TargetClass(className = "jdk.internal.ref.PhantomCleanable")
final class Target_jdk_internal_ref_PhantomCleanable {
    @Alias
    native boolean isListEmpty();
}

// Removed by JDK-8251861
@TargetClass(className = "jdk.internal.ref.WeakCleanable", onlyWith = JDK11OrEarlier.class)
final class Target_jdk_internal_ref_WeakCleanable {
    @Alias
    native boolean isListEmpty();
}

// Removed by JDK-8251861
@TargetClass(className = "jdk.internal.ref.SoftCleanable", onlyWith = JDK11OrEarlier.class)
final class Target_jdk_internal_ref_SoftCleanable {
    @Alias
    native boolean isListEmpty();
}
