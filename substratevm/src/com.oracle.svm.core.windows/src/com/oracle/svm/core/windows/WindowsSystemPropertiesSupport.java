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

import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SysinfoAPI;
import com.oracle.svm.core.windows.headers.VerRsrc;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinVer;
import com.oracle.svm.core.windows.headers.WindowsLibC;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

@Platforms(Platform.WINDOWS.class)
public class WindowsSystemPropertiesSupport extends SystemPropertiesSupport {

    /* Null-terminated wide-character string constants. */
    private static final byte[] USERNAME = "USERNAME\0".getBytes(StandardCharsets.UTF_16LE);
    private static final byte[] KERNEL32_DLL = "\\kernel32.dll\0".getBytes(StandardCharsets.UTF_16LE);
    private static final byte[] ROOT_PATH = "\\\0".getBytes(StandardCharsets.UTF_16LE);

    private static final int VER_NT_WORKSTATION = 0x0000001;
    private static final int VER_PLATFORM_WIN32_WINDOWS = 1;
    private static final int VER_PLATFORM_WIN32_NT = 2;

    @Override
    protected String userNameValue() {
        WCharPointer userName = WindowsLibC._wgetenv(NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(USERNAME), 0));
        if (userName.isNonNull()) {
            UnsignedWord length = WindowsLibC.wcslen(userName);
            if (length.aboveThan(0)) {
                return toJavaString(userName, Math.toIntExact(length.rawValue()));
            }
        }

        int maxLength = WinBase.UNLEN + 1;
        userName = StackValue.get(maxLength, WCharPointer.class);
        CIntPointer lengthPointer = StackValue.get(CIntPointer.class);
        lengthPointer.write(maxLength);
        if (WinBase.GetUserNameW(userName, lengthPointer) != 0) {
            return toJavaString(userName, lengthPointer.read() - 1);
        }

