/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
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
        Log log = Log.log();
        log.autoflush(true);
        log.string("VMError.shouldNotReachHere").newline();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());
        ConfigurationValues.getOSInterface().abort();
        return null;
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        ThreadStackPrinter.printBacktrace();
        Log log = Log.log();
        log.autoflush(true);
        log.string("VMError.shouldNotReachHere: ").string(msg).newline();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());
        ConfigurationValues.getOSInterface().abort();
        return null;
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        ThreadStackPrinter.printBacktrace();
        /*
         * We do not want to call getMessage(), since it can be overriden by subclasses of
         * Throwable. So we access the raw detailMessage directly from the field in Throwable. That
         * is better than printing nothing.
         */
        Log log = Log.log();
        log.autoflush(true);
        String detailMessage = JDKUtils.getRawMessage(ex);
        log.string("VMError.shouldNotReachHere: ").string(ex.getClass().getName()).string(": ").string(detailMessage).newline();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());
        ConfigurationValues.getOSInterface().abort();
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

class UnsupportedFeatureError extends Error {
    private static final long serialVersionUID = -2281164998442235179L;

    UnsupportedFeatureError(String message) {
        super(message);
    }
}

/** Dummy class to have a class with the file's name. */
public class VMErrorSubstitutions {
}
