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
package com.oracle.svm.hosted;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.VM;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.NativeLibraries;

@AutomaticFeature
public class VMFeature implements Feature {

    private NativeLibraries nativeLibraries;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        String fieldName = "VERSION_INFO";
        try {
            Field declaredField = VM.class.getDeclaredField(fieldName);
            access.registerAsRead(access.getMetaAccess().lookupJavaField(declaredField));
        } catch (NoSuchFieldException e) {
            VMError.shouldNotReachHere(VM.class.getName() + " should have field " + fieldName);
        }

        nativeLibraries = access.getNativeLibraries();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        addCGlobalDataString("Target.Platform", ImageSingletons.lookup(Platform.class).getClass().getName());
        addCGlobalDataString("Target.LibC", ImageSingletons.lookup(LibCBase.class).getClass().getName());

        String delimiter = File.pathSeparator;
        addCGlobalDataString("Target.Libraries", String.join(delimiter, nativeLibraries.getLibraries()));
        addCGlobalDataString("Target.StaticLibraries", nativeLibraries.getStaticLibraries().stream()
                        .map(Path::getFileName).map(Path::toString).collect(Collectors.joining(delimiter)));

        /* TODO Add native toolchain info */
    }

    private static void addCGlobalDataString(String infoType, String content) {
        String data = VM.class.getName() + "." + infoType + VM.valueSeparator + content;
        String symbolName = "__svm_vm_" + infoType.toLowerCase().replace(".", "_");
        CGlobalDataFeature.singleton().registerAsAccessedOrGet(CGlobalDataFactory.createCString(data, symbolName));
    }
}
