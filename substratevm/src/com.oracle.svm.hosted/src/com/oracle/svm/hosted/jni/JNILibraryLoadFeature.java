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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jni.JNILibraryInitializer;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CSourceCodeWriter;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
public class JNILibraryLoadFeature implements Feature {

    private final JNILibraryInitializer jniLibraryInitializer = new JNILibraryInitializer();
    private final Set<String> staticBuiltinSymbols = new LinkedHashSet<>();

    @Override
    public void duringSetup(DuringSetupAccess access) {
        NativeLibrarySupport.singleton().registerLibraryInitializer(jniLibraryInitializer);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        NativeLibraries nativeLibraries = NativeLibraries.singleton();
        boolean isChanged = jniLibraryInitializer.fillCGlobalDataMap(nativeLibraries.getJniStaticLibraries());
        if (needsStaticBuiltinSymbolTable()) {
            collectStaticBuiltinSymbols(nativeLibraries, nativeLibraries.getJniStaticLibrariesAndDependencies());
        }
        if (isChanged) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (!needsStaticBuiltinSymbolTable() || staticBuiltinSymbols.isEmpty()) {
            return;
        }

        ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
            Path sourceFile = writeStaticBuiltinSymbolTable(linkerInvocation.getTempDirectory());
            linkerInvocation.addInputFile(sourceFile);
            return linkerInvocation;
        });
    }

    private static boolean needsStaticBuiltinSymbolTable() {
        return SubstrateOptions.StaticExecutable.getValue() && Platform.includedIn(Platform.LINUX.class) && ClassRegistries.respectClassLoader();
    }

    private void collectStaticBuiltinSymbols(NativeLibraries nativeLibraries, Collection<String> staticLibNames) {
        for (String libName : staticLibNames) {
            staticBuiltinSymbols.addAll(nativeLibraries.getStaticLibrarySymbols(libName));
        }
    }

    private Path writeStaticBuiltinSymbolTable(Path tempDirectory) {
        CSourceCodeWriter writer = new CSourceCodeWriter(tempDirectory);
        writer.includeFiles(List.of("<stddef.h>", "<string.h>"));
        writer.appendln();

        for (String symbolName : staticBuiltinSymbols) {
            writer.appendln("extern void " + symbolName + "(void);");
        }

        writer.appendln();
        writer.appendln("typedef struct {");
        writer.appendln("    const char *name;");
        writer.appendln("    void *address;");
        // TODO GR-75585: turn this into a hash table
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
}
