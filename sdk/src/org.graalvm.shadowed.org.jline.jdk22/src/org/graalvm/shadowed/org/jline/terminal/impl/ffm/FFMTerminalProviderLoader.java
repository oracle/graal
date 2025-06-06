/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.shadowed.org.jline.terminal.impl.ffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;
import org.graalvm.shadowed.org.jline.terminal.spi.TerminalProvider;

public class FFMTerminalProviderLoader {
    /**
     * Build time flag to disable the FFM provider and avoid pulling FFM related code into the
     * image.
     */
    static final boolean DISABLED = Boolean.getBoolean("org.graalvm.shadowed.org.jline.terminal.ffm.disable");

    public static TerminalProvider load() {
        if (DISABLED) {
            return null;
        }
        return new org.graalvm.shadowed.org.jline.terminal.impl.ffm.FfmTerminalProvider();
    }
}

class FFMTerminalProviderFeature implements Feature {
    private record DowncallDesc(FunctionDescriptor fd, Linker.Option... options) {
    }

    private static StructLayout WIN_COORD_LAYOUT = MemoryLayout.structLayout(ValueLayout.JAVA_SHORT.withName("x"), ValueLayout.JAVA_SHORT.withName("y"));

    // Downcalls from CLibrary.java
    private static DowncallDesc[] getUnixDowncalls() {
        return new DowncallDesc[]{
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), Linker.Option.firstVariadicArg(2)), // ioctl
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)), // isatty
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // tcsetattr
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // tcgetattr
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)), // ttyname_r
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)) // openpty
        };
    }

    // Downcalls from Kernel32
    private static DowncallDesc[] getWindowsDowncalls() {
        return new DowncallDesc[]{
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)), // WaitForSingleObject
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)), // GetStdHandle
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // FormatMessageW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT)), // SetConsoleTextAttribute
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)), // SetConsoleMode
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)), // GetConsoleMode
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // SetConsoleTitleW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, WIN_COORD_LAYOUT)), // SetConsoleCursorPosition
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_CHAR, ValueLayout.JAVA_INT, WIN_COORD_LAYOUT, ValueLayout.ADDRESS)), // FillConsoleOutputCharacterW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT, WIN_COORD_LAYOUT, ValueLayout.ADDRESS)), // FillConsoleOutputAttribute
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)), // WriteConsoleW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // ReadConsoleInputW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // PeekConsoleInputW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)), // GetConsoleScreenBufferInfo
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, WIN_COORD_LAYOUT, ValueLayout.ADDRESS)), // ScrollConsoleScreenBufferW
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT)), // GetLastError
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)), // GetFileType
                        new DowncallDesc(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)), // _get_osfhandle
        };
    }

    private static DowncallDesc[] getDowncalls() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return getWindowsDowncalls();
        }
        return getUnixDowncalls();
    }

    public void duringSetup(DuringSetupAccess access) {
        if (FFMTerminalProviderLoader.DISABLED) {
            return;
        }
        for (DowncallDesc downcall : getDowncalls()) {
            RuntimeForeignAccess.registerForDowncall(downcall.fd(), (Object[]) downcall.options());
        }
    }
}
