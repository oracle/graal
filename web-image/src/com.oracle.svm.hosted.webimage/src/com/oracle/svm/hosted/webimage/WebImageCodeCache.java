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

package com.oracle.svm.hosted.webimage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.codegen.WebImageCompilationResult;
import com.oracle.svm.webimage.reflect.WebImageMetadataAccessor;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This class provides support for building metadata that is necessary for reflection in
 * {@link #layoutMethods(DebugContext, BigBang)}. The remaining methods are unimplemented.
 */
public class WebImageCodeCache extends NativeImageCodeCache {

    public final Map<HostedMethod, WebImageCompilationResult> webImageCompilationResults;
    public final NativeImageHeap nativeImageHeap;

    public WebImageCodeCache(Map<HostedMethod, CompilationResult> compilationResultMap, NativeImageHeap imageHeap) {
        super(compilationResultMap, imageHeap);
        this.webImageCompilationResults = compilationResultMap.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> (WebImageCompilationResult) entry.getValue()));
        this.nativeImageHeap = imageHeap;
    }

    @Override
    public int getCodeCacheSize() {
        return 0;
    }

    @Override
    public int codeSizeFor(HostedMethod method) {
        return 0;
    }

    @Override
    public void layoutMethods(DebugContext debug, BigBang bb) {
    }

    @Override
    public void buildRuntimeMetadata(DebugContext debug, SnippetReflectionProvider snippetReflectionProvider) {
        buildRuntimeMetadata(debug, snippetReflectionProvider, null, null);
    }

    /**
     * We install the fields of {@link com.oracle.svm.core.code.CodeInfoEncoder.Encoders} in a
     * custom {@link ImageSingletons} in order to avoid relying on {@link CodeInfo}. The
     * {@link CodeInfo} object will not be emitted in our image.
     */
    @Override
    protected ImageCodeInfo.HostedImageCodeInfo installCodeInfo(SnippetReflectionProvider snippetReflection, CFunctionPointer firstMethod, UnsignedWord codeSize, CodeInfoEncoder codeInfoEncoder,
                    RuntimeMetadataEncoder runtimeMetadataEncoder, DeadlockWatchdog watchdog) {
        CodeInfoEncoder.Encoders encoders = codeInfoEncoder.getEncoders();
        JavaConstant[] constants = encoders.objectConstants.encodeAll(new JavaConstant[encoders.objectConstants.getLength()]);
        Class<?>[] classes = encoders.classes.encodeAll(new Class<?>[encoders.classes.getLength()]);
        String[] memberNames = encoders.memberNames.encodeAll(new String[encoders.memberNames.getLength()]);
        String[] otherStrings = encoders.otherStrings.encodeAll(new String[encoders.otherStrings.getLength()]);
        var metadataAccessor = (WebImageMetadataAccessor) RuntimeMetadataDecoder.MetadataAccessor.singleton();
        metadataAccessor.installMetadata(constants, classes, memberNames, otherStrings, codeInfoEncoder);
        runtimeMetadataEncoder.encodeAllAndInstall();

        /*
         * Fill ImageCodeInfo with empty arrays. These don't make it into the image, but may still
         * be accessed during the build, causing NPEs if they're not set.
         */
        ImageCodeInfo.HostedImageCodeInfo imageCodeInfo = CodeInfoTable.getCurrentLayerImageCodeCache().getHostedImageCodeInfo();
        NonmovableArray<Byte> emptyBytes = NonmovableArrays.createByteArray(0, NmtCategory.Code);
        CodeInfoAccess.setCodeInfo(imageCodeInfo, emptyBytes, emptyBytes, emptyBytes);
        CodeInfoAccess.setFrameInfo(imageCodeInfo, emptyBytes);
        imageCodeInfo.setMethodTable(NonmovableArrays.createByteArray(0, NmtCategory.Code));
        return null;
    }

    @Override
    protected boolean verifyMethods(DebugContext debug, HostedUniverse hUniverse, CodeInfoEncoder codeInfoEncoder, CodeInfo codeInfo, ConstantAccess constantAccess) {
        return true;
    }

    @Override
    protected void encodeMethod(CodeInfoEncoder codeInfoEncoder, Pair<HostedMethod, CompilationResult> pair) {
        // we do not use the codeInfoEncoder
    }

    @Override
    public void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile) {

    }

    @Override
    public void writeCode(RelocatableBuffer buffer) {

    }

    @Override
    public NativeImage.NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
        return null;
    }

    @Override
    public List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile) {
        return null;
    }
}
