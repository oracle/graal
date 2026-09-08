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
package com.oracle.svm.guest.staging.option;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.collections.EnumBitmask;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionKey;
import com.oracle.svm.shared.util.SubstrateUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

/**
 * Defines a runtime {@link Option}, in contrast to a {@link HostedOptionKey hosted option}.
 * <p>
 * The option value is stored in a shared map (see {@link RuntimeOptionValues}) and additionally
 * cached in an instance field (see below). The cache allows faster access and ensures that runtime
 * option values can also be accessed from {@link Uninterruptible} code. Note that the cache is
 * reset after every image build in case that multiple images are built in the same process to
 * ensure that options don't carry over between image builds. Note that for layered images, we need
 * to initialize the cache at run-time (see {@link RuntimeOptionValues#copyBuildTimeValuesToCache}).
 * <p>
 * Do not use {@link #onValueUpdate} for validation. When it is called, the option update may have
 * already modified the option map or cached value. Throwing from it can therefore leave the option
 * stores inconsistent. Use a before-value-update validation callback to reject individual
 * candidate values and an after-parsing validation callback for checks that need the complete
 * option configuration, including the values of other options. After-parsing validation stops at
 * the first callback that throws. Validation errors are not collected.
 * <p>
 * Related core option package: {@code com.oracle.svm.core.option}.
 */
public class RuntimeOptionKey<T> extends OptionKey<T> implements SubstrateOptionKey<T> {
    public static final Object OPTION_NOT_SET = new Object();

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final BiConsumer<RuntimeOptionKey<T>, T> initialBeforeValueUpdateValidation;
    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Consumer<RuntimeOptionKey<T>> initialAfterParsingValidation;

    private final int flags;
    private BiConsumer<RuntimeOptionKey<T>, T> beforeValueUpdateValidation;
    private Consumer<RuntimeOptionKey<T>> afterParsingValidation;

    private volatile Object cachedValue = OPTION_NOT_SET;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
        this(defaultValue, null, null, flags);
    }

    /**
     * Creates an option with validation callbacks that run during image building and at run time.
     *
     * <ul>
     * <li>{@code beforeValueUpdateValidation} validates each candidate value before it is applied.
     * During initial builder argument parsing, hosted option values and image singletons are not yet
     * available. At build-time, this callback must therefore perform only self-contained checks,
     * such as range validation. At run-time, it may execute more complex checks.</li>
     * <li>{@code afterParsingValidation} validates the resolved option state after option parsing
     * has finished. This callback can query other options as needed. It runs even if no option
     * value was specified explicitly, so it must check {@link #hasBeenSet()} if the default value
     * should not be validated.</li>
     * </ul>
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionKey(T defaultValue, BiConsumer<RuntimeOptionKey<T>, T> beforeValueUpdateValidation, Consumer<RuntimeOptionKey<T>> afterParsingValidation, RuntimeOptionKeyFlag... flags) {
        super(defaultValue);
        this.initialBeforeValueUpdateValidation = beforeValueUpdateValidation;
        this.initialAfterParsingValidation = afterParsingValidation;
        this.beforeValueUpdateValidation = initialBeforeValueUpdateValidation;
        this.afterParsingValidation = initialAfterParsingValidation;
        this.flags = EnumBitmask.computeBitmask(flags);
    }

    /** Resets hosted state before another image is built in the same process. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void resetHostedState() {
        cachedValue = OPTION_NOT_SET;
        beforeValueUpdateValidation = initialBeforeValueUpdateValidation;
        afterParsingValidation = initialAfterParsingValidation;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    final boolean hasAfterParsingValidation() {
        return afterParsingValidation != null;
    }

    @GuestFold
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
    public final boolean hasBeenSet() {
        return cachedValue != OPTION_NOT_SET;
    }

    public void update(T newValue) {
        RuntimeOptionValues.singleton().update(this, newValue);
    }

    void afterValueUpdateFromRuntimeValues() {
        afterValueUpdate();
    }

    /**
     * Note that the {@code values} argument is not necessarily the map from
     * {@link RuntimeOptionValues#getMap()}, as a temporary map could be used instead.
     */
    @Override
    public final void update(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        validateValueBeforeUpdate(newValue);
        updateAfterValidation(values, newValue);
    }

    final void updateAfterValidation(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        super.update(values, newValue);
    }

    /**
     * Note that the {@code values} argument is not necessarily the map from
     * {@link RuntimeOptionValues#getMap()}, as a temporary map could be used instead.
     */
    @Override
    public final void putIfAbsent(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        validateValueBeforeUpdate(newValue);
        super.putIfAbsent(values, newValue);
    }

    /** Runs validation that needs the complete option configuration. */
    @Override
    public final void validateAfterParsing() {
        if (afterParsingValidation != null) {
            afterParsingValidation.accept(this);
        }
    }

    /** Registers validation for a candidate value before it is applied. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void setBeforeValueUpdateValidation(BiConsumer<RuntimeOptionKey<T>, T> validation) {
        assert !BuildPhaseProvider.isSetupFinished() : "validation registration must finish during setup";
        assert beforeValueUpdateValidation == null : "a before-value-update validation is already registered";
        assert validation != null : "validation must not be null";
        beforeValueUpdateValidation = validation;
    }

    /**
     * Registers validation that runs after all options have been parsed during image building and
     * VM startup. The validation runs even if the option was not specified, so it must check
     * {@link #hasBeenSet()} when an unused option should be ignored.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void setAfterParsingValidation(Consumer<RuntimeOptionKey<T>> validation) {
        assert !BuildPhaseProvider.isSetupFinished() : "validation registration must finish during setup";
        assert afterParsingValidation == null : "an after-parsing validation is already registered";
        assert validation != null : "validation must not be null";
        afterParsingValidation = validation;
    }

    @SuppressWarnings("unchecked")
    final void validateValueBeforeUpdate(Object value) {
        if (!SubstrateUtil.HOSTED && isImmutable() && !ImageSingletons.lookup(RuntimeSupport.class).isUninitialized() && !Objects.equals(getValue(), value)) {
            throw new IllegalStateException("The runtime option '" + this.getName() + "' is immutable and can only be set during startup. Current value: " + getValue() + ", new value: " + value);
        }
        if (beforeValueUpdateValidation != null) {
            beforeValueUpdateValidation.accept(this, (T) value);
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
         * registered for being parsed by {@code com.oracle.svm.core.IsolateArgumentParser} and its value can typically
         * only be set during isolate creation. This implies {@link #Immutable} and
         * {@link #IsolateCreationOnly}.
         * <p>
         * See {@code com.oracle.svm.core.IsolateArgumentParser.verifyOptionValues()} for the
         * validation that these options are not changed after isolate creation and potential
         * exceptions to the rule.
         */
        RegisterForIsolateArgumentParser,
    }
}
