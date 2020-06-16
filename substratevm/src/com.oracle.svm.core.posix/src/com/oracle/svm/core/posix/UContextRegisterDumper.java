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
package com.oracle.svm.core.posix;

import org.graalvm.word.PointerBase;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;

public interface UContextRegisterDumper extends RegisterDumper {
    void dumpRegisters(Log log, ucontext_t uContext);

    PointerBase getHeapBase(ucontext_t uContext);

    PointerBase getThreadPointer(ucontext_t uContext);

    PointerBase getSP(ucontext_t uContext);

    PointerBase getIP(ucontext_t uContext);

    @Override
    default void dumpRegisters(Log log, Context context) {
        dumpRegisters(log, (ucontext_t) context);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    default PointerBase getHeapBase(Context context) {
        return getHeapBase((ucontext_t) context);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    default PointerBase getThreadPointer(Context context) {
        return getThreadPointer((ucontext_t) context);
    }

    @Override
    default PointerBase getSP(Context context) {
        return getSP((ucontext_t) context);
    }

    @Override
    default PointerBase getIP(Context context) {
        return getIP((ucontext_t) context);
    }
}
