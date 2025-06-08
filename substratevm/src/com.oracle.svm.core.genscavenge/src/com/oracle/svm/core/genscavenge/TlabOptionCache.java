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
import static com.oracle.svm.core.genscavenge.TlabSupport.maxSize;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

/**
 * Sanitize and cache TLAB option values. Unfortunately, proper error reporting is impossible during
 * early VM startup. So, we need to ensure that the used values are good enough so that the VM
 * startup can finish. Once the VM reaches a point where it can execute Java code, it validates the
 * options and reports errors (see {@link #registerOptionValidations}).
 */
public class TlabOptionCache {

    private long minTlabSize;
    private long tlabSize;
    private long initialTLABSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public TlabOptionCache() {
        minTlabSize = getAbsoluteMinTlabSize();
        tlabSize = SubstrateGCOptions.TlabOptions.TLABSize.getHostedValue();
        initialTLABSize = SerialAndEpsilonGCOptions.InitialTLABSize.getHostedValue();
    }

    @Fold
    public static TlabOptionCache singleton() {
        return ImageSingletons.lookup(TlabOptionCache.class);
    }

    /*
     * The minimum size a TLAB must always have. A smaller TLAB may lead to a VM crash.
     */
    @Fold
    static long getAbsoluteMinTlabSize() {
        int additionalHeaderBytes = SubstrateOptions.AdditionalHeaderBytes.getValue();
        long absoluteMinTlabSize = 2 * 1024L + additionalHeaderBytes;
        return NumUtil.roundUp(absoluteMinTlabSize, ConfigurationValues.getObjectLayout().getAlignment());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getMinTlabSize() {
        return minTlabSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTlabSize() {
        return tlabSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getInitialTLABSize() {
        return initialTLABSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void cacheOptionValues() {
        int minTlabSizeIdx = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.TlabOptions.MinTLABSize);
        long minTlabSizeValue = IsolateArgumentParser.singleton().getLongOptionValue(minTlabSizeIdx);
        cacheMinTlabSize(minTlabSizeValue);

        int tlabSizeIdx = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.TlabOptions.TLABSize);
        long tlabSizeValue = IsolateArgumentParser.singleton().getLongOptionValue(tlabSizeIdx);
        cacheTlabSize(tlabSizeValue);

        int initialTlabSizeIdx = IsolateArgumentParser.getOptionIndex(SerialAndEpsilonGCOptions.InitialTLABSize);
        long initialTlabSizeValue = IsolateArgumentParser.singleton().getLongOptionValue(initialTlabSizeIdx);
        cacheInitialTlabSize(initialTlabSizeValue, initialTLABSize != initialTlabSizeValue);
    }

    public static void registerOptionValidations() {

        long maxSize = maxSize().rawValue();

        RuntimeOptionValidationSupport validationSupport = RuntimeOptionValidationSupport.singleton();

        validationSupport.register(new RuntimeOptionValidationSupport.RuntimeOptionValidation<>(optionKey -> {
            long minTlabSizeValue = optionKey.getValue();

            if (optionKey.hasBeenSet() && minTlabSizeValue < getAbsoluteMinTlabSize()) {
                throw new IllegalArgumentException(String.format("MinTLABSize (%d) must be greater than or equal to reserved area in TLAB (%d).", minTlabSizeValue, getAbsoluteMinTlabSize()));
            }
            if (minTlabSizeValue > maxSize) {
                throw new IllegalArgumentException(String.format("MinTLABSize (%d) must be less than or equal to ergonomic TLAB maximum (%d).", minTlabSizeValue, maxSize));
            }
        }, SubstrateGCOptions.TlabOptions.MinTLABSize));

        validationSupport.register(new RuntimeOptionValidationSupport.RuntimeOptionValidation<>(optionKey -> {
            // Check that TLABSize is still the default value or size >= abs min && size <= abs max.
            long tlabSizeValue = optionKey.getValue();
            if (optionKey.hasBeenSet() && tlabSizeValue < SubstrateGCOptions.TlabOptions.MinTLABSize.getValue()) {
                throw new IllegalArgumentException(
                                String.format("TLABSize (%d) must be greater than or equal to MinTLABSize (%d).", tlabSizeValue, SubstrateGCOptions.TlabOptions.MinTLABSize.getValue()));
            }
            if (tlabSizeValue > maxSize) {
                throw new IllegalArgumentException(String.format("TLABSize (%d) must be less than or equal to ergonomic TLAB maximum size (%d).", tlabSizeValue, maxSize));
            }
        }, SubstrateGCOptions.TlabOptions.TLABSize));

        validationSupport.register(new RuntimeOptionValidationSupport.RuntimeOptionValidation<>(optionKey -> {
            long initialTlabSizeValue = optionKey.getValue();
            if (initialTlabSizeValue < SubstrateGCOptions.TlabOptions.MinTLABSize.getValue()) {
                throw new IllegalArgumentException(
                                String.format("InitialTLABSize (%d) must be greater than or equal to MinTLABSize (%d).", initialTlabSizeValue, SubstrateGCOptions.TlabOptions.MinTLABSize.getValue()));
            }
            if (initialTlabSizeValue > maxSize) {
                throw new IllegalArgumentException(String.format("TLABSize (%d) must be less than or equal to ergonomic TLAB maximum size (%d).", initialTlabSizeValue, maxSize));
            }
        }, SerialAndEpsilonGCOptions.InitialTLABSize));

    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheMinTlabSize(long optionValue) {
        if (getAbsoluteMinTlabSize() <= optionValue && optionValue <= maxSize().rawValue()) {
            minTlabSize = optionValue;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheTlabSize(long optionValue) {
        if (getAbsoluteMinTlabSize() <= optionValue && optionValue <= maxSize().rawValue()) {
            tlabSize = optionValue;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void cacheInitialTlabSize(long optionValue, boolean hasBeenSet) {
        if (!hasBeenSet && minTlabSize > initialTLABSize) {
            initialTLABSize = minTlabSize;
        } else if (getAbsoluteMinTlabSize() <= optionValue && optionValue <= maxSize().rawValue()) {
            initialTLABSize = UninterruptibleUtils.Math.max(minTlabSize, optionValue);
        }
    }

}
