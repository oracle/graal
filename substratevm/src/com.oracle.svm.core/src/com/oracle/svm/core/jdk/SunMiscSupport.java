/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.util.VMError;

public class SunMiscSupport {
    public static void drainCleanerQueue() {
        Target_java_lang_ref_ReferenceQueue cleanerQueue = SubstrateUtil.cast(Target_jdk_internal_ref_Cleaner.dummyQueue, Target_java_lang_ref_ReferenceQueue.class);
        processQueue(cleanerQueue);

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Target_java_lang_ref_ReferenceQueue cleanableQueue = SubstrateUtil.cast(Target_jdk_internal_ref_CleanerFactory.cleaner().impl.queue, Target_java_lang_ref_ReferenceQueue.class);
            processQueue(cleanableQueue);
        }
    }

    private static void processQueue(Target_java_lang_ref_ReferenceQueue queue) {
        if (!queue.isEmpty()) {
            ThreadingSupportImpl.pauseRecurringCallback("An exception in a recurring callback must not interrupt the cleaner processing as this would result in a memory leak.");
            try {
                for (; /* return */ ;) {
                    Object entry = queue.poll();
                    if (entry == null) {
                        return;
                    }

                    if (entry instanceof Target_jdk_internal_ref_Cleaner) {
                        Target_jdk_internal_ref_Cleaner cleaner = (Target_jdk_internal_ref_Cleaner) entry;
                        cleaner.clean();
                    } else if (JavaVersionUtil.JAVA_SPEC > 8 && entry instanceof Target_java_lang_ref_Cleaner_Cleanable) {
                        Target_java_lang_ref_Cleaner_Cleanable cleaner = (Target_java_lang_ref_Cleaner_Cleanable) entry;
                        cleaner.clean();
                    } else {
                        VMError.shouldNotReachHere("Unexpected type: " + entry.getClass().getName());
                    }
                }
            } finally {
                ThreadingSupportImpl.resumeRecurringCallback();
            }
        }
    }
}
