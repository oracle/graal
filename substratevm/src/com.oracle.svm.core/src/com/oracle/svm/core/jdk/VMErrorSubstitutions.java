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

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.UnreachableNode;

@TargetClass(com.oracle.svm.core.util.VMError.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class Target_com_oracle_svm_core_util_VMError {

    /*
     * These substitutions let the svm print the real message. The original VMError methods throw a
     * VMError, which let the svm just print the type name of VMError.
     */

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHereSubstitution() {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), VMError.msgShouldNotReachHereSubstitution, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHereOverrideInChild() {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), VMError.msgShouldNotReachHereOverrideInChild, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHereAtRuntime() {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), VMError.msgShouldNotReachHereAtRuntime, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHereUnexpectedInput(@SuppressWarnings("unused") Object input) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), VMError.msgShouldNotReachHereUnexpectedInput, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), msg, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), null, ex);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), msg, ex);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException unsupportedPlatform() {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), VMError.msgShouldNotReachHereUnsupportedPlatform, null);
    }

    @Substitute
    private static RuntimeException intentionallyUnimplemented() {
        return unsupportedFeature(VMError.msgUnimplementedIntentionally);
    }

    @Substitute
    private static RuntimeException unsupportedFeature(String msg) {
        throw new UnsupportedFeatureError(msg);
    }
}

/** Dummy class to have a class with the file's name. */
public class VMErrorSubstitutions {

    /*
     * Must only be called from @NeverInline functions above to prevent change of safepoint status
     * and disabling of stack overflow check to leak into caller, especially when caller is not
     * uninterruptible
     */
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    static RuntimeException shouldNotReachHere(CodePointer callerIP, String msg, Throwable ex) {
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        VMErrorSubstitutions.shutdown(callerIP, msg, ex);
        throw UnreachableNode.unreachable();
    }

    @Uninterruptible(reason = "Allow use in uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void shutdown(CodePointer callerIP, String msg, Throwable ex) {
        doShutdown(callerIP, msg, ex);
    }

    @NeverInline("Starting a stack walk in the caller frame")
    private static void doShutdown(CodePointer callerIP, String msg, Throwable ex) {
        LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
        Log log = Log.enterFatalContext(logHandler, callerIP, msg, ex);
        if (log != null) {
            try {
                /*
                 * Print the error message. If the diagnostic output fails, at least we printed the
                 * most important bit of information.
                 */
                log.string("Fatal error");
                if (msg != null) {
                    log.string(": ").string(msg);
                }
                if (ex != null) {
                    log.string(": ").exception(ex);
                }
                log.newline();

                SubstrateDiagnostics.printFatalError(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());

                /*
                 * Print the error message again, so that the most important bit of information
                 * shows up as the last line (which is probably what users look at first).
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
                /*
                 * Ignore exceptions reported during error reporting, we are going to exit anyway.
                 */
            }
        }
        logHandler.fatalError();
    }
}
