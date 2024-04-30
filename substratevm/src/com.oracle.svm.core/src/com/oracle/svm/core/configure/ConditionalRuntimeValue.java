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
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

/**
 * A image-heap stored {@link ConditionalRuntimeValue#value} that is guarded by run-time computed
 * {@link ConditionalRuntimeValue#conditions}.
 * </p>
 * {@link ConditionalRuntimeValue#conditions} are stored as an array to save space in the image
 * heap. This is subject to further optimizations.
 *
 * @param <T> type of the stored value.
 */
public final class ConditionalRuntimeValue<T> {
    private final Class<?>[] conditions;
    private boolean satisfied;
    volatile T value;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ConditionalRuntimeValue(Set<Class<?>> conditions, T value) {
        if (!conditions.isEmpty()) {
            this.conditions = conditions.toArray(Class[]::new);
        } else {
            this.conditions = null;
            satisfied = true;
        }

        VMError.guarantee(conditions.stream().noneMatch(c -> c.equals(Object.class)), "java.lang.Object must not be in conditions as it is always true.");
        this.value = value;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public T getValueUnconditionally() {
        return value;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Set<Class<?>> getConditions() {
        return conditions == null ? Set.of() : Set.of(conditions);
    }

    public T getValue(Predicate<Class<?>> conditionSatisfied) {
        if (satisfied) {
            return value;
        } else {
            for (Class<?> element : conditions) {
                if (conditionSatisfied.test(element)) {
                    satisfied = true;
                    break;
                }
            }
            if (satisfied) {
                return value;
            }
        }
        return null;
    }
}
