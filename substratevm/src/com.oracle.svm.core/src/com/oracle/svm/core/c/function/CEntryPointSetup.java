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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;

public class CEntryPointSetup {
    public static final class EnterPrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to enter the specified IsolateThread context.");

        @Uninterruptible(reason = "prologue")
        static void enter(IsolateThread thread) {
            int code = CEntryPointActions.enter(thread);
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class EnterCreateIsolatePrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to create a new Isolate.");

        @Uninterruptible(reason = "prologue")
        public static void enter() {
            int code = CEntryPointActions.enterCreateIsolate(Word.nullPointer());
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class LeaveEpilogue implements CEntryPointOptions.Epilogue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to leave the current IsolateThread context.");

        @Uninterruptible(reason = "epilogue")
        static void leave() {
            int code = CEntryPointActions.leave();
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class LeaveDetachThreadEpilogue implements CEntryPointOptions.Epilogue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to leave the current IsolateThread context and to detach the current thread.");

        @Uninterruptible(reason = "epilogue")
        public static void leave() {
            int code = CEntryPointActions.leaveDetachThread();
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    public static final class LeaveTearDownIsolateEpilogue implements CEntryPointOptions.Epilogue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString(
                        "Failed to leave the current IsolateThread context and to tear down the Isolate.");

        @Uninterruptible(reason = "epilogue")
        static void leave() {
            int code = CEntryPointActions.leaveTearDownIsolate();
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }
}
