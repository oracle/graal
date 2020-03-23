/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.function;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;

public class CEntryPointSetup {

    /**
     * The sentinel value for {@link Isolate} when the native image is built so that there can be
     * only a single isolate.
     */
    public static final Word SINGLE_ISOLATE_SENTINEL = WordFactory.unsigned(0x150_150_150_150_150L);

    /** @see #SINGLE_THREAD_SENTINEL */
    public static final int SINGLE_ISOLATE_TO_SINGLE_THREAD_ADDEND = 0x777 - 0x150;

    /**
     * The sentinel value for {@link IsolateThread} when the native image is built so that there can
     * be only a single isolate with a single thread.
     */
    public static final Word SINGLE_THREAD_SENTINEL = SINGLE_ISOLATE_SENTINEL.add(SINGLE_ISOLATE_TO_SINGLE_THREAD_ADDEND);

    public static final class EnterPrologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to enter the specified IsolateThread context.");

        static void enter(IsolateThread thread) {
            int code = CEntryPointActions.enter(thread);
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class EnterIsolatePrologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to enter the provided Isolate in the current thread. The thread might not have been attached to the Isolate first.");

        static void enter(Isolate isolate) {
            int code = CEntryPointActions.enterIsolate(isolate);
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class EnterCreateIsolatePrologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to create a new Isolate.");

        public static void enter() {
            int code = CEntryPointActions.enterCreateIsolate(WordFactory.nullPointer());
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class LeaveEpilogue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to leave the current IsolateThread context.");

        static void leave() {
            int code = CEntryPointActions.leave();
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class LeaveDetachThreadEpilogue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to leave the current IsolateThread context and to detach the current thread.");

        static void leave() {
            int code = CEntryPointActions.leaveDetachThread();
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class LeaveTearDownIsolateEpilogue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to leave the current IsolateThread context and to tear down the Isolate.");

        static void leave() {
            int code = CEntryPointActions.leaveTearDownIsolate();
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }
}
