/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.VM;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;

import jdk.graal.compiler.word.Word;

@AutomaticallyRegisteredFeature
public class VMFeature implements InternalFeature {

    private NativeLibraries nativeLibraries;
    private static final String STATIC_BINARY_MARKER_SYMBOL_NAME = "__svm_vm_is_static_binary";
    private static final String VERSION_INFO_SYMBOL_NAME = "__svm_version_info";
    private static final String valueSeparator = "=";

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VM.class, new VM(determineVMInfo()));
    }

    protected String determineVMInfo() {
        return getSelectedGCName();
    }

    protected static final String getSelectedGCName() {
        if (SubstrateOptions.useSerialGC()) {
            return "serial gc";
        } else if (SubstrateOptions.useEpsilonGC()) {
            return "epsilon gc";
        } else {
            return "unknown gc";
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (SubstrateOptions.DumpTargetInfo.getValue()) {
            ReportUtils.report("compilation-target information", SubstrateOptions.reportsPath(), "target_info", "txt", out -> {
                out.println("Building image for target platform: " + ImageSingletons.lookup(Platform.class).getClass().getName());
                if (ImageSingletons.contains(CCompilerInvoker.class)) {
                    out.println("Using native toolchain:");
                    ImageSingletons.lookup(CCompilerInvoker.class).compilerInfo.dump(x -> out.println("   " + x));
                }
                out.println("Using CLibrary: " + ImageSingletons.lookup(LibCBase.class).getClass().getName());
            });
        }

        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        nativeLibraries = access.getNativeLibraries();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        CGlobalDataFeature.singleton().registerWithGlobalSymbol(
                        CGlobalDataFactory.createCString(VM.class.getName() + valueSeparator +
                                        ImageSingletons.lookup(VM.class).vendorVersion, VERSION_INFO_SYMBOL_NAME));

        addCGlobalDataString("Target.Platform", ImageSingletons.lookup(Platform.class).getClass().getName());
        addCGlobalDataString("Target.LibC", ImageSingletons.lookup(LibCBase.class).getClass().getName());
        addCGlobalDataString("Java.Version", System.getProperty("java.version"));

        addCGlobalDataString("Target.Libraries", String.join("|", nativeLibraries.getLibraries()));
        addCGlobalDataString("Target.StaticLibraries", nativeLibraries.getStaticLibraries().stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining("|")));
        if (ImageSingletons.contains(CCompilerInvoker.class)) {
            addCGlobalDataString("Target.CCompiler", ImageSingletons.lookup(CCompilerInvoker.class).compilerInfo.toCGlobalDataString());
        }

        if (SubstrateOptions.DumpTargetInfo.getValue()) {
            ReportUtils.report("native-library information", SubstrateOptions.reportsPath(), "native_library_info", "txt", out -> {
                out.println("Static libraries:");
                nativeLibraries.getStaticLibraries().stream().map(ReportUtils::getCWDRelativePath).map(Path::toString).forEach(x -> out.println("   " + x));
                out.println("Other libraries: " + String.join(",", nativeLibraries.getLibraries()));
            });
        }

        if (!Platform.includedIn(InternalPlatform.WINDOWS_BASE.class)) {
            CGlobalData<PointerBase> isStaticBinaryMarker = CGlobalDataFactory.createWord(Word.unsigned(SubstrateOptions.StaticExecutable.getValue() ? 1 : 0), STATIC_BINARY_MARKER_SYMBOL_NAME);
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                /*
                 * GR-55032: currently in layered images we must register this symbol as global so
                 * that it is visible from JvmFuncs.c linked in any layer. In the future we will
                 * ensure JvmFunc.c is linked in the initial layer.
                 */
                CGlobalDataFeature.singleton().registerWithGlobalSymbol(isStaticBinaryMarker);
            } else {
                CGlobalDataFeature.singleton().registerWithGlobalHiddenSymbol(isStaticBinaryMarker);
            }
        }
    }

    private static void addCGlobalDataString(String infoType, String content) {
        String data = VM.class.getName() + "." + infoType + valueSeparator + content;
        String symbolName = "__svm_vm_" + infoType.toLowerCase(Locale.ROOT).replace(".", "_");
        CGlobalDataFeature.singleton().registerWithGlobalSymbol(CGlobalDataFactory.createCString(data, symbolName));
    }
}
