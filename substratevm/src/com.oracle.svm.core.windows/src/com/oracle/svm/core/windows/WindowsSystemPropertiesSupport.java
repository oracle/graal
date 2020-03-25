/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibC;
import com.oracle.svm.core.windows.headers.LibC.WCharPointer;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SysinfoAPI;
import com.oracle.svm.core.windows.headers.WinBase;

@Platforms(Platform.WINDOWS.class)
public class WindowsSystemPropertiesSupport extends SystemPropertiesSupport {

    /* Null-terminated wide-character string. */
    private static final byte[] USERNAME = "USERNAME\0".getBytes(StandardCharsets.UTF_16LE);

    @Override
    protected String userNameValue() {
        WCharPointer userName = LibC._wgetenv(NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(USERNAME), 0));
        UnsignedWord length = LibC.wcslen(userName);
        if (userName.isNonNull() && length.aboveThan(0)) {
            return toJavaString(userName, length);
        }

        int maxLength = WinBase.UNLEN + 1;
        userName = StackValue.get(maxLength, WCharPointer.class);
        CIntPointer lengthPointer = StackValue.get(CIntPointer.class);
        lengthPointer.write(maxLength);
        if (WinBase.GetUserNameW(userName, lengthPointer) == 0) {
            return "unknown"; /* matches openjdk */
        }

        return toJavaString(userName, WordFactory.unsigned(lengthPointer.read() - 1));
    }

    @Override
    protected String userHomeValue() {
        WinBase.LPHANDLE tokenHandle = StackValue.get(WinBase.LPHANDLE.class);
        if (Process.OpenProcessToken(Process.GetCurrentProcess(), Process.TOKEN_QUERY(), tokenHandle) == 0) {
            return "C:\\"; // matches openjdk
        }

        int initialLen = WinBase.MAX_PATH + 1;
        CIntPointer buffLenPointer = StackValue.get(CIntPointer.class);
        buffLenPointer.write(initialLen);
        WCharPointer userHome = StackValue.get(initialLen, WCharPointer.class);

        // The following call does not support retry on failures if the content does not fit in the
        // buffer.
        // For now this is the intended implementation as it simplifies it and it will be revised in
        // the future
        int result = WinBase.GetUserProfileDirectoryW(tokenHandle.read(), userHome, buffLenPointer);
        WinBase.CloseHandle(tokenHandle.read());
        if (result == 0) {
            return "C:\\"; // matches openjdk
        }

        return toJavaString(userHome, WordFactory.unsigned(buffLenPointer.read() - 1));
    }

    @Override
    protected String userDirValue() {
        int maxLength = WinBase.MAX_PATH;
        WCharPointer userDir = StackValue.get(maxLength, WCharPointer.class);
        int length = WinBase.GetCurrentDirectoryW(maxLength, userDir);
        VMError.guarantee(length > 0 && length < maxLength, "Could not determine value of user.dir");
        return toJavaString(userDir, WordFactory.unsigned(length));
    }

    @Override
    protected String tmpdirValue() {
        int maxLength = WinBase.MAX_PATH + 1;
        WCharPointer tmpdir = StackValue.get(maxLength, WCharPointer.class);
        int length = FileAPI.GetTempPathW(maxLength, tmpdir);
        VMError.guarantee(length > 0, "Could not determine value of java.io.tmpdir");
        return toJavaString(tmpdir, WordFactory.unsigned(length));
    }

    private static String toJavaString(WCharPointer wcString, UnsignedWord length) {
        return CTypeConversion.toJavaString((CCharPointer) wcString, SizeOf.unsigned(WCharPointer.class).multiply(length), StandardCharsets.UTF_16LE);
    }

    @Override
    protected String osVersionValue() {
        ByteBuffer versionBytes = ByteBuffer.allocate(4);
        versionBytes.putInt(SysinfoAPI.GetVersion());
        int majorVersion = versionBytes.get(3);
        int minorVersion = versionBytes.get(2);
        return majorVersion + "." + minorVersion;
    }
}

@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
class WindowsSystemPropertiesFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SystemPropertiesSupport.class, new WindowsSystemPropertiesSupport());
    }
}
