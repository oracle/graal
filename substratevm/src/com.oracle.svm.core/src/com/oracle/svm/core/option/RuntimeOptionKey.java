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

import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.collections.EnumBitmask;
import com.oracle.svm.core.jdk.RuntimeSupport;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

/**
 * Defines a runtime {@link Option}, in contrast to a {@link HostedOptionKey hosted option}.
 *
 * @see com.oracle.svm.core.option
 */
public class RuntimeOptionKey<T> extends OptionKey<T> implements SubstrateOptionKey<T> {
    private final Consumer<RuntimeOptionKey<T>> validation;
    private final int flags;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
        this(defaultValue, null, flags);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionKey(T defaultValue, Consumer<RuntimeOptionKey<T>> validation, RuntimeOptionKeyFlag... flags) {
        super(defaultValue);
        this.validation = validation;
        this.flags = EnumBitmask.computeBitmask(flags);
    }

    /**
     * Returns the value of this option in the {@link RuntimeOptionValues}.
     */
    @Override
    public T getValue() {
        return getValue(RuntimeOptionValues.singleton());
    }

    public void update(T value) {
        RuntimeOptionValues.singleton().update(this, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void update(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        if (!SubstrateUtil.HOSTED && isImmutable() && !ImageSingletons.lookup(RuntimeSupport.class).isUninitialized() && isDifferentValue(values, newValue)) {
            T value = (T) values.get(this);
            throw new IllegalStateException("The runtime option '" + this.getName() + "' is immutable and can only be set during startup. Current value: " + value + ", new value: " + newValue);
        }
        super.update(values, newValue);
    }

    @SuppressWarnings("unchecked")
    private boolean isDifferentValue(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        if (!values.containsKey(this) && !Objects.equals(getDefaultValue(), newValue)) {
            return true;
        }

        T value = (T) values.get(this);
        return !Objects.equals(value, newValue);
    }

    @Override
    public boolean hasBeenSet() {
        return hasBeenSet(RuntimeOptionValues.singleton());
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void validate() {
        if (validation != null) {
            validation.accept(this);
        }
    }

    public boolean shouldCopyToCompilationIsolate() {
        return EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.RelevantForCompilationIsolates);
    }

    public boolean isImmutable() {
        return EnumBitmask.hasBit(flags, RuntimeOptionKeyFlag.Immutable);
    }

    @Fold
    public T getHostedValue() {
        return getValue(RuntimeOptionValues.singleton());
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
    }
}
