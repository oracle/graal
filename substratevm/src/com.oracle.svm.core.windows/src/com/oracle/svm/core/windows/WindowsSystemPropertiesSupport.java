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

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.WinBase;

@Platforms(Platform.WINDOWS.class)
public class WindowsSystemPropertiesSupport extends SystemPropertiesSupport {

    @Override
    protected String userNameValue() {
        return "somebody";
    }

    @Override
    protected String userHomeValue() {
        return "C:\\Users\\somebody";
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
