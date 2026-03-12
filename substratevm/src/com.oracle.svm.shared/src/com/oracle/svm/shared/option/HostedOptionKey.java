/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared.option;

import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.svm.shared.collections.EnumBitmask;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

/**
 * Defines a hosted {@link Option} that is used during native image generation, in contrast to a
 * {@code RuntimeOptionKey runtime option}.
 *
 * See {@code com.oracle.svm.core.option}.
 */
public class HostedOptionKey<T> extends OptionKey<T> implements SubstrateOptionKey<T> {
    private final Consumer<HostedOptionKey<T>> buildTimeValidation;
    private final int flags;
    private OptionOrigin lastOrigin;

    public HostedOptionKey(T defaultValue, HostedOptionKeyFlag... flags) {
        this(defaultValue, null, flags);
    }

    /**
     * Hosted option with build-time validation.
     * <p/>
     * Note: <code>buildTimeValidation</code> is called even when the option is not passed in.
     */
    public HostedOptionKey(T defaultValue, Consumer<HostedOptionKey<T>> buildTimeValidation, HostedOptionKeyFlag... flags) {
        super(defaultValue);
        this.buildTimeValidation = buildTimeValidation;
        this.flags = EnumBitmask.computeBitmask(flags);
    }

    public boolean shouldPassToNativeGC() {
        return !EnumBitmask.hasBit(flags, HostedOptionKeyFlag.DoNotPassToNativeGC);
    }

    /**
     * Returns the value of this option in the {@link HostedOptionValues}.
     */
    @Override
    @GuestFold
    public T getValue() {
        VMError.guarantee(!ImageInfo.inImageRuntimeCode(), "Must not be called at run time");
        return getValue(HostedOptionValues.singleton());
    }

    /**
     * Returns {@code true} if this option has been set in the {@link HostedOptionValues}.
     */
    @Override
    @GuestFold
    public boolean hasBeenSet() {
        VMError.guarantee(!ImageInfo.inImageRuntimeCode(), "Must not be called at run time");
        return hasBeenSet(HostedOptionValues.singleton());
    }

    /**
     * Descriptors are not loaded for {@link HostedOptionKey}.
     */
    @Override
    protected boolean checkDescriptorExists() {
        return true;
    }

    @Override
    public void update(EconomicMap<OptionKey<?>, Object> values, Object boxedValue) {
        if (defaultValue instanceof MultiOptionValue) {
            MultiOptionValue<?> value = (MultiOptionValue<?>) values.get(this);
            if (value == null) {
                value = ((MultiOptionValue<?>) defaultValue).createCopy();
            }
            value.valueUpdate(boxedValue);
            super.update(values, value);
        } else {
            /* store origin, last option update wins. */
            lastOrigin = OptionOrigin.from(LocatableOption.valueOrigin(boxedValue), false);
            super.update(values, LocatableOption.rawValue(boxedValue));
        }
    }

    @Override
    public void validate() {
        if (buildTimeValidation != null) {
            buildTimeValidation.accept(this);
        }
    }

    public OptionOrigin getLastOrigin() {
        return lastOrigin;
    }

    public enum HostedOptionKeyFlag {
        /** If this flag is set, then the option value is not passed to native GCs like G1. */
        DoNotPassToNativeGC,
    }
}
