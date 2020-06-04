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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.VM;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;

@AutomaticFeature
public class VMFeature implements Feature {

    private NativeLibraries nativeLibraries;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (SubstrateOptions.DumpTargetInfo.getValue()) {
            System.out.println("# Building image for target platform: " + ImageSingletons.lookup(Platform.class).getClass().getName());
            if (ImageSingletons.contains(CCompilerInvoker.class)) {
                System.out.println("# Using native toolchain:");
                ImageSingletons.lookup(CCompilerInvoker.class).compilerInfo.dump(x -> System.out.println("#   " + x));
            }
            System.out.println("# Using CLibrary: " + ImageSingletons.lookup(LibCBase.class).getClass().getName());
        }

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

        addCGlobalDataString("Target.Libraries", String.join("|", nativeLibraries.getLibraries()));
        addCGlobalDataString("Target.StaticLibraries", nativeLibraries.getStaticLibraries().stream()
                        .map(Path::getFileName).map(Path::toString).collect(Collectors.joining("|")));
        if (ImageSingletons.contains(CCompilerInvoker.class)) {
            addCGlobalDataString("Target.CCompiler", ImageSingletons.lookup(CCompilerInvoker.class).compilerInfo.toString());
        }

        if (SubstrateOptions.DumpTargetInfo.getValue()) {
            System.out.println("# Static libraries:");
            Path current = Paths.get(".").toAbsolutePath();
            nativeLibraries.getStaticLibraries().stream().map(current::relativize).map(Path::toString).forEach(x -> System.out.println("#   " + x));
            System.out.println("# Other libraries: " + String.join(",", nativeLibraries.getLibraries()));
        }
    }

    private static void addCGlobalDataString(String infoType, String content) {
        String data = VM.class.getName() + "." + infoType + VM.valueSeparator + content;
        String symbolName = "__svm_vm_" + infoType.toLowerCase().replace(".", "_");
        CGlobalDataFeature.singleton().registerAsAccessedOrGet(CGlobalDataFactory.createCString(data, symbolName));
    }
}
