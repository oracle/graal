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
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.VMThreads;

@TargetClass(com.oracle.svm.core.util.VMError.class)
final class Target_com_oracle_svm_core_util_VMError {

    /*
     * These substitutions let the svm print the real message. The original VMError methods throw a
     * VMError, which let the svm just print the type name of VMError.
     */

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    @Substitute
    private static RuntimeException shouldNotReachHere() {
        throw shouldNotReachHere(null, null);
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        throw shouldNotReachHere(msg, null);
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        throw shouldNotReachHere(null, ex);
    }

    @NeverInline("Prevent change of safepoint status and disabling of stack overflow check to leak into caller, especially when caller is not uninterruptible")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        ThreadStackPrinter.printBacktrace();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();
        VMErrorSubstitutions.shutdown(msg, ex);
        return null;
    }

    @Substitute
    private static RuntimeException unimplemented() {
        return unsupportedFeature("unimplemented");
    }

    @Substitute
    private static RuntimeException unsupportedFeature(String msg) {
        throw new UnsupportedFeatureError(msg);
    }
}

/** Dummy class to have a class with the file's name. */
public class VMErrorSubstitutions {

    @Uninterruptible(reason = "Allow use in uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void shutdown(String msg, Throwable ex) {
        doShutdown(msg, ex);
    }

    @NeverInline("Starting a stack walk in the caller frame")
    private static void doShutdown(String msg, Throwable ex) {
        try {
            Log log = Log.log();
            log.autoflush(true);

            /*
             * Print the error message. If the diagnostic output fails, at least we printed the most
             * important bit of information.
             */
            log.string("Fatal error");
            if (msg != null) {
                log.string(": ").string(msg);
            }
            if (ex != null) {
                log.string(": ").exception(ex);
            } else {
                log.newline();
            }

            SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());

            /*
             * Print the error message again, so that the most important bit of information shows up
             * as the last line (which is probably what users look at first).
             */
            log.string("Fatal error");
            if (msg != null) {
                log.string(": ").string(msg);
            }
            if (ex != null) {
                log.string(": ").string(ex.getClass().getName()).string(": ").string(JDKUtils.getRawMessage(ex));
            }
            log.newline();

        } catch (Throwable ignored) {
            /* Ignore exceptions reported during error reporting, we are going to exit anyway. */
        }
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }
}
