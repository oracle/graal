/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import java.util.concurrent.Executor;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "java.lang.ProcessHandleImpl", onlyWith = JDK11OrLater.class)
final class Target_java_lang_ProcessHandleImpl {

    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})//
    @Delete static Executor processReaperExecutor;

    @Substitute
    private static long isAlive0(long pid) {
        return ProcessProperties.isAlive(pid) ? 0 : -1;
    }

    @Substitute
    private static int waitForProcessExit0(long pid, boolean reapvalue) {
        return Java_lang_Process_Supplement.waitForProcessExit0(pid, reapvalue);
    }

    @Substitute
    private static long getCurrentPid0() {
        return ProcessProperties.getProcessID();
    }

    @Substitute
    private static boolean destroy0(long pid, long startTime, boolean forcibly) {
        VMError.guarantee(startTime == 0, "startTime != 0 currently not supported");
        if (forcibly) {
            return ProcessProperties.destroyForcibly(pid);
        } else {
            return ProcessProperties.destroy(pid);
        }
    }

    @Substitute
    private static void initNative() {
        /* Currently unused */
    }
}
