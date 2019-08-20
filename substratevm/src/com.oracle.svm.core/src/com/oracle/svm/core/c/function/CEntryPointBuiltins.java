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
package com.oracle.svm.core.c.function;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Methods implementing the built-ins of {@link Builtin}, which are matched by name
 * (case-insensitive). The methods may only have {@link Isolate} or {@link IsolateThread}
 * parameters, which are matched to the parameters of entry points specifying
 * {@link CEntryPoint#builtin()}.
 */
public final class CEntryPointBuiltins {
    private static final String UNINTERRUPTIBLE_REASON = "Unsafe state in case of failure";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface CEntryPointBuiltinImplementation {
        Builtin builtin() default Builtin.NO_BUILTIN;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished)
    @CEntryPointBuiltinImplementation(builtin = Builtin.CREATE_ISOLATE)
    public static IsolateThread createIsolate() {
        IsolateThread result = WordFactory.nullPointer();
        int status = CEntryPointActions.enterCreateIsolate(WordFactory.nullPointer());
        if (status == 0) {
            result = CurrentIsolate.getCurrentThread();
            CEntryPointActions.leave();
        }
        return result;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished)
    @CEntryPointBuiltinImplementation(builtin = Builtin.ATTACH_THREAD)
    public static IsolateThread attachThread(Isolate isolate) {
        IsolateThread result = WordFactory.nullPointer();
        int status = CEntryPointActions.enterAttachThread(isolate, true);
        if (status == 0) {
            result = CurrentIsolate.getCurrentThread();
            status = CEntryPointActions.leave();
        }
        return result;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished)
    @CEntryPointBuiltinImplementation(builtin = Builtin.GET_CURRENT_THREAD)
    public static IsolateThread getCurrentThread(Isolate isolate) {
        int status = CEntryPointActions.enterIsolate(isolate);
        if (status != 0) {
            return WordFactory.nullPointer();
        }
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        if (CEntryPointActions.leave() != 0) {
            thread = WordFactory.nullPointer();
        }
        return thread;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished)
    @CEntryPointBuiltinImplementation(builtin = Builtin.GET_ISOLATE)
    public static Isolate getIsolate(IsolateThread thread) {
        int status = CEntryPointActions.enter(thread);
        if (status != 0) {
            return WordFactory.nullPointer();
        }
        Isolate isolate = CurrentIsolate.getIsolate();
        if (CEntryPointActions.leave() != 0) {
            isolate = WordFactory.nullPointer();
        }
        return isolate;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished)
    @CEntryPointBuiltinImplementation(builtin = Builtin.DETACH_THREAD)
    public static int detachThread(IsolateThread thread) {
        int status = CEntryPointActions.enter(thread);
        if (status != 0) {
            CEntryPointActions.leave();
            return status;
        }
        status = CEntryPointActions.leaveDetachThread();
        return status;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished)
    @CEntryPointBuiltinImplementation(builtin = Builtin.TEAR_DOWN_ISOLATE)
    public static int tearDownIsolate(IsolateThread isolateThread) {
        int result = CEntryPointActions.enter(isolateThread);
        if (result != 0) {
            CEntryPointActions.leave();
            return result;
        }
        result = CEntryPointActions.leaveTearDownIsolate();
        return result;
    }

    private CEntryPointBuiltins() {
    }
}
