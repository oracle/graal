/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.debug;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;

import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.option.RuntimeOptionKey;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements the {@link com.oracle.objectfile.debuginfo.DebugInfoProvider DebugInfoProvider}
 * interface based on the {@code SharedDebugInfoProvider} to handle run-time compiled methods.
 *
 * <p>
 * For each run-time compilation, one {@code SubstrateDebugInfoProvider} is created and the debug
 * info for the compiled method is installed. As type information is already available in the native
 * image's debug info, the {@code SubstrateDebugInfoProvider} just produces as little information as
 * needed and reuses debug info from the native image. Therefore, for type entries the
 * {@code SubstrateDebugInfoProvider} just creates stubs that contain the type signature, which can
 * then be resolved by the debugger.
 */
public class SubstrateDebugInfoProvider extends SharedDebugInfoProvider {

    public static class Options {
        @Option(help = "Directory where Java source-files will be placed for the debugger")//
        public static final RuntimeOptionKey<String> RuntimeSourceDestDir = new RuntimeOptionKey<>(null, RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates);

        public static Path getRuntimeSourceDestDir() {
            String sourceDestDir = RuntimeSourceDestDir.getValue();
            /*
             * If not set, use source cache root from the native image debug info. This makes sure
             * the cachePath is the same as in the native image debug info.
             */
            return sourceDestDir != null ? Path.of(sourceDestDir) : getHostedSourceCacheRoot();
        }
    }

    @Fold
    static Path getHostedSourceCacheRoot() {
        return SubstrateOptions.getDebugInfoSourceCacheRoot();
    }

    private final SharedMethod sharedMethod;
    private final CompilationResult compilation;
    private final long codeAddress;

    public SubstrateDebugInfoProvider(DebugContext debug, SharedMethod sharedMethod, CompilationResult compilation, RuntimeConfiguration runtimeConfiguration, MetaAccessProvider metaAccess,
                    long codeAddress) {
        super(debug, runtimeConfiguration, metaAccess);
        this.sharedMethod = sharedMethod;
        this.compilation = compilation;
        this.codeAddress = codeAddress;
    }

    /**
     * Create a compilation unit name from the {@link CompilationResult} or the {@link SharedMethod}
     * the debug info is produced for.
     * 
     * @return the name of the compilation unit in the debug info
     */
    public String getCompilationName() {
        String name = null;
        if (compilation != null) {
            name = compilation.getName();
        }
        if ((name == null || name.isEmpty()) && sharedMethod != null) {
            name = sharedMethod.format("%h.%n");
        }
        if (name == null || name.isEmpty()) {
            name = "UnnamedCompilation";
        }
        return name + "@0x" + Long.toHexString(codeAddress);
    }

    @Override
    public String cachePath() {
        return Options.getRuntimeSourceDestDir().toString();
    }

    @Override
    public boolean isRuntimeCompilation() {
        return true;
    }

    /**
     * Returns an empty stream, because there are no types to stream here. All types needed for the
     * run-time debug info are installed when needed for providing debug info of the compilation.
     * 
     * @return an empty stream
     */
    @Override
    protected Stream<SharedType> typeInfo() {
        // create type infos on demand for compilation
        return Stream.empty();
    }

    /**
     * Provides the single compilation with its corresponding method as code info for this object
     * file. All the debug info for the object file is produced based on this compilation and
     * method.
     * 
     * @return a stream containing the run-time compilation result and method
     */
    @Override
    protected Stream<Pair<SharedMethod, CompilationResult>> codeInfo() {
        return Stream.of(Pair.create(sharedMethod, compilation));
    }

    /**
     * Returns an empty stream, no any additional data is handled for run-time compilations.
     * 
     * @return an empty stream
     */
    @Override
    protected Stream<Object> dataInfo() {
        // no data info needed for run-time compilations
        return Stream.empty();
    }

    @Override
    protected long getCodeOffset(@SuppressWarnings("unused") SharedMethod method) {
        // use the code offset from the compilation
        return codeAddress;
    }

