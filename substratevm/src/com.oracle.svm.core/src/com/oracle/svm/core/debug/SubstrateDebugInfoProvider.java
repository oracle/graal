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
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateDebugInfoProvider extends SharedDebugInfoProvider {

    private final SharedMethod sharedMethod;
    private final CompilationResult compilation;
    private final long codeAddress;

    public SubstrateDebugInfoProvider(DebugContext debug, SharedMethod sharedMethod, CompilationResult compilation, RuntimeConfiguration runtimeConfiguration, MetaAccessProvider metaAccess,
                    long codeAddress) {
        super(debug, runtimeConfiguration, metaAccess, SubstrateOptions.getRuntimeSourceDestDir());
        this.sharedMethod = sharedMethod;
        this.compilation = compilation;
        this.codeAddress = codeAddress;
    }

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
    public boolean isRuntimeCompilation() {
        return true;
    }

    @Override
    protected Stream<SharedType> typeInfo() {
        // create type infos on demand for compilation
        return Stream.empty();
    }

    @Override
    protected Stream<Pair<SharedMethod, CompilationResult>> codeInfo() {
        return Stream.of(Pair.create(sharedMethod, compilation));
    }

    @Override
    protected Stream<Object> dataInfo() {
        // no data info needed for runtime compilations
        return Stream.empty();
    }

    @Override
    protected long getCodeOffset(@SuppressWarnings("unused") SharedMethod method) {
        return codeAddress;
    }

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

    @Override
    protected TypeEntry createTypeEntry(SharedType type) {
        String typeName = type.toJavaName();
        LoaderEntry loaderEntry = lookupLoaderEntry(type);
        int size = getTypeSize(type);
        long classOffset = -1;
        String loaderName = loaderEntry == null ? uniqueNullStringEntry : loaderEntry.loaderId();
        long typeSignature = getTypeSignature(typeName + loaderName);
        long compressedTypeSignature = useHeapBase ? getTypeSignature(INDIRECT_PREFIX + typeName + loaderName) : typeSignature;

        if (type.isPrimitive()) {
            JavaKind kind = type.getStorageKind();
            return new PrimitiveTypeEntry(typeName, kind == JavaKind.Void ? 0 : kind.getByteCount(), classOffset, typeSignature, kind);
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
                if (isForeignWordType(type)) {
                    // we just need the correct type signatures here
                    return new ClassEntry(typeName, size, classOffset, typeSignature, typeSignature, layoutTypeSignature,
                                    superClass, fileEntry, loaderEntry);
                } else {
                    return new ClassEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                    layoutTypeSignature, superClass, fileEntry, loaderEntry);
                }
            }
        }
    }

    @Override
    protected void processTypeEntry(@SuppressWarnings("unused") SharedType type, @SuppressWarnings("unused") TypeEntry typeEntry) {
        // nothing to do here
    }

    @Override
    public long objectOffset(@SuppressWarnings("unused") JavaConstant constant) {
        return 0;
    }
}
