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

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceMapDecoder;
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
 */
public class CodeInfoTable {

    public static class Options {
        @Option(help = "Count accesses to the image and runtime code info table")//
        public static final HostedOptionKey<Boolean> CodeCacheCounters = new HostedOptionKey<>(false);
    }

    public static ImageCodeInfo getImageCodeCache() {
        return ImageSingletons.lookup(ImageCodeInfo.class);
    }

    public static RuntimeCodeInfo getRuntimeCodeCache() {
        return ImageSingletons.lookup(RuntimeCodeInfo.class);
    }

    public static CodeInfoQueryResult lookupCodeInfoQueryResult(CodePointer ip) {
        counters().lookupCodeInfoCount.inc();
        AbstractCodeInfo data = lookupCodeInfo(ip);
        if (data == null) {
            return null;
        }
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        result.data = data;
        result.ip = ip;
        data.lookupCodeInfo(data.relativeIP(ip), result);
        return result;
    }

    public static CodeInfoQueryResult lookupDeoptimizationEntrypoint(int deoptOffsetInImage, long encodedBci) {
        counters().lookupDeoptimizationEntrypointCount.inc();
        /* Deoptimization entry points are always in the image, i.e., never compiled at run time. */
        AbstractCodeInfo data = getImageCodeCache();
        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = data.lookupDeoptimizationEntrypoint(deoptOffsetInImage, encodedBci, result);
        if (relativeIP < 0) {
            return null;
        }
        result.data = data;
        result.ip = data.absoluteIP(relativeIP);
        return result;
    }

    public static long lookupTotalFrameSize(CodePointer ip) {
        counters().lookupTotalFrameSizeCount.inc();
        AbstractCodeInfo data = lookupCodeInfo(ip);
        if (data == null) {
            return -1;
        }
        return data.lookupTotalFrameSize(data.relativeIP(ip));
    }

    public static long lookupExceptionOffset(CodePointer ip) {
        counters().lookupExceptionOffsetCount.inc();
        AbstractCodeInfo data = lookupCodeInfo(ip);
        if (data == null) {
            return -1;
        }
        return data.lookupExceptionOffset(data.relativeIP(ip));
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    public static boolean visitObjectReferences(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, ObjectReferenceVisitor visitor) {
        counters().visitObjectReferencesCount.inc();

        if (deoptimizedFrame != null) {
            /*
             * It is a deoptimized frame. The DeoptimizedFrame object is stored in the frame, but it
             * is pinned so we do not have to do anything.
             */
            return true;
        }

        byte[] referenceMapEncoding = null;
        long referenceMapIndex = CodeInfoQueryResult.NO_REFERENCE_MAP;
        AbstractCodeInfo data = lookupCodeInfo(ip);
        if (data != null) {
            referenceMapEncoding = data.getReferenceMapEncoding();
            referenceMapIndex = data.lookupReferenceMapIndex(data.relativeIP(ip));
        }

        if (referenceMapIndex == CodeInfoQueryResult.NO_REFERENCE_MAP) {
            throw reportNoReferenceMap(sp, ip, deoptimizedFrame, data);
        }
        return ReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, visitor);
    }

    private static RuntimeException reportNoReferenceMap(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, AbstractCodeInfo data) {
        Log.log().string("ip: ").hex(ip).string("  sp: ").hex(sp);
        Log.log().string("  deoptFrame: ").object(deoptimizedFrame).string("  data:").object(data).newline();
        throw VMError.shouldNotReachHere("No reference map information found");
    }

    /**
     * Retrieves the {@link InstalledCode} that contains the provided instruction pointer. Returns
     * {@code null} if the instruction pointer is not within a runtime compile method.
     */
    public static SubstrateInstalledCode lookupInstalledCode(CodePointer ip) {
        counters().lookupInstalledCodeCount.inc();
        RuntimeMethodInfo methodInfo = getRuntimeCodeCache().lookupMethod(ip);
        return methodInfo != null ? methodInfo.installedCode.get() : null;
    }

    public static void invalidateInstalledCode(SubstrateInstalledCode installedCode) {
        /* Captures "installedCode" for the VMOperation. */
        VMOperation.enqueueBlockingSafepoint("CodeInfoTable.invalidateInstalledCode", () -> {
            counters().invalidateInstalledCodeCount.inc();
            if (installedCode.isValid()) {
                final RuntimeCodeInfo codeCache = getRuntimeCodeCache();
                RuntimeMethodInfo methodInfo = codeCache.lookupMethod(WordFactory.pointer(installedCode.getAddress()));
                long num = codeCache.logMethodOperation(methodInfo, RuntimeCodeInfo.INFO_INVALIDATE);
                codeCache.invalidateMethod(methodInfo);
                codeCache.logMethodOperationEnd(num);
            }
        });
    }

    public static AbstractCodeInfo lookupCodeInfo(CodePointer ip) {
        if (getImageCodeCache().contains(ip)) {
            return getImageCodeCache();
        } else {
            return getRuntimeCodeCache().lookupMethod(ip);
        }
    }

    public static Log logCodeInfoResult(Log log, CodePointer ip) {
        AbstractCodeInfo data = lookupCodeInfo(ip);
        if (data == null) {
            return log.string("No CodeInfo for IP ").zhex(ip.rawValue());
        }
        log.object(data);
        return log.string(" name = ").string(data.getName());
    }

    private static CodeInfoTableCounters counters() {
        return ImageSingletons.lookup(CodeInfoTableCounters.class);
    }
}

class CodeInfoTableCounters {
    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "CodeInfoTable");
    final Counter lookupCodeInfoCount = new Counter(counters, "lookupCodeInfo", "");
    final Counter lookupDeoptimizationEntrypointCount = new Counter(counters, "lookupDeoptimizationEntrypoint", "");
    final Counter lookupTotalFrameSizeCount = new Counter(counters, "lookupTotalFrameSize", "");
    final Counter lookupExceptionOffsetCount = new Counter(counters, "lookupExceptionOffset", "");
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
        ImageCodeInfo imageInfo = CodeInfoTable.getImageCodeCache();
        config.registerAsImmutable(imageInfo);
        config.registerAsImmutable(imageInfo.codeInfoIndex);
        config.registerAsImmutable(imageInfo.codeInfoEncodings);
        config.registerAsImmutable(imageInfo.referenceMapEncoding);
        config.registerAsImmutable(imageInfo.frameInfoEncodings);
        config.registerAsImmutable(imageInfo.frameInfoObjectConstants);
        config.registerAsImmutable(imageInfo.frameInfoSourceClassNames);
        config.registerAsImmutable(imageInfo.frameInfoSourceMethodNames);
        config.registerAsImmutable(imageInfo.frameInfoSourceFileNames);
        config.registerAsImmutable(imageInfo.frameInfoNames);
    }
}
