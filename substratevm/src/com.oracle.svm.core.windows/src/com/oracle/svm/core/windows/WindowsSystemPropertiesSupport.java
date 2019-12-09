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
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.LibC;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.WinBase;

@Platforms(Platform.WINDOWS.class)
public class WindowsSystemPropertiesSupport extends SystemPropertiesSupport {

    private static final String USERNAME_ENV_VAR = "USERNAME\0";
    private static final int USERNAME_ENV_VAR_LEN = USERNAME_ENV_VAR.length();

    private static String stringFromWideCString(CCharPointer buffer, int numOfChars) {
        UnsignedWord realLen = WordFactory.unsigned(numOfChars * 2 - 2);
        return CTypeConversion.toJavaString(buffer, realLen, StandardCharsets.UTF_16LE);
    }

    @Override
    protected String userNameValue() {
        int initialLen = WinBase.UNLEN + 1;
        CIntPointer buffLenPointer = StackValue.get(1, CIntPointer.class);
        buffLenPointer.write(initialLen);
        final CCharPointer userNameEnvVar = StackValue.get(USERNAME_ENV_VAR_LEN * 2, CCharPointer.class);
        CTypeConversion.toCString(USERNAME_ENV_VAR, StandardCharsets.UTF_16LE, userNameEnvVar, WordFactory.unsigned(USERNAME_ENV_VAR_LEN * 2));

        CCharPointer envUser = LibC._wgetenv(userNameEnvVar);
        int charNums = LibC.wcslen(envUser);
        if (envUser.isNonNull() && charNums > 0) {
            return stringFromWideCString(envUser, charNums + 1);
        }

        CCharPointer buffer = StackValue.get(2 * initialLen, CCharPointer.class);
        // The following call does not support retry on failures if the content does not fit in the
        // buffer.
        // For now this is the intended implementation as it simplifies it and it will be revised in
        // the future
        int result = WinBase.GetUserNameW(buffer, buffLenPointer);
        if (result == 0 || buffLenPointer.read() == 0) {
            return "unknown"; // matches openjdk
        }

        return stringFromWideCString(buffer, buffLenPointer.read());
    }

    @Override
    protected String userHomeValue() {
        WinBase.HANDLE handleCurrentProcess = Process.GetCurrentProcess();
        WinBase.LPHANDLE tokenHandle = StackValue.get(WinBase.LPHANDLE.class);
        int token = Process.TOKEN_QUERY;
        int success = Process.OpenProcessToken(handleCurrentProcess, token, tokenHandle);
        if (success == 0) {
            return "C:\\"; // matches openjdk
        }

        int initialLen = WinBase.MAX_PATH + 1;
        CIntPointer buffLenPointer = StackValue.get(1, CIntPointer.class);
        buffLenPointer.write(initialLen);
        CCharPointer userHome = StackValue.get(2 * initialLen, CCharPointer.class);

        // The following call does not support retry on failures if the content does not fit in the
        // buffer.
        // For now this is the intended implementation as it simplifies it and it will be revised in
        // the future
        int result = WinBase.GetUserProfileDirectoryW(tokenHandle.read(), userHome, buffLenPointer);
        WinBase.CloseHandle(tokenHandle.read());
        if (result == 0 || buffLenPointer.read() == 0) {
            return "C:\\"; // matches openjdk
        }

        return stringFromWideCString(userHome, buffLenPointer.read());
    }

    @Override
    protected String userDirValue() {
        CCharPointer path = StackValue.get(WinBase.MAX_PATH, CCharPointer.class);
        int result = WinBase.GetCurrentDirectoryA(WinBase.MAX_PATH, path);
        VMError.guarantee(result > 0, "Could not determine value of user.dir");
        return CTypeConversion.toJavaString(path);
    }

    @Override
    protected String tmpdirValue() {
        return "C:\\Temp";
    }

    @Override
    protected String osVersionValue() {
        ByteBuffer versionBytes = ByteBuffer.allocate(4);
        versionBytes.putInt(WinBase.GetVersion());
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
