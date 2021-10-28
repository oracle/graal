/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

public interface RegisterDumper {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static RegisterDumper singleton() {
        if (!ImageSingletons.contains(RegisterDumper.class)) {
            throw VMError.shouldNotReachHere();
        }
        return ImageSingletons.lookup(RegisterDumper.class);
    }

    static void dumpReg(Log log, String label, long value, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        log.string(label).zhex(value);
        if (printLocationInfo) {
            log.spaces(1);
            SubstrateDiagnostics.printLocationInfo(log, WordFactory.unsigned(value), allowJavaHeapAccess, allowUnsafeOperations);
        }
        log.newline();
    }

    interface Context extends PointerBase {
    }

    void dumpRegisters(Log log, Context context, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations);

    PointerBase getHeapBase(Context context);

    PointerBase getThreadPointer(Context context);

    PointerBase getSP(Context context);

    PointerBase getIP(Context context);
}
