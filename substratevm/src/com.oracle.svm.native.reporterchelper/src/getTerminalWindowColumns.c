/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include <jni.h>

#if defined(__linux__) || defined(__APPLE__)

#include <unistd.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/resource.h>

JNIEXPORT jint JNICALL Java_com_oracle_svm_hosted_ProgressReporterCHelper_getTerminalWindowColumns0(void *env, void * ignored) {
    struct winsize w;
    ioctl(STDOUT_FILENO, TIOCGWINSZ, &w);
    return w.ws_col;
}

#elif defined(_WIN64)

#include <windows.h>

JNIEXPORT jint JNICALL Java_com_oracle_svm_hosted_ProgressReporterCHelper_getTerminalWindowColumns0(void *env, void * ignored) {
    CONSOLE_SCREEN_BUFFER_INFO c;
    GetConsoleScreenBufferInfo(GetStdHandle(STD_OUTPUT_HANDLE), &c);
    return c.srWindow.Right - c.srWindow.Left + 1;
}

#endif
