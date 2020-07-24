/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.serviceprovider.BufferUtil;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.HostedPatcher;
import com.oracle.svm.hosted.image.NativeBootImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.Reference;

public class LIRNativeImageCodeCache extends NativeImageCodeCache {

    private static final byte CODE_FILLER_BYTE = (byte) 0xCC;

    private int codeCacheSize;

    public LIRNativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap) {
        super(compilations, imageHeap);
    }

    @Override
    public int getCodeCacheSize() {
        assert codeCacheSize > 0;
        return codeCacheSize;
    }

    @SuppressWarnings("try")
    @Override
    public void layoutMethods(DebugContext debug, String imageName, BigBang bb, ForkJoinPool threadPool) {

        try (Indent indent = debug.logAndIndent("layout methods")) {

            // Assign a location to all methods.
            assert codeCacheSize == 0;
            HostedMethod firstMethod = null;
            for (Entry<HostedMethod, CompilationResult> entry : compilations.entrySet()) {

                HostedMethod method = entry.getKey();
                if (firstMethod == null) {
                    firstMethod = method;
                }
                CompilationResult compilation = entry.getValue();
                compilationsByStart.put(codeCacheSize, compilation);
                method.setCodeAddressOffset(codeCacheSize);
                codeCacheSize = NumUtil.roundUp(codeCacheSize + compilation.getTargetCodeSize(), SubstrateOptions.codeAlignment());
            }

            buildRuntimeMetadata(MethodPointer.factory(firstMethod), WordFactory.unsigned(codeCacheSize));
        }
    }

    /**
     * Patch references from code to other code and constant data. Generate relocation information
     * in the process. More patching can be done, and correspondingly fewer relocation records
     * generated, if the caller passes a non-null rodataDisplacementFromText.
     *
     * @param relocs a relocation map
     */
    @Override
    @SuppressWarnings("try")
    public void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile) {

        /*
         * Patch instructions which reference code or data by address.
         *
         * Note that the image we write happens to be naturally position-independent on x86-64,
         * since both code and data references are PC-relative.
         *
         * So not only can we definitively fix up the all code--code and code--data references as
         * soon as we have assigned all our addresses, but also, the resulting blob can be loaded at
         * any address without relocation (and therefore potentially shared between many processes).
         * (This is true for shared library output only, not relocatable code.)
         *
         * These properties may change. Once the code includes references to external symbols, we
         * will either no longer have a position-independent image (if we stick with the current
         * load-time relocation approach) or will require us to implement a PLT (for
         * {code,data}->code references) and GOT (for code->data references).
         *
         * Splitting text from rodata is straightforward when generating shared libraries or
         * executables, since even in the case where the loader has to pick a different virtual
         * address range than the one preassigned in the object file, it will preserve the offsets
         * between the vaddrs. So, if we're generating a shared library or executable (i.e.
         * something with vaddrs), we always know the offset of our data from our code (and
         * vice-versa). BUT if we're generating relocatable code, we don't know that yet. In that
         * case, the caller will pass a null rodataDisplacecmentFromText, and we behave accordingly
         * by generating extra relocation records.
         */

        // in each compilation result...
        for (Entry<HostedMethod, CompilationResult> entry : compilations.entrySet()) {
            HostedMethod method = entry.getKey();
            CompilationResult compilation = entry.getValue();

            // the codecache-relative offset of the compilation
            int compStart = method.getCodeAddressOffset();

            // Build an index of PatchingAnnoations
            Map<Integer, HostedPatcher> patches = new HashMap<>();
            for (CodeAnnotation codeAnnotation : compilation.getCodeAnnotations()) {
                if (codeAnnotation instanceof HostedPatcher) {
                    patches.put(codeAnnotation.getPosition(), (HostedPatcher) codeAnnotation);
                }
            }
            // ... patch direct call sites.
            for (Infopoint infopoint : compilation.getInfopoints()) {
                if (infopoint instanceof Call && ((Call) infopoint).direct) {
                    Call call = (Call) infopoint;

                    // NOTE that for the moment, we don't make static calls to external
                    // (e.g. native) functions. So every static call site has a target
                    // which is also in the code cache (a.k.a. a section-local call).
                    // This will change, and we will have to case-split here... but not yet.
                    int callTargetStart = ((HostedMethod) call.target).getCodeAddressOffset();

                    // Patch a PC-relative call.
                    // This code handles the case of section-local calls only.
                    int pcDisplacement = callTargetStart - (compStart + call.pcOffset);
                    patches.get(call.pcOffset).patch(call.pcOffset, pcDisplacement, compilation.getTargetCode());
                }
            }
            for (DataPatch dataPatch : compilation.getDataPatches()) {
                Reference ref = dataPatch.reference;
                /*
                 * Constants are allocated offsets in a separate space, which can be emitted as
                 * read-only (.rodata) section.
                 */
                patches.get(dataPatch.pcOffset).relocate(ref, relocs, compStart);
            }
            try (DebugContext.Scope ds = debug.scope("After Patching", method.asJavaMethod())) {
                debug.dump(DebugContext.BASIC_LEVEL, compilation, "After patching");
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public void writeCode(RelocatableBuffer buffer) {
        ByteBuffer bufferBytes = buffer.getByteBuffer();
        int startPos = bufferBytes.position();
        /*
         * Compilation start offsets are relative to the beginning of the code cache (since the heap
         * size is not fixed at the time they are computed). This is just startPos, i.e. we start
         * emitting the code wherever the buffer is positioned when we're called.
         */
        for (Entry<HostedMethod, CompilationResult> entry : compilations.entrySet()) {
            HostedMethod method = entry.getKey();
            CompilationResult compilation = entry.getValue();

            BufferUtil.asBaseBuffer(bufferBytes).position(startPos + method.getCodeAddressOffset());
            int codeSize = compilation.getTargetCodeSize();
            bufferBytes.put(compilation.getTargetCode(), 0, codeSize);

            for (int i = codeSize; i < NumUtil.roundUp(codeSize, SubstrateOptions.codeAlignment()); i++) {
                bufferBytes.put(CODE_FILLER_BYTE);
            }
        }
        BufferUtil.asBaseBuffer(bufferBytes).position(startPos);
    }

    @Override
    public NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
        return new NativeTextSectionImpl(buffer, objectFile, codeCache) {
            @Override
            protected void defineMethodSymbol(String name, boolean global, ObjectFile.Element section, HostedMethod method, CompilationResult result) {
                final int size = result == null ? 0 : result.getTargetCodeSize();
                objectFile.createDefinedSymbol(name, section, method.getCodeAddressOffset(), size, true, global);
            }
        };
    }

    @Override
    public List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile, boolean onlyGlobal) {
        Stream<ObjectFile.Symbol> stream = StreamSupport.stream(objectFile.getSymbolTable().spliterator(), false);
        if (onlyGlobal) {
            stream = stream.filter(ObjectFile.Symbol::isGlobal);
        }
        return stream.filter(ObjectFile.Symbol::isDefined).collect(Collectors.toList());
    }
}
