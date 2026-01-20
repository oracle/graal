/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.collections.EnumBitmask;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * Defines a runtime {@link Option}, in contrast to a {@link HostedOptionKey hosted option}.
 * <p>
 * The option value is stored in a shared map (see {@link RuntimeOptionValues}) and additionally
 * cached in an instance field (see below). The cache allows faster access and ensures that runtime
 * option values can also be accessed from {@link Uninterruptible} code. Note that the cache is
 * reset after every image build in case that multiple images are built in the same process to
 * ensure that options don't carry over between image builds. Note that for layered images, we need
 * to initialize the cache at run-time (see {@link RuntimeOptionValues#copyBuildTimeValuesToCache}).
 *
 * @see com.oracle.svm.core.option
 */
public class RuntimeOptionKey<T> extends OptionKey<T> implements SubstrateOptionKey<T> {
    public static final Object OPTION_NOT_SET = new Object();

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Consumer<RuntimeOptionKey<T>> buildTimeValidation;
    private final int flags;
    private volatile Object cachedValue = OPTION_NOT_SET;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
        this(defaultValue, null, flags);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionKey(T defaultValue, Consumer<RuntimeOptionKey<T>> buildTimeValidation, RuntimeOptionKeyFlag... flags) {
        super(defaultValue);
        this.buildTimeValidation = buildTimeValidation;
        this.flags = EnumBitmask.computeBitmask(flags);
    }

    @Fold
    public T getHostedValue() {
        return getValue();
    }

    public void setRawCachedValue(Object value) {
        this.cachedValue = value;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("unchecked")
    public final T getValue() {
        Object value = cachedValue;
        if (value == OPTION_NOT_SET) {
            return defaultValue;
        }
        return (T) value;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final T getValue(OptionValues values) {
        VMError.guarantee(RuntimeOptionValues.singleton() == values);
        return getValue();
    }

    @Override
    public final T getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        throw VMError.shouldNotReachHere("RuntimeOptionKey.getValueOrDefault() is not supported. Please use getValue() instead.");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final boolean hasBeenSet() {
        return cachedValue != OPTION_NOT_SET;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final boolean hasBeenSet(OptionValues values) {
        VMError.guarantee(RuntimeOptionValues.singleton() == values);
        return hasBeenSet();
    }

    public void update(T newValue) {
        RuntimeOptionValues.singleton().update(this, newValue);
    }

    /**
     * Note that the {@code values} argument is not necessarily the map from
     * {@link RuntimeOptionValues#getMap()}, as a temporary map could be used instead.
     */
    @Override
    public final void update(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        if (!SubstrateUtil.HOSTED && isImmutable() && !ImageSingletons.lookup(RuntimeSupport.class).isUninitialized() && !Objects.equals(getValue(), newValue)) {
            throw new IllegalStateException("The runtime option '" + this.getName() + "' is immutable and can only be set during startup. Current value: " + getValue() + ", new value: " + newValue);
        }
        super.update(values, newValue);
    }

    /**
     * Note that the {@code values} argument is not necessarily the map from
     * {@link RuntimeOptionValues#getMap()}, as a temporary map could be used instead.
     */
    @Override
    public final void putIfAbsent(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        if (!SubstrateUtil.HOSTED && isImmutable() && !ImageSingletons.lookup(RuntimeSupport.class).isUninitialized() && !Objects.equals(getValue(), newValue)) {
            throw new IllegalStateException("The runtime option '" + this.getName() + "' is immutable and can only be set during startup. Current value: " + getValue() + ", new value: " + newValue);
        }
        super.putIfAbsent(values, newValue);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void validate() {
        if (buildTimeValidation != null) {
            buildTimeValidation.accept(this);
        }
    }

    public boolean shouldCopyToCompilationIsolate() {
        return EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.RelevantForCompilationIsolates);
    }

    public boolean isImmutable() {
        return EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.Immutable) || EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.IsolateCreationOnly) ||
                        EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.RegisterForIsolateArgumentParser);
    }

    public boolean isIsolateCreationOnly() {
        return EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.IsolateCreationOnly) || EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.RegisterForIsolateArgumentParser);
    }

    public boolean shouldRegisterForIsolateArgumentParser() {
        return EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.RegisterForIsolateArgumentParser);
    }

    public enum RuntimeOptionKeyFlag {
        /** If this flag is set, then option value is propagated to all compilation isolates. */
        RelevantForCompilationIsolates,

        /**
         * If this flag is set, then the option value can only be changed during startup, i.e.,
         * before the startup hooks are executed (see {@link RuntimeSupport#initialize()}). This
         * flag should be used for runtime options that are accessed in startup hooks.
         */
        Immutable,
        /**
         * If this flag is set, then the option is parsed during isolate creation and its value can
         * typically only be set during isolate creation. This implies {@link #Immutable}.
         */
        IsolateCreationOnly,
        /**
         * If this flag is set, then the option is always included in the image. The option is also
         * registered for being parsed by {@link IsolateArgumentParser} and its value can typically
         * only be set during isolate creation. This implies {@link #Immutable} and
         * {@link #IsolateCreationOnly}.
         * <p>
         * See {@link IsolateArgumentParser#verifyOptionValues()} for the validation that these
         * options are not changed after isolate creation and potential exceptions to the rule.
         */
        RegisterForIsolateArgumentParser,
    }
}
