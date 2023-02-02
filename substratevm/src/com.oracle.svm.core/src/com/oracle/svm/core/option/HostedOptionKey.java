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
package com.oracle.svm.core.option;

import com.oracle.svm.common.option.LocatableOption;
import com.oracle.svm.common.option.MultiOptionValue;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import java.util.function.Consumer;

/**
 * Defines a hosted {@link Option} that is used during native image generation, in contrast to a
 * {@link RuntimeOptionKey runtime option}.
 *
 * @see com.oracle.svm.core.option
 */
public class HostedOptionKey<T> extends OptionKey<T> implements SubstrateOptionKey<T> {
    private final Consumer<HostedOptionKey<T>> validation;

    public HostedOptionKey(T defaultValue) {
        this(defaultValue, null);
    }

    public HostedOptionKey(T defaultValue, Consumer<HostedOptionKey<T>> validation) {
        super(defaultValue);
        this.validation = validation;
    }

    /**
     * Returns the value of this option in the {@link HostedOptionValues}.
     * <p>
     * The result of this method is guaranteed to be constant folded in the native image due to the
     * {@link Fold} annotation.
     */
    @Fold
    @Override
    public T getValue() {
        return getValue(HostedOptionValues.singleton());
    }

    /**
     * Returns {@code true} if this option has been set in the {@link HostedOptionValues}.
     * <p>
     * The result of this method is guaranteed to be constant folded in the native image due to the
     * {@link Fold} annotation.
     */
    @Fold
    @Override
    public boolean hasBeenSet() {
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
        Object defaultValue = getDefaultValue();
        if (defaultValue instanceof MultiOptionValue) {
            MultiOptionValue<?> value = (MultiOptionValue<?>) values.get(this);
            if (value == null) {
                value = ((MultiOptionValue<?>) defaultValue).createCopy();
            }
            value.valueUpdate(boxedValue);
            super.update(values, value);
        } else {
            super.update(values, LocatableOption.rawValue(boxedValue));
        }
    }

    @Override
    public void validate() {
        if (validation != null) {
            validation.accept(this);
        }
    }
}
