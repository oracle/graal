/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jvmtiagentbase;

import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.util.VMError;

/**
 * A utility class for managing the JVMTI agent's isolate. The JVMTI agent uses a single isolate
 * that is created during the {@link JvmtiAgentBase#onLoad} callback.
 */
public final class AgentIsolate {
    private static final CGlobalData<WordPointer> GLOBAL_ISOLATE = CGlobalDataFactory.createWord();

    public static final class Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to enter (or attach to) the global isolate in the current thread.");

        static void enter() {
            int code = CEntryPointActions.enterAttachThread(GLOBAL_ISOLATE.get().read(), true);
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class EnterOrBailoutPrologue {
        static void enter() {
            Isolate global = GLOBAL_ISOLATE.get().read();
            if (global.isNull()) {
                CEntryPointActions.bailoutInPrologue();
            }
            if (CEntryPointActions.enterIsolate(global) != 0) {
                CEntryPointActions.bailoutInPrologue();
            }
        }
    }

    public static void setGlobalIsolate(Isolate isolate) {
        VMError.guarantee(nullPointer().equal(GLOBAL_ISOLATE.get().read()), "Global isolate must be set exactly once");
        GLOBAL_ISOLATE.get().write(isolate);
    }

    public static void resetGlobalIsolate() {
        VMError.guarantee(nullPointer().notEqual(GLOBAL_ISOLATE.get().read()), "Global isolate must be set");
        GLOBAL_ISOLATE.get().write(nullPointer());
    }

    private AgentIsolate() {
    }
}
