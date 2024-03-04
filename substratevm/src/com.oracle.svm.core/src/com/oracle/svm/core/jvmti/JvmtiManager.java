/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti;

import org.graalvm.nativeimage.ImageSingletons;

public final class JvmtiManager {
    // TODO @dprcci refactor
    public static void registerAllJvmtiClasses() {
        ImageSingletons.add(JvmtiAgents.class, new JvmtiAgents());
        ImageSingletons.add(JvmtiEnvManager.class, new JvmtiEnvManager());
        ImageSingletons.add(JvmtiStackTraceUtil.class, new JvmtiStackTraceUtil());
        ImageSingletons.add(JvmtiClassInfoUtil.class, new JvmtiClassInfoUtil());
        ImageSingletons.add(JvmtiGetThreadsUtil.class, new JvmtiGetThreadsUtil());
        ImageSingletons.add(JvmtiMultiStackTracesUtil.class, new JvmtiMultiStackTracesUtil());
        ImageSingletons.add(JvmtiThreadStateUtil.class, new JvmtiThreadStateUtil());
        ImageSingletons.add(JvmtiRawMonitorUtil.class, new JvmtiRawMonitorUtil());
        ImageSingletons.add(JvmtiFunctionTable.class, new JvmtiFunctionTable());
        ImageSingletons.add(JvmtiEnvStorage.class, new JvmtiEnvStorage());
        ImageSingletons.add(JvmtiThreadLocalStorage.class, new JvmtiThreadLocalStorage());
        ImageSingletons.add(JvmtiThreadGroupUtil.class, new JvmtiThreadGroupUtil());
    }

    public static void freeAllJvmtiClassesUnmanagedMemory() {
        ;
        ImageSingletons.lookup(JvmtiRawMonitorUtil.class).releaseAllUnmanagedMemory();
        ImageSingletons.lookup(JvmtiEnvStorage.class).releaseAllUnmanagedMemory();
        ImageSingletons.lookup(JvmtiThreadLocalStorage.class).releaseAllUnmanagedMemory();
    }
}
