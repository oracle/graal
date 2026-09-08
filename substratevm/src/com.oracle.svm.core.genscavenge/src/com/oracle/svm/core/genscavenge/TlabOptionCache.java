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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.guest.staging.SubstrateGCOptions.ConcealedOptions.MinTLABSize;
import static com.oracle.svm.guest.staging.SubstrateGCOptions.ConcealedOptions.TLABSize;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidation;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Sanitize and cache TLAB option values. Unfortunately, proper error reporting is impossible during
 * early VM startup. So, we need to ensure that the used values are good enough so that the VM
 * startup can finish. Once the VM reaches a point where it can execute Java code, it validates the
 * options and reports errors (see {@link #registerOptionValidations}).
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class TlabOptionCache {
    private static final long DEFAULT_INITIAL_TLAB_SIZE = 8 * 1024;

    private long minTlabSize;
    private long tlabSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public TlabOptionCache() {
    }

    @Fold
    public static TlabOptionCache singleton() {
        return ImageSingletons.lookup(TlabOptionCache.class);
    }

    /** The minimum size that a TLAB must have. Anything smaller than that could crash the VM. */
    @Fold
    static long getAbsoluteMinTlabSize() {
        int additionalHeaderBytes = SubstrateOptions.AdditionalHeaderBytes.getValue();
        long absoluteMinTlabSize = 2 * 1024L + additionalHeaderBytes;
        return ObjectLayout.singleton().alignUp(absoluteMinTlabSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getMinTlabSize() {
        if (SubstrateUtil.HOSTED) {
            return Math.max(getAbsoluteMinTlabSize(), MinTLABSize.getHostedValue());
        }

        var minTlabSize = singleton().minTlabSize;
        assert minTlabSize >= getAbsoluteMinTlabSize() && ObjectLayout.singleton().isAligned(minTlabSize) && minTlabSize <= TlabSupport.maxSize().rawValue();
        return minTlabSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTlabSize() {
        assert tlabSize >= minTlabSize && ObjectLayout.singleton().isAligned(tlabSize) && tlabSize <= TlabSupport.maxSize().rawValue();
        return tlabSize;
    }

    /**
     * Based on the build-time and run-time option values, compute sane values that are at least
     * good enough for VM startup.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void cacheOptionValues() {
        long maxTlabSize = TlabSupport.maxSize().rawValue();
        assert ObjectLayout.singleton().isAligned(maxTlabSize) : "rounded values must not exceed max size";

        cacheMinTlabSize(maxTlabSize);
        cacheTlabSize(maxTlabSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheMinTlabSize(long maxTlabSize) {
        int optionIndex = IsolateArgumentParser.getOptionIndex(MinTLABSize);
        long optionValue = IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
        optionValue = UninterruptibleUtils.Math.clamp(optionValue, getAbsoluteMinTlabSize(), maxTlabSize);
        minTlabSize = ObjectLayout.singleton().alignUp(optionValue);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheTlabSize(long maxTlabSize) {
        int optionIndex = IsolateArgumentParser.getOptionIndex(TLABSize);
        long optionValue = IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
        if (optionValue == 0) {
            optionValue = UninterruptibleUtils.Math.clamp(DEFAULT_INITIAL_TLAB_SIZE, minTlabSize, maxTlabSize);
        } else {
            optionValue = UninterruptibleUtils.Math.clamp(optionValue, minTlabSize, maxTlabSize);
        }
        tlabSize = ObjectLayout.singleton().alignUp(optionValue);
    }

    /**
     * Registers validations here because they depend on collector-specific TLAB sizing and cannot
     * be declared with the shared TLAB options. Values parsed before registration are validated
     * immediately.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerOptionValidations() {
        MinTLABSize.setBeforeValueUpdateValidation(TlabOptionCache::validateMinTlabSizeValue);
        MinTLABSize.setAfterParsingValidation(TlabOptionCache::validateMinTlabSize);
        TLABSize.setBeforeValueUpdateValidation(TlabOptionCache::validateTlabSizeValue);
        TLABSize.setAfterParsingValidation(TlabOptionCache::validateTlabSize);

        /*
         * The option validation is registered after option parsing has already finished. So, we
         * need to execute it right away because the current option values could be invalid.
         */
        validateMinTlabSize(MinTLABSize);
        validateTlabSize(TLABSize);
    }

    private static void validateMinTlabSize(RuntimeOptionKey<Long> optionKey) {
        if (optionKey.hasBeenSet()) {
            validateMinTlabSizeValue(optionKey, optionKey.getValue());
        }
    }

    private static void validateMinTlabSizeValue(RuntimeOptionKey<Long> optionKey, long optionValue) {
        long minSize = getAbsoluteMinTlabSize();
        if (optionValue < minSize) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, optionValue, "The value must not be smaller than " + minSize);
        }
        long maxSize = TlabSupport.maxSize().rawValue();
        if (optionValue > maxSize) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, optionValue, "The value must not be larger than " + maxSize);
        }
    }

    private static void validateTlabSize(RuntimeOptionKey<Long> optionKey) {
        if (optionKey.hasBeenSet()) {
            validateTlabSizeValue(optionKey, optionKey.getValue());
        }
    }

    private static void validateTlabSizeValue(RuntimeOptionKey<Long> optionKey, long optionValue) {
        long minSize = getMinTlabSize();
        if (optionValue < minSize) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, optionValue, "The value must not be smaller than '" + MinTLABSize.getName() + "' (" + minSize + ")");
        }

        long maxSize = TlabSupport.maxSize().rawValue();
        if (optionValue > maxSize) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, optionValue, "The value must not be larger than " + maxSize);
        }
    }
}
