/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.substitute;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;
import com.oracle.svm.webimage.substitute.system.Target_java_lang_Throwable_Web;

import jdk.graal.compiler.nodes.UnreachableNode;

public class VMErrorSubstitutions {

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        shutdown(msg, ex);
        throw UnreachableNode.unreachable();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void shutdown(String msg, Throwable ex) {
        Log log = Log.log();
        if (log != null) {
            try {
                /*
                 * Print the error message again, so that the most important bit of information
                 * shows up as the last line (which is probably what users look at first).
                 */
                log.string("Fatal error");
                if (msg != null) {
                    log.string(": ").string(msg);
                }
                if (ex != null) {
                    log.string(": ").string(ex.getClass().getName()).string(": ").string(SubstrateUtil.cast(ex, Target_java_lang_Throwable_Web.class).detailMessage);
                }
                log.newline();
            } catch (Throwable ignored) {
                /*
                 * Ignore exceptions reported during error reporting, we are going to exit anyway.
                 */
            }
        }
        WasmTrapNode.trap();
    }

}

@TargetClass(com.oracle.svm.core.util.VMError.class)
@Platforms(WebImageWasmLMPlatform.class)
@SuppressWarnings("unused")
final class Target_com_oracle_svm_core_util_VMError_Web {

    /*
     * These substitutions let the svm print the real message. The original VMError methods throw a
     * VMError, which let the svm just print the type name of VMError.
     */

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        throw VMErrorSubstitutions.shouldNotReachHere(msg, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        throw VMErrorSubstitutions.shouldNotReachHere(null, ex);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        throw VMErrorSubstitutions.shouldNotReachHere(msg, ex);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereSubstitution() {
        throw VMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereSubstitution, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHereUnexpectedInput(Object input) {
        throw VMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereUnexpectedInput, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHereAtRuntime() {
        throw VMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereAtRuntime, null);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereOverrideInChild() {
        throw VMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereOverrideInChild, null);
    }

    @Substitute
    private static RuntimeException unsupportedPlatform() {
        throw VMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereUnsupportedPlatform, null);
    }

    @Substitute
    private static RuntimeException intentionallyUnimplemented() {
        return unsupportedFeature("this method has intentionally not been implemented");
    }

    @Substitute
    private static RuntimeException unsupportedFeature(String msg) {
        throw new Error("UNSUPPORTED FEATURE: " + msg);
    }
}
