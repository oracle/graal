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
package com.oracle.svm.core.windows.headers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.util.VMError;

@Platforms(Platform.WINDOWS.class)
public class WindowsDirectives implements CContext.Directives {

    private static final String[] windowsLibs = new String[]{
                    "<windows.h>",
                    "<winsock.h>",
                    "<process.h>",
                    "<stdio.h>",
                    "<stdlib.h>",
                    "<string.h>",
                    "<io.h>",
                    "<math.h>"
    };

    @Override
    public boolean isInConfiguration() {
        return Platform.includedIn(Platform.WINDOWS.class);
    }

    @Override
    public List<String> getHeaderFiles() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return new ArrayList<>(Arrays.asList(windowsLibs));
        } else {
            throw VMError.shouldNotReachHere("Unsupported OS");
        }
    }

    @Override
    public List<String> getMacroDefinitions() {
        return Arrays.asList("_WIN64");
    }
}
