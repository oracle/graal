/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.log;

import java.io.PrintStream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.guest.staging.log.Log;

import jdk.graal.compiler.api.replacements.Fold;

/** Core implementations of the static access operations exposed by {@link Log}. */
public final class CoreLogSupport {

    @Platforms(HOSTED_ONLY.class)
    public static void finalizeDefaultLogHandler(LogHandler handler) {
        if (!ImageSingletons.contains(LogHandler.class)) {
            ImageSingletons.add(LogHandler.class, new FunctionPointerLogHandler(handler));
        }
    }

    public static Log enterFatalContext(LogHandler logHandler, CodePointer callerIP, String msg, Throwable ex) {
        if (logHandler instanceof LogHandlerExtension ext) {
            return ext.enterFatalContext(callerIP, msg, ex);
        }
        return log();
    }

    @Fold
    public static Log log() {
        return Loggers.realLog;
    }

    @Fold
    public static PrintStream logStream() {
        return Loggers.logStream;
    }

    @Fold
    public static Log noopLog() {
        return Loggers.noopLog;
    }

    private CoreLogSupport() {
    }
}
