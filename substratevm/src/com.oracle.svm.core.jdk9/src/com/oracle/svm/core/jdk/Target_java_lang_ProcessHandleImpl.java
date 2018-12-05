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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;

// Checkstyle: stop

@TargetClass(className = "java.lang.ProcessHandleImpl", onlyWith = JDK9OrLater.class)
final class Target_java_lang_ProcessHandleImpl {

    @Alias//
    static long REAPER_DEFAULT_STACKSIZE;

    @Alias//
    @InjectAccessors(ProcessReaperExecutorAccessor.class)//
    static Executor processReaperExecutor;
}

class ProcessReaperExecutorAccessor {
    private static Executor executor;

    /**
     * The get accessor for {@link Target_java_lang_ProcessHandleImpl#processReaperExecutor}.
     */
    static Executor get() {
        if (executor == null) {
            /**
             * Same implementation as {@link java.lang.ProcessHandleImpl#processReaperExecutor} but
             * delayed to image runtime.
             */
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
            ThreadGroup systemThreadGroup = tg;
            long stackSize = Boolean.getBoolean("jdk.lang.processReaperUseDefaultStackSize")
                            ? 0
                            : Target_java_lang_ProcessHandleImpl.REAPER_DEFAULT_STACKSIZE;
            ThreadFactory threadFactory = grimReaper -> {
                Thread t = new Thread(systemThreadGroup, grimReaper, "process reaper",
                                stackSize, false);
                t.setDaemon(true);
                // A small attempt (probably futile) to avoid priority inversion
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            };

            executor = Executors.newCachedThreadPool(threadFactory);
        }
        return executor;
    }
}
