/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi.libffi;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.c.ProjectHeaderFile;
import com.oracle.svm.truffle.nfi.TruffleNFIFeature;

public class LibFFIHeaderDirectives implements CContext.Directives {

    @Override
    public boolean isInConfiguration() {
        return ImageSingletons.contains(TruffleNFIFeature.class);
    }

    @Override
    public List<String> getHeaderFiles() {
        return Collections.singletonList("<svm_libffi.h>");
    }

    private static String basePath() {
        /* Find location of library directory based on known header name */
        String libffiHeader = ProjectHeaderFile.resolve("com.oracle.svm.libffi", "include/svm_libffi.h");
        File libffiHeaderPath = new File(libffiHeader.substring(1));

        return libffiHeaderPath.getParentFile().getParent();
    }

    private static String multitargetSuffix() {
        String os = OS.getCurrent().asPackageName();
        String arch = SubstrateUtil.getArchitectureName();
        String libc = OS.LINUX.isCurrent() ? SubstrateOptions.UseLibC.getValue() : "default";

        return os + "-" + arch + File.separator + libc;
    }

    @Override
    public List<String> getOptions() {
        /* Add base and target specific include directories */
        Function<String, String> bp = multitarget -> "-I" + basePath() + File.separator + multitarget + "include";
        return List.of(bp.apply(""), bp.apply(multitargetSuffix() + File.separator));
    }

    @Override
    public List<String> getLibraryPaths() {
        /* Add multitarget style path */
        return List.of(basePath() + File.separator + multitargetSuffix());
    }
}
