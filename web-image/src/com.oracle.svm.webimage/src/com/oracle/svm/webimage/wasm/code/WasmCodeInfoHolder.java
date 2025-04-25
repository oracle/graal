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

package com.oracle.svm.webimage.wasm.code;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * Stores {@link WasmCodeInfoQueryResult} for each instruction pointer (IP).
 */
public class WasmCodeInfoHolder {
    /**
     * Maps an IP to its {@link WasmCodeInfoQueryResult}.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private static WasmCodeInfoQueryResult[] codeInfos = null;

    /**
     * Lookup {@link WasmCodeInfoQueryResult} by its IP.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static WasmCodeInfoQueryResult getCodeInfo(CodePointer ip) {
        int maxIP = codeInfos.length;
        long raw = ip.rawValue();

        if (Word.unsigned(raw).aboveOrEqual(maxIP)) {
            throw reportInvalidIp(ip);
        } else {
            return codeInfos[(int) raw];
        }
    }

    @Uninterruptible(reason = "Switch to interruptible code for fatal error reporting.", calleeMustBe = false)
    private static RuntimeException reportInvalidIp(CodePointer ip) {
        Log.log().hex(ip).newline();
        throw VMError.shouldNotReachHere("Tried to look up out-of-range IP");
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setCodeInfos(WasmCodeInfoQueryResult[] codeInfos) {
        WasmCodeInfoHolder.codeInfos = codeInfos;
    }
}
