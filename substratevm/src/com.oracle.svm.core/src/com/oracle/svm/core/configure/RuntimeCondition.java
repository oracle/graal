/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.util.Set;
import java.util.WeakHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.util.VMError;

/**
 * Represents a super-type for all metadata conditions that are checked at runtime. It is created
 * from {@link ConfigurationCondition}s that represent the build-time conditions.
 * <p>
 * {@link RuntimeCondition}s can be stored into the heap or encoded into the image for space
 * reduction ({@link #getTypesForEncoding()}. All conditions are cached in the
 * {@link #runtimeConditionCache} to save space in the image heap and hence they must implement
 * {@link Object#equals(Object)} and {@link Object#hashCode()}. {@link RuntimeCondition} is most
 * often used in groups that are stored in {@link RuntimeConditionSet}.
 */
public sealed interface RuntimeCondition permits TypeReachedCondition {

    WeakHashMap<RuntimeCondition, RuntimeCondition> runtimeConditionCache = new WeakHashMap<>();

    static RuntimeCondition create(ConfigurationCondition cnd) {
        if (cnd.isAlwaysTrue() || !cnd.isRuntimeChecked()) {
            throw VMError.shouldNotReachHere("We should never create run-time conditions from conditions that are always true at build time. Condition: " + cnd);
        }
        return createTypeReachedCondition(cnd.getType());
    }

    static RuntimeCondition createTypeReachedCondition(Class<?> type) {
        TypeReachedCondition typeReachedCondition = new TypeReachedCondition(type);
        synchronized (runtimeConditionCache) {
            if (runtimeConditionCache.containsKey(typeReachedCondition)) {
                return runtimeConditionCache.get(typeReachedCondition);
            } else {
                runtimeConditionCache.put(typeReachedCondition, typeReachedCondition);
                return typeReachedCondition;
            }
        }
    }

    /**
     * @return <code>true</code> if the condition has been satisfied at run time.
     */
    boolean isSatisfied();

    @Platforms(Platform.HOSTED_ONLY.class)
    Set<Class<?>> getTypesForEncoding();
}
