/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jni;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jni.JNILibraryInitializer;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.codegen.CSourceCodeWriter;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
public class JNILibraryLoadFeature implements Feature {

    private static final String WINDOWS_EXTNET_ONLOAD_SYMBOL = JNILibraryInitializer.getOnLoadName("extnet", true);

    private final JNILibraryInitializer jniLibraryInitializer = new JNILibraryInitializer();
    private final Set<String> nativeCallWrapperSymbols = new LinkedHashSet<>();

    private boolean emitNopExtnetOnLoad = false;

    public static JNILibraryLoadFeature singleton() {
        return ImageSingletons.lookup(JNILibraryLoadFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        NativeLibrarySupport.singleton().registerLibraryInitializer(jniLibraryInitializer);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        NativeLibraries nativeLibraries = NativeLibraries.singleton();
        boolean isChanged = jniLibraryInitializer.fillCGlobalDataMap(nativeLibraries.getJniStaticLibraries());
        if (isChanged) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (!ClassRegistries.respectClassLoader()) {
            return;
        }

        nativeCallWrapperSymbols.clear();
        AfterAnalysisAccessImpl accessImpl = (AfterAnalysisAccessImpl) access;
        for (AnalysisMethod method : accessImpl.getUniverse().getMethods()) {
            if (method.isReachable() && method.getWrapped() instanceof JNINativeCallWrapperMethod wrapper && wrapper.isBuiltInFunction()) {
                nativeCallWrapperSymbols.add(wrapper.getShortSymbol());
                nativeCallWrapperSymbols.add(wrapper.getLongSymbol());
            }
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (!ClassRegistries.respectClassLoader()) {
            return;
        }

        NativeLibraries nativeLibraries = NativeLibraries.singleton();
        Set<String> staticBuiltinSymbols = collectStaticBuiltinSymbols(nativeLibraries, nativeLibraries.getJniStaticLibrariesAndDependencies());

        ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
            Path sourceFile = writeStaticBuiltinSymbolTable(linkerInvocation.getTempDirectory(), staticBuiltinSymbols);
            Path objectFile = compileStaticBuiltinSymbolTable(sourceFile, linkerInvocation.getTempDirectory());
            linkerInvocation.addInputFile(firstStaticLibraryIndex(linkerInvocation.getInputFiles(), NativeLibraries.singleton().getStaticLibraries()), objectFile);
            return linkerInvocation;
        });
    }

    private static int firstStaticLibraryIndex(List<Path> inputFiles, Collection<Path> staticLibraries) {
        for (int i = 0; i < inputFiles.size(); i++) {
            if (staticLibraries.contains(inputFiles.get(i))) {
                return i;
            }
        }
        return inputFiles.size();
    }

    private Set<String> collectStaticBuiltinSymbols(NativeLibraries nativeLibraries, Collection<String> staticLibNames) {
        Set<String> staticBuiltinSymbols = new LinkedHashSet<>();
        for (String libName : staticLibNames) {
            staticBuiltinSymbols.addAll(nativeLibraries.getStaticLibrarySymbols(libName));
            if (Platform.includedIn(Platform.WINDOWS.class) && libName.equals("extnet")) {
                emitNopExtnetOnLoad = true;
            }
        }
        // Remove function symbols for which we create wrappers aot.
        staticBuiltinSymbols.removeAll(nativeCallWrapperSymbols);
        return staticBuiltinSymbols;
    }

    private Path writeStaticBuiltinSymbolTable(Path tempDirectory, Set<String> staticBuiltinSymbols) {
        CSourceCodeWriter writer = new CSourceCodeWriter(tempDirectory);
        writer.includeFiles(List.of("<stddef.h>", "<string.h>"));
        writer.appendln();

        for (String symbolName : staticBuiltinSymbols) {
            writer.appendln("extern void " + symbolName + "(void);");
        }

        if (emitNopExtnetOnLoad) {
            writer.appendln();
            writer.appendln("int " + WINDOWS_EXTNET_ONLOAD_SYMBOL + "(void *vm, void *reserved) {");
            writer.appendln("    (void) vm;");
            writer.appendln("    (void) reserved;");
            writer.appendln("    return 0x00010008;");
            writer.appendln("}");
            staticBuiltinSymbols.add(WINDOWS_EXTNET_ONLOAD_SYMBOL);
        }

        writer.appendln();
        writer.appendln("typedef struct {");
        writer.appendln("    const char *name;");
        writer.appendln("    void *address;");
        // GR-76022: turn this into a hash table
        writer.appendln("} svm_builtin_symbol_t;");
        writer.appendln();

        writer.appendln("static const svm_builtin_symbol_t svm_builtin_symbols[] = {");
        for (String symbolName : staticBuiltinSymbols) {
            writer.appendln("    { \"" + symbolName + "\", (void *) " + symbolName + " },");
        }
        writer.appendln("};");
        writer.appendln();

        writer.appendln("void *__svm_find_builtin_symbol(const char *name) {");
        writer.appendln("    for (size_t i = 0; i < sizeof(svm_builtin_symbols) / sizeof(svm_builtin_symbols[0]); i++) {");
        writer.appendln("        if (strcmp(name, svm_builtin_symbols[i].name) == 0) {");
        writer.appendln("            return svm_builtin_symbols[i].address;");
        writer.appendln("        }");
        writer.appendln("    }");
        writer.appendln("    return NULL;");
        writer.appendln("}");

        return writer.writeFile("svm_builtin_symbols.c");
    }

    private static Path compileStaticBuiltinSymbolTable(Path sourceFile, Path tempDirectory) {
        String objectFileName = "svm_builtin_symbols" + ObjectFile.getFilenameSuffix();
        Path objectFile = tempDirectory.resolve(objectFileName);
        List<String> compileOptions = Platform.includedIn(Platform.WINDOWS.class) ? List.of("/c", "/Fo" + objectFile) : List.of("-fPIC", "-c");
        ImageSingletons.lookup(CCompilerInvoker.class).compileAndParseError(false, compileOptions, sourceFile, objectFile, (command, source, line) -> {
            throw new RuntimeException("Unable to compile " + source + " with " + command.command() + ": " + line);
        });
        return objectFile;
    }
}
