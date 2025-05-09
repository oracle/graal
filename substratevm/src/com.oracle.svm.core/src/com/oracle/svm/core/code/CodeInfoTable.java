/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import static com.oracle.svm.core.deopt.Deoptimizer.Options.LazyDeoptimization;

import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceMapIndex;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.CounterFeature;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.InstalledCode;

/**
 * Provides the main entry points to look up metadata for code, either
 * {@link #getImageCodeCacheForLayer(int) ahead-of-time compiled code in the native image} or
 * {@link CodeInfoTable#getRuntimeCodeCache() code compiled at runtime}.
 * <p>
 * Users of this class must take special care because code can be invalidated at arbitrary times and
 * their metadata can be freed, see notes on {@link CodeInfoAccess}.
 */
public class CodeInfoTable {

    private static CodeInfo imageCodeInfo;

    public static class Options {

        @Option(help = "Count accesses to the image and runtime code info table")//
        public static final HostedOptionKey<Boolean> CodeCacheCounters = new HostedOptionKey<>(false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ImageCodeInfo getCurrentLayerImageCodeCache() {
        return LayeredImageSingletonSupport.singleton().lookup(ImageCodeInfo.class, false, true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static ImageCodeInfo getImageCodeCacheForLayer(int layerNumber) {
        return MultiLayeredImageSingleton.getForLayer(ImageCodeInfo.class, layerNumber);
    }

    @Fold
    public static RuntimeCodeCache getRuntimeCodeCache() {
        return ImageSingletons.lookup(RuntimeCodeCache.class);
    }

    @Uninterruptible(reason = "Executes during isolate creation.")
    public static void prepareImageCodeInfo() {
        // Stored in this class because ImageCodeInfo is immutable
        imageCodeInfo = ImageCodeInfo.prepareCodeInfo();
        assert imageCodeInfo.notEqual(Word.zero());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo getFirstImageCodeInfo() {
        assert imageCodeInfo.notEqual(Word.zero()) : "uninitialized";
        return imageCodeInfo;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo getFirstImageCodeInfo(int layerNumber) {
        return MultiLayeredImageSingleton.getForLayer(ImageCodeInfoStorage.class, layerNumber).getData();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo getImageCodeInfo(CodePointer ip) {
        CodeInfo info = lookupImageCodeInfo(ip);
        VMError.guarantee(info.isNonNull(), "not in image code");
        return info;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo getImageCodeInfo(SharedMethod method) {
        return getImageCodeInfo(method.getImageCodeInfo().getCodeStart());
    }

    public static CodeInfoQueryResult lookupCodeInfoQueryResult(CodeInfo info, CodePointer absoluteIP) {
        counters().lookupCodeInfoCount.inc();
        if (info.isNull()) {
            return null;
        }
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        result.ip = absoluteIP;
        CodeInfoAccess.lookupCodeInfo(info, absoluteIP, result);
        return result;
    }

    public static CodeInfoQueryResult lookupDeoptimizationEntrypoint(CodeInfo info, int deoptOffsetInImage, long encodedBci) {
        counters().lookupDeoptimizationEntrypointCount.inc();
        /* Deoptimization entry points are always in the image, i.e., never compiled at run time. */
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = CodeInfoAccess.lookupDeoptimizationEntrypoint(info, deoptOffsetInImage, encodedBci, result, FrameInfoDecoder.SubstrateConstantAccess);
        if (relativeIP < 0) {
            return null;
        }
        result.ip = CodeInfoAccess.absoluteIP(info, relativeIP);
        return result;
    }

    /** Note that this method is only called for regular frames but not for deoptimized frames. */
    public static void visitObjectReferences(Pointer sp, CodePointer ip, CodeInfo info, ObjectReferenceVisitor visitor) {
        counters().visitObjectReferencesCount.inc();

        /*
         * NOTE: if this code does not execute in a VM operation, it is possible for the visited
         * frame to be deoptimized concurrently, and that one of the references is overwritten with
         * the reference to the DeoptimizedFrame object, before, after, or during visiting it.
         */

        NonmovableArray<Byte> referenceMapEncoding = NonmovableArrays.nullArray();
        long referenceMapIndex = ReferenceMapIndex.NO_REFERENCE_MAP;
        if (info.isNonNull()) {
            referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(info);
            referenceMapIndex = CodeInfoAccess.lookupStackReferenceMapIndex(info, CodeInfoAccess.relativeIP(info, ip));
        }
        if (referenceMapIndex == ReferenceMapIndex.NO_REFERENCE_MAP) {
            throw fatalErrorNoReferenceMap(sp, ip, info);
        }
        CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, visitor, null);
    }

    @Uninterruptible(reason = "Not really uninterruptible, but we are about to fail.", calleeMustBe = false)
    public static RuntimeException fatalErrorNoReferenceMap(Pointer sp, CodePointer ip, CodeInfo info) {
        Log.log().string("ip: ").hex(ip).string("  sp: ").hex(sp).string("  info:");
        CodeInfoAccess.log(info, Log.log()).newline();
        throw VMError.shouldNotReachHere("No reference map information found");
    }

    /**
     * Retrieves the {@link InstalledCode} that contains the provided instruction pointer. Returns
     * {@code null} if the instruction pointer is not within a runtime compile method.
     */
    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    public static SubstrateInstalledCode lookupInstalledCode(CodePointer ip) {
        counters().lookupInstalledCodeCount.inc();
        UntetheredCodeInfo untetheredInfo = lookupCodeInfo(ip);
        if (untetheredInfo.isNull() || UntetheredCodeInfoAccess.isAOTImageCode(untetheredInfo)) {
            return null; // not within a runtime-compiled method
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            return getInstalledCode0(info);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly retrieve InstalledCode.", calleeMustBe = false)
    private static SubstrateInstalledCode getInstalledCode0(CodeInfo info) {
        return RuntimeCodeInfoAccess.getInstalledCode(info);
    }

    public static void invalidateInstalledCode(SubstrateInstalledCode installedCode) {
        InvalidateInstalledCodeOperation vmOp = new InvalidateInstalledCodeOperation(installedCode);
        vmOp.enqueue();
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    private static void invalidateInstalledCodeAtSafepoint(SubstrateInstalledCode installedCode, CodePointer codePointer) {
        /*
         * Don't try to invalidate the code if it was already invalidated earlier. It is essential
         * that we do this check in uninterruptible code because the GC can invalidate code as well.
         */
        if (!installedCode.isAlive()) {
            return;
        }

        /*
         * This invalidation is done at a safepoint and we acquire the tether of the {@link
         * CodeInfo} object. Therefore, it is guaranteed that there is no conflict with the {@link
         * CodeInfo} invalidation/freeing that the GC does because the tether is still reachable.
         */
        UntetheredCodeInfo untetheredInfo = getRuntimeCodeCache().lookupCodeInfo(codePointer);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            assert tether != null : "Invalidation can't be triggered before the code was fully installed.";
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            // Multiple threads could trigger this method - only the first one must do something.
            if (CodeInfoAccess.isAlive(info)) {
                invalidateCodeAtSafepoint0(info);
            }
            // If lazy deoptimization is enabled, the CodeInfo will not be removed immediately.
            if (LazyDeoptimization.getValue()) {
                assert CodeInfoAccess.getState(info) == CodeInfo.STATE_NON_ENTRANT;
            } else {
                assert CodeInfoAccess.getState(info) == CodeInfo.STATE_REMOVED_FROM_CODE_CACHE;
            }
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly retrieve InstalledCode.", calleeMustBe = false)
    private static void invalidateCodeAtSafepoint0(CodeInfo info) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");
        RuntimeCodeCache codeCache = getRuntimeCodeCache();
        codeCache.invalidateMethod(info);
    }

    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called by the GC")
    public static void invalidateNonStackCodeAtSafepoint(CodeInfo info) {
        VMOperation.guaranteeGCInProgress("Must only be called during a GC.");
        RuntimeCodeCache codeCache = getRuntimeCodeCache();
        codeCache.invalidateNonStackMethod(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo lookupImageCodeInfo(CodePointer ip) {
        CodeInfo info = getFirstImageCodeInfo();
        while (info.isNonNull() && !CodeInfoAccess.contains(info, ip)) {
            info = CodeInfoAccess.getNextImageCodeInfo(info);
        }
        return info;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInAOTImageCode(CodePointer ip) {
        return lookupImageCodeInfo(ip).isNonNull();
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo.", callerMustBe = true)
    public static UntetheredCodeInfo lookupCodeInfo(CodePointer ip) {
        counters().lookupCodeInfoCount.inc();
        UntetheredCodeInfo info = lookupImageCodeInfo(ip);
        if (info.isNull()) {
            info = getRuntimeCodeCache().lookupCodeInfo(ip);
        }
        return info;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void tearDown() {
        getRuntimeCodeCache().tearDown();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CodeInfoTableCounters counters() {
        return ImageSingletons.lookup(CodeInfoTableCounters.class);
    }

    private static class InvalidateInstalledCodeOperation extends JavaVMOperation {
        private final SubstrateInstalledCode installedCode;

        InvalidateInstalledCodeOperation(SubstrateInstalledCode installedCode) {
            super(VMOperationInfos.get(InvalidateInstalledCodeOperation.class, "Invalidate code", SystemEffect.SAFEPOINT));
            this.installedCode = installedCode;
        }

        @Override
        protected void operate() {
            counters().invalidateInstalledCodeCount.inc();
            CodePointer codePointer = Word.pointer(installedCode.getAddress());
            invalidateInstalledCodeAtSafepoint(installedCode, codePointer);
        }
    }
}

final class CodeInfoTableCounters {
    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "CodeInfoTable");
    final Counter lookupCodeInfoCount = new Counter(counters, "lookupCodeInfo", "");
    final Counter lookupDeoptimizationEntrypointCount = new Counter(counters, "lookupDeoptimizationEntrypoint", "");
    final Counter visitObjectReferencesCount = new Counter(counters, "visitObjectReferences", "");
    final Counter lookupInstalledCodeCount = new Counter(counters, "lookupInstalledCode", "");
    final Counter invalidateInstalledCodeCount = new Counter(counters, "invalidateInstalledCode", "");
}

@AutomaticallyRegisteredFeature
class CodeInfoFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(CounterFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(CodeInfoTableCounters.class, new CodeInfoTableCounters());
        ImageSingletons.add(CodeInfoDecoderCounters.class, new CodeInfoDecoderCounters());
        ImageSingletons.add(CodeInfoEncoder.Counters.class, new CodeInfoEncoder.Counters());
        ImageSingletons.add(ImageCodeInfo.class, new ImageCodeInfo());
        ImageSingletons.add(RuntimeCodeInfoHistory.class, new RuntimeCodeInfoHistory());
        ImageSingletons.add(RuntimeCodeCache.class, new RuntimeCodeCache());
        ImageSingletons.add(RuntimeCodeInfoMemory.class, new RuntimeCodeInfoMemory());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess config) {
        ImageCodeInfo imageInfo = CodeInfoTable.getCurrentLayerImageCodeCache();
        config.registerAsImmutable(imageInfo);
        config.registerAsImmutable(imageInfo.codeInfoIndex);
        config.registerAsImmutable(imageInfo.codeInfoEncodings);
        config.registerAsImmutable(imageInfo.referenceMapEncoding);
        config.registerAsImmutable(imageInfo.frameInfoEncodings);
        config.registerAsImmutable(imageInfo.objectConstants);
        config.registerAsImmutable(imageInfo.classes);
        config.registerAsImmutable(imageInfo.memberNames);
        config.registerAsImmutable(imageInfo.otherStrings);
        config.registerAsImmutable(imageInfo.methodTable);
    }
}
