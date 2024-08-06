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

import static com.oracle.svm.core.configure.ConfigurationFiles.Options.TrackUnsatisfiedTypeReachedConditions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

/**
 * Represents a group of {@link #conditions} that guard a value. The conditions are encoded
 * <p>
 * If any of the {@link #conditions} is satisfied then the whole set becomes also
 * {@link #satisfied}. {@link RuntimeConditionSet}s can be created at build time
 * {@link #createHosted(ConfigurationCondition)} and stored to the image heap, or it can be encoded
 * ({@link #getTypesForEncoding()} and later decoded at run time ({@link #createDecoded(Object[])}.
 * The current implementation does not cache {@link #conditions}, although this will be implemented
 * in the future (GR-49526)
 */
public class RuntimeConditionSet {

    private Object[] conditions;
    private boolean satisfied;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeConditionSet emptySet() {
        return new RuntimeConditionSet(new Object[0]);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeConditionSet createHosted(ConfigurationCondition condition) {
        var conditionSet = new RuntimeConditionSet(new Object[0]);
        conditionSet.addCondition(condition);
        return conditionSet;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void addCondition(ConfigurationCondition cnd) {
        VMError.guarantee(cnd.isRuntimeChecked(), "Only runtime conditions can be added to the ConditionalRuntimeValue.");
        if (satisfied) {
            return;
        } else if (cnd.isAlwaysTrue()) {
            conditions = null;
            satisfied = true;
            return;
        }

        Object newRuntimeCondition = createRuntimeCondition(cnd);
        Set<Object> existingConditions = conditions == null ? new HashSet<>() : new HashSet<>(Arrays.asList(conditions));
        existingConditions.add(newRuntimeCondition);
        setConditions(existingConditions.toArray());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Set<Class<?>> getTypesForEncoding() {
        if (conditions == null) {
            return Set.of();
        } else {
            Set<Class<?>> types = new HashSet<>();
            for (Object condition : conditions) {
                types.addAll(getTypesForEncoding(condition));
            }
            return types;
        }
    }

    public static RuntimeConditionSet unmodifiableEmptySet() {
        return UnmodifiableRuntimeConditionSet.UNMODIFIABLE_EMPTY_SET;
    }

    public static RuntimeConditionSet createDecoded(Object[] conditions) {
        return new RuntimeConditionSet(conditions);
    }

    /**
     * Checks if any of the conditions has been satisfied. It caches the value in satisfied. This
     * code can be concurrently executed, however there are no concurrency primitives used. The
     * implementation relies on the fact that checking if a condition is satisfied is an idempotent
     * operation.
     *
     * @return <code>true</code> if any of the elements is satisfied.
     */
    public boolean satisfied() {
        var result = false;
        if (satisfied) {
            result = true;
        } else {
            final var localConditions = conditions;
            if (localConditions == null) {
                result = true;
            } else {
                for (Object condition : localConditions) {
                    if (isSatisfied(condition)) {
                        conditions = null;
                        satisfied = result = true;
                        break;
                    }
                }
            }
        }

        if (TrackUnsatisfiedTypeReachedConditions.getValue() && !result) {
            LogUtils.info("Unsatisfied runtime conditions reachable at build-time: " + Arrays.toString(conditions));
            new Exception().printStackTrace(System.out);
            return true;
        }

        return result;
    }

    @Override
    public String toString() {
        String conditionsString = this.conditions == null ? "[]" : Arrays.toString(this.conditions);
        return conditionsString + " = " + satisfied;
    }

    private RuntimeConditionSet(Object[] conditions) {
        setConditions(conditions);
    }

    private void setConditions(Object[] conditions) {
        if (conditions.length == 0) {
            this.conditions = null;
        } else {
            this.conditions = conditions;
        }
        satisfied = false;
    }

    private static Object createRuntimeCondition(ConfigurationCondition cnd) {
        if (cnd.isAlwaysTrue() || !cnd.isRuntimeChecked()) {
            throw VMError.shouldNotReachHere("We should never create run-time conditions from conditions that are always true at build time. Condition: " + cnd);
        }
        return cnd.getType();
    }

    private static boolean isSatisfied(Object condition) {
        if (condition instanceof Class<?> typeReachedCondition) {
            return DynamicHub.fromClass(typeReachedCondition).isReached();
        } else {
            throw VMError.shouldNotReachHere("Only typeReached condition is supported.");
        }
    }

    private static Set<Class<?>> getTypesForEncoding(Object condition) {
        if (condition instanceof Class<?> res) {
            return Set.of(res);
        } else {
            throw VMError.shouldNotReachHere("Only typeReached condition is supported.");
        }
    }

    public static final class UnmodifiableRuntimeConditionSet extends RuntimeConditionSet {
        private static final RuntimeConditionSet UNMODIFIABLE_EMPTY_SET = new UnmodifiableRuntimeConditionSet(new Object[0]);

        private UnmodifiableRuntimeConditionSet(Object[] conditions) {
            super(conditions);
        }

        @Override
        public synchronized void addCondition(ConfigurationCondition cnd) {
            throw new UnsupportedOperationException("Can't add conditions to an unmodifiable set of conditions.");
        }
    }
}
