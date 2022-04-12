/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libjavavm.jniapi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.c.CContext;

import com.oracle.truffle.espresso.libjavavm.OS;

public class JNIHeaderDirectives implements CContext.Directives {

    private static Path jdkIncludeDir() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path includeFolder = javaHome.resolve("include");
        if (!Files.exists(includeFolder) && javaHome.endsWith("jre")) {
            // try parent folder for jdk8 style jdk/jre
            Path parent = javaHome.getParent();
            if (parent != null) {
                javaHome = parent;
            }
        }
        includeFolder = javaHome.resolve("include");
        if (!Files.exists(includeFolder)) {
            throw new IllegalStateException("Cannot find 'include' folder in JDK.");
        }
        return includeFolder;
    }

    @Override
    public List<String> getHeaderFiles() {
        return Collections.singletonList("\"" + jdkIncludeDir().resolve("jni.h") + "\"");
    }

    @Override
    public List<String> getOptions() {
        return Collections.singletonList("-I" + jdkIncludeDir().resolve(OS.isWindows() ? "win32" : OS.getCurrent().name().toLowerCase(Locale.ROOT)));
    }
}