        return "unknown"; /* matches openjdk */
    }

    @Override
    protected String userHomeValue() {
        WinBase.LPHANDLE tokenHandle = StackValue.get(WinBase.LPHANDLE.class);
        if (Process.NoTransitions.OpenProcessToken(Process.NoTransitions.GetCurrentProcess(), Process.TOKEN_QUERY(), tokenHandle) == 0) {
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

        return toJavaString(userHome, buffLenPointer.read() - 1);
    }

    @Override
    protected String userDirValue() {
        int maxLength = WinBase.MAX_PATH;
        WCharPointer userDir = StackValue.get(maxLength, WCharPointer.class);
        int length = WinBase.GetCurrentDirectoryW(maxLength, userDir);
        VMError.guarantee(length > 0 && length < maxLength, "Could not determine value of user.dir");
        return toJavaString(userDir, length);
    }

    @Override
    protected String tmpdirValue() {
        int maxLength = WinBase.MAX_PATH + 1;
        WCharPointer tmpdir = StackValue.get(maxLength, WCharPointer.class);
        int length = FileAPI.GetTempPathW(maxLength, tmpdir);
        VMError.guarantee(length > 0, "Could not determine value of java.io.tmpdir");
        return toJavaString(tmpdir, length);
    }

    /* Null-terminated wide-character string constant. */
    private static final byte[] PATH = "PATH\0".getBytes(StandardCharsets.UTF_16LE);

    @Override
    protected String javaLibraryPathValue() {
        /*
         * Adapted from `os::init_system_properties_values` in
         * `src/hotspot/os/windows/os_windows.cpp`, but omits HotSpot specifics.
         */
        int tmpLength;
        WCharPointer tmp = StackValue.get(WinBase.MAX_PATH, WCharPointer.class);

        WCharPointer path = WindowsLibC._wgetenv(NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(PATH), 0));
        int pathLength = path.isNonNull() ? Math.toIntExact(WindowsLibC.wcslen(path).rawValue()) : 0;

        StringBuilder libraryPath = new StringBuilder(3 * WinBase.MAX_PATH + pathLength + 5);

        /* Add the directory from which application is loaded. */
        tmpLength = LibLoaderAPI.GetModuleFileNameW(WordFactory.nullPointer(), tmp, WinBase.MAX_PATH);
        VMError.guarantee(tmpLength > 0 && tmpLength < WinBase.MAX_PATH);
        libraryPath.append(asCharBuffer(tmp, tmpLength));
        /* Get rid of `\<filename>.exe`. */
        libraryPath.setLength(libraryPath.lastIndexOf("\\"));

        /* Add System directory. */
        tmpLength = SysinfoAPI.GetSystemDirectoryW(tmp, WinBase.MAX_PATH);
        VMError.guarantee(tmpLength > 0 && tmpLength < WinBase.MAX_PATH);
        libraryPath.append(';');
        libraryPath.append(asCharBuffer(tmp, tmpLength));

        /* Add Windows directory. */
        tmpLength = SysinfoAPI.GetWindowsDirectoryW(tmp, WinBase.MAX_PATH);
        VMError.guarantee(tmpLength > 0 && tmpLength < WinBase.MAX_PATH);
        libraryPath.append(';');
        libraryPath.append(asCharBuffer(tmp, tmpLength));

        /* Add the PATH environment variable. */
        if (path.isNonNull()) {
            libraryPath.append(';');
            libraryPath.append(asCharBuffer(path, pathLength));
        }

        /* Add the current directory. */
        libraryPath.append(";.");

        return libraryPath.toString();
    }

    private static String toJavaString(WCharPointer wcString, int length) {
        return asCharBuffer(wcString, length).toString();
    }

    private static CharBuffer asCharBuffer(WCharPointer wcString, int length) {
        /*
         * Wide characters encoded using UTF-16LE (for little-endian) are the native character
         * format on Windows, so we can simply wrap wide strings without any conversion.
         */
        return CTypeConversion.asByteBuffer(wcString, length * SizeOf.get(WCharPointer.class))
                        .order(ByteOrder.LITTLE_ENDIAN).asCharBuffer();
    }

    private Pair<String, String> cachedOsNameAndVersion;

    @Override
    protected String osNameValue() {
        if (cachedOsNameAndVersion == null) {
            cachedOsNameAndVersion = getOsNameAndVersion();
        }
        return cachedOsNameAndVersion.getLeft();
    }

    @Override
    protected String osVersionValue() {
        if (cachedOsNameAndVersion == null) {
            cachedOsNameAndVersion = getOsNameAndVersion();
        }
        return cachedOsNameAndVersion.getRight();
    }

    public Pair<String, String> getOsNameAndVersion() {
        /*
         * Reimplementation of code from java_props_md.c
         */
        SysinfoAPI.OSVERSIONINFOEXA ver = StackValue.get(SysinfoAPI.OSVERSIONINFOEXA.class);
        ver.dwOSVersionInfoSize(SizeOf.get(SysinfoAPI.OSVERSIONINFOEXA.class));
        SysinfoAPI.GetVersionExA(ver);

        boolean is64bit = ImageSingletons.lookup(Platform.class).getArchitecture().endsWith("64");
        boolean isWorkstation = ver.wProductType() == VER_NT_WORKSTATION;
        int platformId = ver.dwPlatformId();

        int majorVersion = ver.dwMajorVersion();
        int minorVersion = ver.dwMinorVersion();
        int buildNumber = ver.dwBuildNumber();
        do {
            /* Get the full path to \Windows\System32\kernel32.dll ... */
            WindowsLibC.WCharPointer kernel32Path = StackValue.get(WinBase.MAX_PATH, WindowsLibC.WCharPointer.class);
            WindowsLibC.WCharPointer kernel32Dll = NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(KERNEL32_DLL), 0);
            int len = WinBase.MAX_PATH - (int) WindowsLibC.wcslen(kernel32Dll).rawValue() - 1;
            int ret = SysinfoAPI.GetSystemDirectoryW(kernel32Path, len);
            if (ret == 0 || ret > len) {
                break;
            }
            WindowsLibC.wcsncat(kernel32Path, kernel32Dll, WordFactory.unsigned(WinBase.MAX_PATH - ret));

            /* ... and use that for determining what version of Windows we're running on. */
            int versionSize = WinVer.GetFileVersionInfoSizeW(kernel32Path, WordFactory.nullPointer());
            if (versionSize == 0) {
                break;
            }

            VoidPointer versionInfo = LibC.malloc(WordFactory.unsigned(versionSize));
            if (versionInfo.isNull()) {
                break;
            }

            if (WinVer.GetFileVersionInfoW(kernel32Path, 0, versionSize, versionInfo) == 0) {
                LibC.free(versionInfo);
                break;
            }

            WindowsLibC.WCharPointer rootPath = NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(ROOT_PATH), 0);
            WordPointer fileInfoPointer = StackValue.get(WordPointer.class);
            CIntPointer lengthPointer = StackValue.get(CIntPointer.class);
            if (WinVer.VerQueryValueW(versionInfo, rootPath, fileInfoPointer, lengthPointer) == 0) {
                LibC.free(versionInfo);
                break;
            }

            VerRsrc.VS_FIXEDFILEINFO fileInfo = fileInfoPointer.read();
            majorVersion = (short) (fileInfo.dwProductVersionMS() >> 16); // HIWORD
            minorVersion = (short) fileInfo.dwProductVersionMS(); // LOWORD
            buildNumber = (short) (fileInfo.dwProductVersionLS() >> 16); // HIWORD
            LibC.free(versionInfo);
        } while (false);

        String osVersion = majorVersion + "." + minorVersion;
        String osName;

        switch (platformId) {
            case VER_PLATFORM_WIN32_WINDOWS:
                if (majorVersion == 4) {
                    switch (minorVersion) {
                        case 0:
                            osName = "Windows 95";
                            break;
                        case 10:
                            osName = "Windows 98";
                            break;
                        case 90:
                            osName = "Windows Me";
                            break;
                        default:
                            osName = "Windows 9X (unknown)";
                            break;
                    }
                } else {
                    osName = "Windows 9X (unknown)";
                }
                break;
            case VER_PLATFORM_WIN32_NT:
                if (majorVersion <= 4) {
                    osName = "Windows NT";
                } else if (majorVersion == 5) {
                    switch (minorVersion) {
                        case 0:
                            osName = "Windows 2000";
                            break;
                        case 1:
                            osName = "Windows XP";
                            break;
                        case 2:
                            if (isWorkstation && is64bit) {
                                osName = "Windows XP"; /* 64 bit */
                            } else {
                                osName = "Windows 2003";
                            }
                            break;
                        default:
                            osName = "Windows NT (unknown)";
                            break;
                    }
                } else if (majorVersion == 6) {
                    if (isWorkstation) {
                        switch (minorVersion) {
                            case 0:
                                osName = "Windows Vista";
                                break;
                            case 1:
                                osName = "Windows 7";
                                break;
                            case 2:
                                osName = "Windows 8";
                                break;
                            case 3:
                                osName = "Windows 8.1";
                                break;
                            default:
                                osName = "Windows NT (unknown)";
                        }
                    } else {
                        switch (minorVersion) {
                            case 0:
                                osName = "Windows Server 2008";
                                break;
                            case 1:
                                osName = "Windows Server 2008 R2";
                                break;
                            case 2:
                                osName = "Windows Server 2012";
                                break;
                            case 3:
                                osName = "Windows Server 2012 R2";
                                break;
                            default:
                                osName = "Windows NT (unknown)";
                        }
                    }
                } else if (majorVersion == 10) {
                    if (isWorkstation) {
                        switch (minorVersion) {
                            case 0:
                                osName = "Windows 10";
                                break;
                            default:
                                osName = "Windows NT (unknown)";
                        }
                    } else {
                        switch (minorVersion) {
                            case 0:
                                if (buildNumber > 17762) {
                                    osName = "Windows Server 2019";
                                } else {
                                    osName = "Windows Server 2016";
                                }
                                break;
                            default:
                                osName = "Windows NT (unknown)";
                        }
                    }
                } else {
                    osName = "Windows NT (unknown)";
                }
                break;
            default:
                osName = "Windows (unknown)";
                break;
        }
        return Pair.create(osName, osVersion);
    }
}

@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
class WindowsSystemPropertiesFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(SystemPropertiesSupport.class, new WindowsSystemPropertiesSupport());
    }
}
