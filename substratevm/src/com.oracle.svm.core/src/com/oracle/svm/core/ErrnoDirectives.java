/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.impl.InternalPlatform;

public class ErrnoDirectives implements CContext.Directives {

    @Override
    public boolean isInConfiguration() {
        return Platform.includedIn(InternalPlatform.LINUX_AND_JNI.class) || Platform.includedIn(InternalPlatform.DARWIN_AND_JNI.class) ||
                        Platform.includedIn(Platform.WINDOWS.class);
    }

    @Override
    public List<String> getHeaderFiles() {
        List<String> result = new ArrayList<>();
        if (Platform.includedIn(InternalPlatform.LINUX_AND_JNI.class) || Platform.includedIn(InternalPlatform.DARWIN_AND_JNI.class)) {
            result.add("<sys/errno.h>");
        } else if (Platform.includedIn(Platform.WINDOWS.class)) {
            result.add("<errno.h>");
        } else {
            throw VMError.shouldNotReachHere("Unsupported OS");
        }
        return result;
    }

    @Override
    public List<String> getMacroDefinitions() {
        if (!Platform.includedIn(Platform.WINDOWS.class)) {
            return Arrays.asList("_GNU_SOURCE", "_LARGEFILE64_SOURCE");
        }
        return Collections.emptyList();
    }
}
