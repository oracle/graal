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
package com.oracle.svm.core.gc.shenandoah;

import static com.oracle.svm.core.gc.shared.NativeGCOptions.K;
import static com.oracle.svm.core.gc.shared.NativeGCOptions.M;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.gc.shared.NativeGCDebugLevel;
import com.oracle.svm.core.gc.shared.NativeGCOptions;
import com.oracle.svm.core.gc.shared.NativeGCOptions.HostedArgumentsSupplier;
import com.oracle.svm.core.gc.shared.NativeGCOptions.NativeGCHostedOptionKey;
import com.oracle.svm.core.gc.shared.NativeGCOptions.NativeGCRuntimeOptionKey;
import com.oracle.svm.core.gc.shared.NativeGCOptions.RuntimeArgumentsSupplier;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionKey;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

/**
 * Contains options that are specific to Shenandoah. See {@link NativeGCOptions} for more details.
 */
public class ShenandoahOptions {
    private static final String SUPPORTED_REGION_SIZES = "Supported values are 256k, 512k, 1m, 2m, 4m, 8m, 16m, or 32m";

    @Option(help = "Specifies the debug level of the linked Shenandoah GC [product, fastdebug, or debug]", type = OptionType.Debug) //
    protected static final HostedOptionKey<String> ShenandoahDebugLevel = new ShenandoahHostedOptionKey<>("product", false);

    @Fold
    public static NativeGCDebugLevel getDebugLevel() {
        NativeGCDebugLevel result = NativeGCDebugLevel.fromString(ShenandoahDebugLevel.getValue());
        UserError.guarantee(result != null, "'%s' is not a valid value for the option %s.", ShenandoahDebugLevel.getValue(), ShenandoahDebugLevel.getName());
        return result;
    }

    @Option(help = "Size of the Shenandoah heap regions in bytes. " + SUPPORTED_REGION_SIZES + ".", type = OptionType.User)//
    public static final HostedOptionKey<Integer> ShenandoahRegionSize = new ShenandoahHostedOptionKey<>(1 * M, ShenandoahOptions::validateRegionSize);

    /* Encoded option values. */
    public static final CGlobalData<CCharPointer> HOSTED_ARGUMENTS = CGlobalDataFactory.createBytes(new HostedArgumentsSupplier(getOptionFields()));
    public static final CGlobalData<CCharPointer> RUNTIME_ARGUMENTS = CGlobalDataFactory.createBytes(new RuntimeArgumentsSupplier(getOptionFields()));

    public static ArrayList<Field> getOptionFields() {
        Class<?>[] optionClasses = {SubstrateGCOptions.class, SubstrateGCOptions.TlabOptions.class, NativeGCOptions.class, ShenandoahOptions.class};
        return NativeGCOptions.getOptionFields(optionClasses);
    }

    private static void validateShenandoahOption(SubstrateOptionKey<?> optionKey) {
        if (optionKey.hasBeenSet() && !SubstrateOptions.useShenandoahGC()) {
            throw UserError.abort("The option '%s' can only be used together with the Shenandoah garbage collector ('--gc=shenandoah').", optionKey.getName());
        }
    }

    private static void validateRegionSize(HostedOptionKey<Integer> optionKey) {
        int value = optionKey.getValue();
        if (value % M == 0 && SubstrateUtil.isPowerOf2(value / M) && value >= 256 * K && value <= 32 * M) {
            return;
        }
        throw UserError.invalidOptionValue(ShenandoahRegionSize, value, SUPPORTED_REGION_SIZES);
    }

    private static class ShenandoahHostedOptionKey<T> extends NativeGCHostedOptionKey<T> {
        ShenandoahHostedOptionKey(T defaultValue, Consumer<HostedOptionKey<T>> validation) {
            this(defaultValue, true, validation);
        }

        ShenandoahHostedOptionKey(T defaultValue, boolean passToCpp) {
            this(defaultValue, passToCpp, null);
        }

        ShenandoahHostedOptionKey(T defaultValue, boolean passToCpp, Consumer<HostedOptionKey<T>> validation) {
            super(defaultValue, passToCpp, validation);
        }

        @Override
        public void validate() {
            validateShenandoahOption(this);
            super.validate();
        }
    }

    @SuppressWarnings("unused")
    private static class ShenandoahRuntimeOptionKey<T> extends NativeGCRuntimeOptionKey<T> {
        ShenandoahRuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
            super(defaultValue, flags);
        }

        @Override
        public void validate() {
            validateShenandoahOption(this);
            super.validate();
        }
    }
}
