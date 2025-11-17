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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport.RuntimeOptionValidation;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

/**
 * Sanitize and cache TLAB option values. Unfortunately, proper error reporting is impossible during
 * early VM startup. So, we need to ensure that the used values are good enough so that the VM
 * startup can finish. Once the VM reaches a point where it can execute Java code, it validates the
 * options and reports errors (see {@link #registerOptionValidations}).
 */
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public void validateHostedOptionValues() {
        validateMinTlabSize(SubstrateGCOptions.ConcealedOptions.MinTLABSize);
        validateTlabSize(SubstrateGCOptions.ConcealedOptions.TLABSize);
    }

    /* The minimum size that a TLAB must have. Anything smaller than that could crash the VM. */
    @Fold
    static long getAbsoluteMinTlabSize() {
        int additionalHeaderBytes = SubstrateOptions.AdditionalHeaderBytes.getValue();
        long absoluteMinTlabSize = 2 * 1024L + additionalHeaderBytes;
        return NumUtil.roundUp(absoluteMinTlabSize, ConfigurationValues.getObjectLayout().getAlignment());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getMinTlabSize() {
        if (SubstrateUtil.HOSTED) {
            return Math.max(getAbsoluteMinTlabSize(), SubstrateGCOptions.ConcealedOptions.MinTLABSize.getHostedValue());
        }

        assert minTlabSize >= getAbsoluteMinTlabSize() && ConfigurationValues.getObjectLayout().isAligned(minTlabSize) && minTlabSize <= TlabSupport.maxSize().rawValue();
        return minTlabSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTlabSize() {
        assert tlabSize >= minTlabSize && ConfigurationValues.getObjectLayout().isAligned(tlabSize) && tlabSize <= TlabSupport.maxSize().rawValue();
        return tlabSize;
    }

    /**
     * Based on the build-time and run-time option values, compute sane values that are at least
     * good enough for VM startup.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void cacheOptionValues() {
        long maxTlabSize = TlabSupport.maxSize().rawValue();
        assert ConfigurationValues.getObjectLayout().isAligned(maxTlabSize) : "rounded values must not exceed max size";

        cacheMinTlabSize(maxTlabSize);
        cacheTlabSize(maxTlabSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheMinTlabSize(long maxTlabSize) {
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.ConcealedOptions.MinTLABSize);
        long optionValue = IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
        optionValue = UninterruptibleUtils.Math.clamp(optionValue, getAbsoluteMinTlabSize(), maxTlabSize);
        minTlabSize = ConfigurationValues.getObjectLayout().alignUp(optionValue);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheTlabSize(long maxTlabSize) {
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.ConcealedOptions.TLABSize);
        long optionValue = IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
        if (optionValue == 0) {
            optionValue = UninterruptibleUtils.Math.clamp(DEFAULT_INITIAL_TLAB_SIZE, minTlabSize, maxTlabSize);
        } else {
            optionValue = UninterruptibleUtils.Math.clamp(optionValue, minTlabSize, maxTlabSize);
        }
        tlabSize = ConfigurationValues.getObjectLayout().alignUp(optionValue);
    }

    public static void registerOptionValidations() {
        RuntimeOptionValidationSupport validationSupport = RuntimeOptionValidationSupport.singleton();
        validationSupport.register(new RuntimeOptionValidation<>(TlabOptionCache::validateMinTlabSize, SubstrateGCOptions.ConcealedOptions.MinTLABSize));
        validationSupport.register(new RuntimeOptionValidation<>(TlabOptionCache::validateTlabSize, SubstrateGCOptions.ConcealedOptions.TLABSize));
    }

    private static void validateMinTlabSize(RuntimeOptionKey<Long> optionKey) {
        long optionValue = optionKey.getValue();
        if (optionKey.hasBeenSet() && optionValue < getAbsoluteMinTlabSize()) {
            throw invalidOptionValue("Option 'MinTLABSize' (" + optionValue + ") must not be smaller than " + getAbsoluteMinTlabSize());
        }

        long maxSize = TlabSupport.maxSize().rawValue();
        if (optionValue > maxSize) {
            throw invalidOptionValue("Option 'MinTLABSize' (" + optionValue + ") must not be larger than " + maxSize);
        }
    }

    private static void validateTlabSize(RuntimeOptionKey<Long> optionKey) {
        long optionValue = optionKey.getValue();
        if (optionKey.hasBeenSet() && optionValue < TlabOptionCache.singleton().getMinTlabSize()) {
            throw invalidOptionValue("Option 'TLABSize' (" + optionValue + ") must not be smaller than 'MinTLABSize' (" + TlabOptionCache.singleton().getMinTlabSize() + ").");
        }

        long maxSize = TlabSupport.maxSize().rawValue();
        if (optionValue > maxSize) {
            throw invalidOptionValue("Option 'TLABSize' (" + optionValue + ") must not be larger than " + maxSize);
        }
    }

    private static RuntimeException invalidOptionValue(String msg) {
        if (SubstrateUtil.HOSTED) {
            throw UserError.abort(msg);
        }
        throw new IllegalArgumentException(msg);
    }
}
