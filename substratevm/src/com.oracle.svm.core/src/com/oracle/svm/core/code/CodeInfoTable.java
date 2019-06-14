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

import com.oracle.svm.core.annotate.AlwaysInline;
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
 * their metadata can be freed, see notes on {@link CodeInfoAccessor}.
 */
public class CodeInfoTable {

    public static class Options {

        @Option(help = "Count accesses to the image and runtime code info table")//
        public static final HostedOptionKey<Boolean> CodeCacheCounters = new HostedOptionKey<>(false);
    }

    @Fold
    public static ImageCodeInfo getImageCodeCache() {
        return ImageSingletons.lookup(ImageCodeInfo.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfoAccessor getImageCodeInfoAccessor() {
        return getImageCodeCache().getAccessor();
    }

    @Fold
    public static RuntimeCodeInfo getRuntimeCodeCache() {
        return ImageSingletons.lookup(RuntimeCodeInfo.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfoAccessor getRuntimeCodeInfoAccessor() {
        return getRuntimeCodeCache().getAccessor();
    }

    public static CodeInfoQueryResult lookupCodeInfoQueryResult(CodeInfoAccessor accessor, CodeInfoHandle handle, CodePointer absoluteIP) {
        counters().lookupCodeInfoCount.inc();
        if (accessor.isNone(handle)) {
            return null;
        }
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        result.ip = absoluteIP;
        accessor.lookupCodeInfo(handle, accessor.relativeIP(handle, absoluteIP), result);
        return result;
    }

    public static CodeInfoQueryResult lookupDeoptimizationEntrypoint(int deoptOffsetInImage, long encodedBci) {
        counters().lookupDeoptimizationEntrypointCount.inc();
        /* Deoptimization entry points are always in the image, i.e., never compiled at run time. */
        CodeInfoAccessor accessor = getImageCodeInfoAccessor();
        CodeInfoHandle handle = ImageCodeInfo.SINGLETON_HANDLE;
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = accessor.lookupDeoptimizationEntrypoint(handle, deoptOffsetInImage, encodedBci, result);
        if (relativeIP < 0) {
            return null;
        }
        result.ip = accessor.absoluteIP(handle, relativeIP);
        return result;
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    public static boolean visitObjectReferences(Pointer sp, CodePointer ip, CodeInfoAccessor accessor, CodeInfoHandle handle, DeoptimizedFrame deoptimizedFrame, ObjectReferenceVisitor visitor) {
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
        if (!accessor.isNone(handle)) {
            referenceMapEncoding = accessor.getReferenceMapEncoding(handle);
            referenceMapIndex = accessor.lookupReferenceMapIndex(handle, accessor.relativeIP(handle, ip));
        }

        if (referenceMapIndex == CodeInfoQueryResult.NO_REFERENCE_MAP) {
            throw reportNoReferenceMap(sp, ip, deoptimizedFrame, accessor, handle);
        }
        return CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, visitor);
    }

    private static RuntimeException reportNoReferenceMap(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, CodeInfoAccessor accessor, CodeInfoHandle data) {
        Log.log().string("ip: ").hex(ip).string("  sp: ").hex(sp);
        Log.log().string("  deoptFrame: ").object(deoptimizedFrame).string("  data:");
        accessor.log(data, Log.log()).newline();
        throw VMError.shouldNotReachHere("No reference map information found");
    }

    /**
     * Retrieves the {@link InstalledCode} that contains the provided instruction pointer. Returns
     * {@code null} if the instruction pointer is not within a runtime compile method.
     */
    @Uninterruptible(reason = "Prevent invalidation of code while in this method.")
    public static SubstrateInstalledCode lookupInstalledCode(CodePointer ip) {
        counters().lookupInstalledCodeCount.inc();
        RuntimeCodeInfoAccessor accessor = (RuntimeCodeInfoAccessor) getRuntimeCodeInfoAccessor();
        CodeInfoHandle handle = accessor.lookupCodeInfo(ip);
        if (accessor.isNone(handle)) {
            return null;
        }
        Object tether = accessor.acquireTether(handle);
        try {
            return getInstalledCode0(accessor, handle);
        } finally {
            accessor.releaseTether(handle, tether);
        }
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly retrieve InstalledCode.", calleeMustBe = false)
    private static SubstrateInstalledCode getInstalledCode0(RuntimeCodeInfoAccessor accessor, CodeInfoHandle handle) {
        return accessor.getInstalledCode(handle);
    }

    public static void invalidateInstalledCode(SubstrateInstalledCode installedCode) {
        /* Captures "installedCode" for the VMOperation. */
        VMOperation.enqueueBlockingSafepoint("CodeInfoTable.invalidateInstalledCode", () -> {
            counters().invalidateInstalledCodeCount.inc();
            if (installedCode.isValid()) {
                final RuntimeCodeInfo codeCache = getRuntimeCodeCache();
                CodeInfoHandle handle = codeCache.lookupMethod(WordFactory.pointer(installedCode.getAddress()));
                long num = codeCache.logMethodOperation(handle, RuntimeCodeInfo.INFO_INVALIDATE);
                codeCache.invalidateMethod(handle);
                codeCache.logMethodOperationEnd(num);
            }
        });
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfoAccessor lookupCodeInfoAccessor(CodePointer ip) {
        counters().lookupCodeInfoAccessorCount.inc();
        if (getImageCodeInfoAccessor().contains(ImageCodeInfo.SINGLETON_HANDLE, ip)) {
            return getImageCodeInfoAccessor();
        } else {
            return getRuntimeCodeInfoAccessor();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CodeInfoTableCounters counters() {
        return ImageSingletons.lookup(CodeInfoTableCounters.class);
    }
}

class CodeInfoTableCounters {
    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "CodeInfoTable");
    final Counter lookupCodeInfoAccessorCount = new Counter(counters, "lookupCodeInfoAccessor", "");
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
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CodeInfoTableCounters.class, new CodeInfoTableCounters());
        ImageSingletons.add(CodeInfoDecoderCounters.class, new CodeInfoDecoderCounters());
        ImageSingletons.add(CodeInfoEncoder.Counters.class, new CodeInfoEncoder.Counters());
        ImageSingletons.add(ImageCodeInfo.class, new ImageCodeInfo());
        ImageSingletons.add(RuntimeCodeInfo.class, new RuntimeCodeInfo());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess config) {
        config.registerAsImmutable(CodeInfoTable.getImageCodeInfoAccessor());
        config.registerAsImmutable(CodeInfoTable.getRuntimeCodeInfoAccessor());

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
