/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.RuntimeSupport;

/**
 * A runtime option that can only be modified as long as the VM is not fully initialized yet.
 */
public class ImmutableRuntimeOptionKey<T> extends RuntimeOptionKey<T> {
    public ImmutableRuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
        super(defaultValue, flags);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void update(EconomicMap<OptionKey<?>, Object> values, Object newValue) {
        if (!SubstrateUtil.HOSTED && !ImageSingletons.lookup(RuntimeSupport.class).isUninitialized() && isDifferentValue(values, newValue)) {
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
}
