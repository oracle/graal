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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.windows.headers.StringAPISet.CP_ACP;
import static com.oracle.svm.core.windows.headers.StringAPISet.MultiByteToWideChar;

import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WindowsLibC;

import jdk.graal.compiler.word.Word;

@AutomaticallyRegisteredImageSingleton
class WindowsJavaMainWrapperArgsSupport extends JavaMainWrapper.ArgsSupport {
    @Override
    protected String toJavaArg(CCharPointer rawArg) {
        /*
         * On Windows, wide-character strings are UTF-16LE, matching Java char[]. So we convert ANSI
         * bytes directly into a Java char[] (excluding the trailing NUL) and construct the String.
         */
        int rawLen = UnsignedUtils.safeToInt(WindowsLibC.strlen(rawArg)); // excludes trailing NUL
        if (rawLen == 0) {
            return ""; // MultiByteToWideChar would fail here
        }
        int wcLen = checkResult(MultiByteToWideChar(CP_ACP(), 0, rawArg, rawLen, Word.nullPointer(), 0));
        char[] wcArg = new char[wcLen];
        try (var wcBuf = PrimitiveArrayView.createForReadingAndWriting(wcArg)) {
            checkResult(MultiByteToWideChar(CP_ACP(), 0, rawArg, rawLen, wcBuf.addressOfArrayElement(0), wcLen));
        }
        return new String(wcArg);
    }

    private static int checkResult(int result) {
        if (result == 0) {
            var log = new StringBuilderLog();
            log.string("MultiByteToWideChar failed with error ").hex(WinBase.GetLastError());
            throw VMError.shouldNotReachHere(log.getResult());
        }
        return result;
    }
}
