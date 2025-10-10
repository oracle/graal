/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah.nativelib;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.MuslLibC;
import com.oracle.svm.core.gc.shenandoah.ShenandoahOptions;
import com.oracle.svm.core.heap.ReferenceAccess;

/**
 * Determines which native libraries are included when building a native image that uses Shenandoah.
 */
public class ShenandoahLibraryDependencies implements CContext.Directives {
    @Override
    public boolean isInConfiguration() {
        return SubstrateOptions.useShenandoahGC();
    }

    @Override
    public List<String> getLibraries() {
        boolean useCompressedReferences = ReferenceAccess.singleton().getCompressionShift() > 0;
        String compressedReferenceSuffix = useCompressedReferences ? "-cr" : "-ur";
        String toolchainSuffix = LibCBase.targetLibCIs(MuslLibC.class) ? "-" + LibCBase.singleton().getName() : "";

        ArrayList<String> libraries = new ArrayList<>();
        libraries.add("shenandoahgc" + ShenandoahOptions.getDebugLevel().getLibSuffix() + toolchainSuffix + compressedReferenceSuffix);
        libraries.addAll(List.of("dl", "m", "pthread"));
        libraries.addAll(getCppLibs());

        return libraries;
    }

    private static List<String> getCppLibs() {
        return List.of("stdc++");
    }
}
