/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.SuppressFBWarnings;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.thread.ThreadingSupportImpl.PauseRecurringCallback;

/** Access to methods in support of {@link sun.misc}. */
public class SunMiscSupport {

    @SuppressFBWarnings(value = "BC", justification = "Target_jdk_internal_ref_Cleaner is an alias for a class that extends Reference")
    @SuppressWarnings("try")
    public static void drainCleanerQueue() {
        // An exception in a recurring callback could interrupt Cleaner processing and leak memory
        Target_java_lang_ref_ReferenceQueue queue = SubstrateUtil.cast(Target_jdk_internal_ref_Cleaner.dummyQueue, Target_java_lang_ref_ReferenceQueue.class);
        if (!queue.isEmpty()) {
            try (PauseRecurringCallback prc = new PauseRecurringCallback()) {
                for (; /* return */ ;) {
                    final Object entry = queue.poll();
                    if (entry == null) {
                        return;
                    }
                    if (entry instanceof Target_jdk_internal_ref_Cleaner) {
                        final Target_jdk_internal_ref_Cleaner cleaner = (Target_jdk_internal_ref_Cleaner) entry;
                        cleaner.clean();
                    }
                }
            }
        }
    }
}
