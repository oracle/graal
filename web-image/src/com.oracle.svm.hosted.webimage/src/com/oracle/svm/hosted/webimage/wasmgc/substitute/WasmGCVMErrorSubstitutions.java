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

package com.oracle.svm.hosted.webimage.wasmgc.substitute;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.UnsupportedFeatureError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.debug.WasmDebug;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.substitute.system.Target_java_lang_Throwable_Web;

import jdk.graal.compiler.nodes.UnreachableNode;

public class WasmGCVMErrorSubstitutions {
    static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        StringBuilder sb = new StringBuilder("Fatal error");

        if (msg != null) {
            sb.append(": ").append(msg);
        }
        if (ex != null) {
            sb.append(": ").append(ex.getClass().getName()).append(": ").append(SubstrateUtil.cast(ex, Target_java_lang_Throwable_Web.class).detailMessage);
        }
        WasmDebug.getErrorStream().println(sb);
        WasmTrapNode.trap();
        throw UnreachableNode.unreachable();
    }
}

@TargetClass(com.oracle.svm.core.util.VMError.class)
@Platforms(WebImageWasmGCPlatform.class)
@SuppressWarnings("unused")
final class Target_com_oracle_svm_core_util_VMError_Web {

    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(msg, null);
    }

    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(null, ex);
    }

    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(msg, ex);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereSubstitution() {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereSubstitution, null);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereUnexpectedInput(Object input) {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereUnexpectedInput, null);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereAtRuntime() {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereAtRuntime, null);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereOverrideInChild() {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereOverrideInChild, null);
    }

    @Substitute
    private static RuntimeException unsupportedPlatform() {
        throw WasmGCVMErrorSubstitutions.shouldNotReachHere(VMError.msgShouldNotReachHereUnsupportedPlatform, null);
    }

    @Substitute
    private static RuntimeException intentionallyUnimplemented() {
        return unsupportedFeature("this method has intentionally not been implemented");
    }

    @Substitute
    private static RuntimeException unsupportedFeature(String msg) {
        throw new UnsupportedFeatureError(msg);
    }
}
