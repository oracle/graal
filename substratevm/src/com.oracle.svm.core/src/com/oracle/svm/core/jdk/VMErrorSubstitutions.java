/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.VMThreads;

@TargetClass(com.oracle.svm.core.util.VMError.class)
final class Target_com_oracle_svm_core_util_VMError {

    /*
     * These substitutions let the svm print the real message. The original VMError methods throw a
     * VMError, which let the svm just print the type name of VMError.
     */

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere() {
        ThreadStackPrinter.printBacktrace();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        VMErrorSubstitutions.shutdown(null, null);
        return null;
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        ThreadStackPrinter.printBacktrace();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        VMErrorSubstitutions.shutdown(msg, null);
        return null;
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        ThreadStackPrinter.printBacktrace();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        VMErrorSubstitutions.shutdown(null, ex);
        return null;
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        ThreadStackPrinter.printBacktrace();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        VMErrorSubstitutions.shutdown(msg, ex);
        return null;
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static void guarantee(boolean condition) {
        if (!condition) {
            throw shouldNotReachHere("guarantee failed");
        }
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static void guarantee(boolean condition, String msg) {
        if (!condition) {
            throw shouldNotReachHere(msg);
        }
    }

    @Substitute
    private static RuntimeException unimplemented() {
        return unsupportedFeature("unimplemented");
    }

    @Substitute
    @NeverInline("avoid corner cases in error reporting: when we have a call to this method, we have a proper stack trace that includes the caller")
    private static RuntimeException unsupportedFeature(String msg) {
        throw new UnsupportedFeatureError(msg);
    }
}

/** Dummy class to have a class with the file's name. */
public class VMErrorSubstitutions {

    @Uninterruptible(reason = "Allow use in uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void shutdown(String msg, Throwable ex) {
        Log log = Log.log();
        log.autoflush(true);
        log.string("VMError.shouldNotReachHere");
        if (msg != null) {
            log.string(": ").string(msg);
        }
        if (ex != null) {
            /*
             * We do not want to call getMessage(), since it can be overridden by subclasses of
             * Throwable. So we access the raw detailMessage directly from the field in Throwable.
             * That is better than printing nothing.
             */
            String detailMessage = JDKUtils.getRawMessage(ex);
            StackTraceElement[] stackTrace = JDKUtils.getRawStackTrace(ex);

            log.string(": ").string(ex.getClass().getName()).string(": ").string(detailMessage);
            if (stackTrace != null) {
                for (StackTraceElement element : stackTrace) {
                    if (element != null) {
                        log.newline();
                        log.string("    at ").string(element.getClassName()).string(".").string(element.getMethodName());
                        log.string("(").string(element.getFileName()).string(":").signed(element.getLineNumber()).string(")");
                    }
                }
            }
        }
        log.newline();
        doShutdown(log);
    }

    private static void doShutdown(Log log) {
        SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }
}