    /**
     * Fetches the package name from the types hub and the types source file name and produces a
     * file name with that. There is no guarantee that the source file is at the location of the
     * file entry, but it is the best guess we can make at run-time.
     * 
     * @param type the given {@code ResolvedJavaType}
     * @return the {@code FileEntry} of the type
     */
    @Override
    public FileEntry lookupFileEntry(ResolvedJavaType type) {
        if (type instanceof SharedType sharedType) {
            String[] packageElements = SubstrateUtil.split(sharedType.getHub().getPackageName(), ".");
            String fileName = sharedType.getSourceFileName();
            if (fileName != null && !fileName.isEmpty()) {
                Path filePath = FileSystems.getDefault().getPath("", packageElements).resolve(fileName);
                return lookupFileEntry(filePath);
            }
        }
        return super.lookupFileEntry(type);
    }

    private static int getTypeSize(SharedType type) {
        if (type.isPrimitive()) {
            JavaKind javaKind = type.getStorageKind();
            return (javaKind == JavaKind.Void ? 0 : javaKind.getByteCount());
        } else if (type.isArray()) {
            SharedType componentType = (SharedType) type.getComponentType();
            return getObjectLayout().getArrayBaseOffset(componentType.getStorageKind());
        } else if (type.isInterface() || type.isInstanceClass()) {
            return getObjectLayout().getFirstFieldOffset();
        } else {
            return 0;
        }
    }

    /**
     * Creates a {@code TypeEntry} for use in object files produced for run-time compilations.
     * 
     * <p>
     * To avoid duplicating debug info, this mainly produces the {@link #getTypeSignature type
     * signatures} to link the types to type entries produced at native image build time. Connecting
     * the run-time compiled type entry with the native image's type entry is left for the debugger.
     * This allows the reuse of type information from the native image, where we have more
     * information available to produce debug info.
     * 
     * @param type the {@code SharedType} to process
     * @return a {@code TypeEntry} for the type
     */
    @Override
    protected TypeEntry createTypeEntry(SharedType type) {
        String typeName = type.toJavaName();
        LoaderEntry loaderEntry = lookupLoaderEntry(type);
        int size = getTypeSize(type);
        long classOffset = -1;
        String loaderName = loaderEntry.loaderId();
        long typeSignature = getTypeSignature(typeName + loaderName);
        long compressedTypeSignature = useHeapBase ? getTypeSignature(INDIRECT_PREFIX + typeName + loaderName) : typeSignature;

        if (type.isPrimitive()) {
            JavaKind kind = type.getStorageKind();
            return new PrimitiveTypeEntry(typeName, size, classOffset, typeSignature, kind);
        } else {
            // otherwise we have a structured type
            long layoutTypeSignature = getTypeSignature(LAYOUT_PREFIX + typeName + loaderName);
            if (type.isArray()) {
                TypeEntry elementTypeEntry = lookupTypeEntry((SharedType) type.getComponentType());
                return new ArrayTypeEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                layoutTypeSignature, elementTypeEntry, loaderEntry);
            } else {
                // otherwise this is a class entry
                ClassEntry superClass = type.getSuperclass() == null ? null : (ClassEntry) lookupTypeEntry((SharedType) type.getSuperclass());
                FileEntry fileEntry = lookupFileEntry(type);
                // try to get an already generated version of this type
                TypeEntry typeEntry = SubstrateDebugTypeEntrySupport.singleton().getTypeEntry(typeSignature);

                if (typeEntry != null) {
                    // this must be a foreign type (struct, pointer, or primitive)
                    if (typeEntry instanceof PointerToTypeEntry pointerToTypeEntry && pointerToTypeEntry.getPointerTo() == null) {
                        // fix-up void pointers
                        pointerToTypeEntry.setPointerTo(lookupTypeEntry(voidType));
                    }
                    return typeEntry;
                } else {
                    return new ClassEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                    layoutTypeSignature, superClass, fileEntry, loaderEntry);
                }
            }
        }
    }

    /**
     * The run-time debug info relies on type entries in the native image. In
     * {@link #createTypeEntry} we just produce dummy types that hold just enough information to
     * connect them to the types in the native image. Therefore, there is nothing to do for
     * processing types at run-time.
     *
     * @param type the {@code SharedType} of the type entry
     * @param typeEntry the {@code TypeEntry} to process
     */
    @Override
    protected void processTypeEntry(@SuppressWarnings("unused") SharedType type, @SuppressWarnings("unused") TypeEntry typeEntry) {
        // nothing to do here
    }

    @Override
    public long objectOffset(@SuppressWarnings("unused") JavaConstant constant) {
        return -1;
    }
}
