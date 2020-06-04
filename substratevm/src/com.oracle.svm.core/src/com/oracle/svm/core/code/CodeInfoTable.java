/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.CounterFeature;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;

/**
 * Provides the main entry points to look up metadata for code, either {@link #getImageCodeCache()
 * ahead-of-time compiled code in the native image} or {@link CodeInfoTable#getRuntimeCodeCache()
 * code compiled at runtime}.
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

    @Fold
    public static ImageCodeInfo getImageCodeCache() {
        return ImageSingletons.lookup(ImageCodeInfo.class);
    }

    @Fold
    public static RuntimeCodeCache getRuntimeCodeCache() {
        return ImageSingletons.lookup(RuntimeCodeCache.class);
    }

    @Uninterruptible(reason = "Executes during isolate creation.")
    public static void prepareImageCodeInfo() {
        // Stored in this class because ImageCodeInfo is immutable
        imageCodeInfo = getImageCodeCache().prepareCodeInfo();
        assert imageCodeInfo.notEqual(WordFactory.zero());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo getImageCodeInfo() {
        assert imageCodeInfo.notEqual(WordFactory.zero()) : "uninitialized";
        return imageCodeInfo;
    }

    public static CodeInfoQueryResult lookupCodeInfoQueryResult(CodeInfo info, CodePointer absoluteIP) {
        counters().lookupCodeInfoCount.inc();
        if (info.isNull()) {
            return null;
        }
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        result.ip = absoluteIP;
        CodeInfoAccess.lookupCodeInfo(info, CodeInfoAccess.relativeIP(info, absoluteIP), result);
        return result;
    }

    public static CodeInfoQueryResult lookupDeoptimizationEntrypoint(int deoptOffsetInImage, long encodedBci) {
        counters().lookupDeoptimizationEntrypointCount.inc();
        /* Deoptimization entry points are always in the image, i.e., never compiled at run time. */
        CodeInfo info = getImageCodeInfo();
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = CodeInfoAccess.lookupDeoptimizationEntrypoint(info, deoptOffsetInImage, encodedBci, result);
        if (relativeIP < 0) {
            return null;
        }
        result.ip = CodeInfoAccess.absoluteIP(info, relativeIP);
        return result;
    }

    public static boolean visitObjectReferences(Pointer sp, CodePointer ip, CodeInfo info, DeoptimizedFrame deoptimizedFrame, ObjectReferenceVisitor visitor) {
        counters().visitObjectReferencesCount.inc();

        if (deoptimizedFrame != null) {
            /*
             * It is a deoptimized frame. The DeoptimizedFrame object is stored in the frame, but it
             * is pinned so we do not have to do anything.
             */
            return true;
        }

        /*
         * NOTE: if this code does not execute in a VM operation, it is possible for the visited
         * frame to be deoptimized concurrently, and that one of the references is overwritten with
         * the reference to the DeoptimizedFrame object, before, after, or during visiting it.
         */

        NonmovableArray<Byte> referenceMapEncoding = NonmovableArrays.nullArray();
        long referenceMapIndex = CodeInfoQueryResult.NO_REFERENCE_MAP;
        if (info.isNonNull()) {
            referenceMapEncoding = CodeInfoAccess.getReferenceMapEncoding(info);
            referenceMapIndex = CodeInfoAccess.lookupReferenceMapIndex(info, CodeInfoAccess.relativeIP(info, ip));
        }
        if (referenceMapIndex == CodeInfoQueryResult.NO_REFERENCE_MAP) {
            throw reportNoReferenceMap(sp, ip, info);
        }
        return CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, visitor);
    }

    public static RuntimeException reportNoReferenceMap(Pointer sp, CodePointer ip, CodeInfo info) {
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
        if (untetheredInfo.isNull() || untetheredInfo.equal(getImageCodeInfo())) {
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
        /* Captures "installedCode" for the VMOperation. */
        JavaVMOperation.enqueueBlockingSafepoint("CodeInfoTable.invalidateInstalledCode", () -> {
            counters().invalidateInstalledCodeCount.inc();
            if (installedCode.isValid()) {
                invalidateInstalledCodeAtSafepoint(WordFactory.pointer(installedCode.getAddress()));
            }
        });
    }

    /**
     * This invalidation is done at a safepoint and we acquire the tether of the {@link CodeInfo}
     * object. Therefore, it is guaranteed that there is no conflict with the {@link CodeInfo}
     * invalidation/freeing that the GC does because the tether is still reachable.
     */
    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    private static void invalidateInstalledCodeAtSafepoint(CodePointer codePointer) {
        UntetheredCodeInfo untetheredInfo = getRuntimeCodeCache().lookupCodeInfo(codePointer);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            assert tether != null : "Invalidation can't be triggered before the code was fully installed.";
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            // Multiple threads could trigger this method - only the first one must do something.
            if (CodeInfoAccess.getState(info) == CodeInfo.STATE_CODE_CONSTANTS_LIVE) {
                invalidateCodeAtSafepoint0(info);
            }
            assert CodeInfoAccess.getState(info) == CodeInfo.STATE_PARTIALLY_FREED;
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly retrieve InstalledCode.", calleeMustBe = false)
    private static void invalidateCodeAtSafepoint0(CodeInfo info) {
        invalidateCodeAtSafepoint(info);
    }

    private static void invalidateCodeAtSafepoint(CodeInfo info) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");
        RuntimeCodeCache codeCache = getRuntimeCodeCache();
        long num = codeCache.logMethodOperation(info, RuntimeCodeCache.INFO_INVALIDATE);
        codeCache.invalidateMethod(info);
        codeCache.logMethodOperationEnd(num);
    }

    public static void invalidateNonStackCodeAtSafepoint(CodeInfo info) {
        VMOperation.guaranteeGCInProgress("Must only be called during a GC.");
        RuntimeCodeCache codeCache = getRuntimeCodeCache();
        long num = codeCache.logMethodOperation(info, RuntimeCodeCache.INFO_INVALIDATE);
        codeCache.invalidateNonStackMethod(info);
        codeCache.logMethodOperationEnd(num);
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo.", callerMustBe = true)
    public static UntetheredCodeInfo lookupCodeInfo(CodePointer ip) {
        counters().lookupCodeInfoCount.inc();
        if (CodeInfoAccess.contains(getImageCodeInfo(), ip)) {
            return getImageCodeInfo();
        } else {
            return getRuntimeCodeCache().lookupCodeInfo(ip);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void tearDown() {
        getRuntimeCodeCache().tearDown();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CodeInfoTableCounters counters() {
        return ImageSingletons.lookup(CodeInfoTableCounters.class);
    }
}

class CodeInfoTableCounters {
    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "CodeInfoTable");
    final Counter lookupCodeInfoCount = new Counter(counters, "lookupCodeInfo", "");
    final Counter lookupDeoptimizationEntrypointCount = new Counter(counters, "lookupDeoptimizationEntrypoint", "");
    final Counter visitObjectReferencesCount = new Counter(counters, "visitObjectReferences", "");
    final Counter lookupInstalledCodeCount = new Counter(counters, "lookupInstalledCode", "");
    final Counter invalidateInstalledCodeCount = new Counter(counters, "invalidateInstalledCode", "");
}

@AutomaticFeature
class CodeInfoFeature implements Feature {
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
        ImageSingletons.add(RuntimeCodeCache.class, new RuntimeCodeCache());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess config) {
        ImageCodeInfo imageInfo = CodeInfoTable.getImageCodeCache();
        config.registerAsImmutable(imageInfo);
        config.registerAsImmutable(imageInfo.codeInfoIndex);
        config.registerAsImmutable(imageInfo.codeInfoEncodings);
        config.registerAsImmutable(imageInfo.referenceMapEncoding);
        config.registerAsImmutable(imageInfo.frameInfoEncodings);
        config.registerAsImmutable(imageInfo.frameInfoObjectConstants);
        config.registerAsImmutable(imageInfo.frameInfoSourceClasses);
        config.registerAsImmutable(imageInfo.frameInfoSourceMethodNames);
        config.registerAsImmutable(imageInfo.frameInfoNames);
    }
}
