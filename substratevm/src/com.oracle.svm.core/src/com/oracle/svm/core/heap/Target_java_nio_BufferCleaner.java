/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.ReferenceQueue;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.thread.VMThreads;

@TargetClass(className = "java.nio.BufferCleaner")
public final class Target_java_nio_BufferCleaner {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "java.nio.BufferCleaner$CleanerList")//
    static Target_java_nio_BufferCleaner_CleanerList cleanerList;
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = true) //
    static ReferenceQueue<Object> queue;
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static Thread cleaningThread;
}

@TargetClass(className = "java.nio.BufferCleaner", innerClass = "CleaningRunnable")
final class Target_java_nio_BufferCleaner_CleaningRunnable {
    @SuppressWarnings("static-method")
    @Substitute
    public void run() {
        while (true) {
            try {
                Target_sun_nio_Cleaner c = SubstrateUtil.cast(Target_java_nio_BufferCleaner.queue.remove(), Target_sun_nio_Cleaner.class);
                c.clean();
            } catch (InterruptedException e) {
                if (VMThreads.isTearingDown()) {
                    return;
                }
                // Ignore InterruptedException in cleaner thread.
            }
        }
    }
}

@TargetClass(className = "java.nio.BufferCleaner", innerClass = "CleanerList")
final class Target_java_nio_BufferCleaner_CleanerList {
}
