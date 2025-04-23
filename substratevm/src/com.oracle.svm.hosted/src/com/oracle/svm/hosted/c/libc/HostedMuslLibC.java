/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.libc;

import java.util.Collections;
import java.util.List;

import com.oracle.svm.core.c.libc.MuslLibC;
import com.oracle.svm.hosted.image.AbstractImage;

public class HostedMuslLibC extends MuslLibC implements HostedLibCBase {
    @Override
    public List<String> getAdditionalQueryCodeCompilerOptions() {
        /* Avoid the dependency to muslc for builds cross compiling to muslc. */
        return isCrossCompiling()
                        ? Collections.singletonList("--static")
                        : Collections.emptyList();
    }

    @Override
    public String getTargetCompiler() {
        return isCrossCompiling() ? "x86_64-linux-musl-gcc" : "gcc";
    }

    @Override
    public boolean requiresLibCSpecificStaticJDKLibraries() {
        return isCrossCompiling();
    }

    @Override
    public List<String> getAdditionalLinkerOptions(AbstractImage.NativeImageKind imageKind) {
        if (imageKind != AbstractImage.NativeImageKind.EXECUTABLE) {
            return List.of();
        }
        return List.of("-no-pie");
    }

    private static boolean isCrossCompiling() {
        return !"musl".equals(System.getProperty("substratevm.HostLibC"));
    }
}
