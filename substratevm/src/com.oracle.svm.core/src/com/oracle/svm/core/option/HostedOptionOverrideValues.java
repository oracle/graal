/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

/**
 * The singleton holder of hosted options overrides. The values held by this object override those
 * in {@link HostedOptionValues}.
 * <p>
 * Unlike {@link HostedOptionValues}, this object is mutable. This allows for options to be
 * overridden programmatically in later phases.
 */
public class HostedOptionOverrideValues {
    private final EconomicMap<HostedOptionKey<?>, Object> overrides;

    public HostedOptionOverrideValues() {
        this.overrides = EconomicMap.create();
    }

    public static HostedOptionOverrideValues singleton() {
        return ImageSingletons.lookup(HostedOptionOverrideValues.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(HostedOptionKey<T> key) {
        return (T) this.overrides.get(key);
    }

    public <T> void set(HostedOptionKey<T> key, T value) {
        this.overrides.put(key, value);
    }
}
