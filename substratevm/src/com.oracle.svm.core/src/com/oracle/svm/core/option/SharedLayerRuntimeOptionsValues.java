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
package com.oracle.svm.core.option;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.OptionKey;

/**
 * This option container is installed in shared layers so that the option values are observable.
 * However, the values themselves are only allowed to be changed in the app layer.
 */
@Platforms(Platform.HOSTED_ONLY.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class SharedLayerRuntimeOptionsValues extends RuntimeOptionValues {
    public SharedLayerRuntimeOptionsValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values, EconomicSet<String> allOptionNames) {
        super(values, allOptionNames);
        VMError.guarantee(values.isEmpty(), "It is currently disallowed to set a runtime option in a shared layer");
    }

    @Override
    public void update(OptionKey<?> key, Object value) {
        throw VMError.shouldNotReachHere("It is currently disallowed to set a runtime option in a shared layer %s", key);
    }

    @Override
    public void update(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        throw VMError.shouldNotReachHere("It is currently disallowed to set a runtime option in a shared layer");
    }
}
