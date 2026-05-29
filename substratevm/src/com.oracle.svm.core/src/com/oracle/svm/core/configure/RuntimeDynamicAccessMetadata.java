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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.TypeReachabilityCondition;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;

/**
 * The dynamic access metadata for some value that can be accessed at run time. Contains a set of
 * {@link #conditions} that dictate whether the value (e.g., a resource) should be accessible; also
 * tracks whether the value is present because it was preserved.
 * <p>
 * If any of the {@link #conditions} is satisfied then the whole set becomes also
 * {@link #satisfied}. {@link RuntimeDynamicAccessMetadata}s can be created at build time
 * {@link #createHosted(AccessCondition,boolean)} and stored to the image heap, or it can be encoded
 * ({@link #getTypesForEncoding()} and later decoded at run time
 * ({@link #createDecoded(Object[], boolean)}. The current implementation does not cache
 * {@link #conditions}, although this will be implemented in the future (GR-49526)
 */
public class RuntimeDynamicAccessMetadata {

    private final Object[] conditions;
    private volatile boolean satisfied;
    private final boolean preserved;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final ConcurrentHashMap<MetadataKey, RuntimeDynamicAccessMetadata> INTERNED = new ConcurrentHashMap<>();

    public static RuntimeDynamicAccessMetadata emptySet(boolean preserved) {
        return alwaysAvailable(preserved);
    }

    public static RuntimeDynamicAccessMetadata alwaysAvailable(boolean preserved) {
        return preserved ? AlwaysAvailableRuntimeDynamicAccessMetadata.ALWAYS_AVAILABLE_PRESERVED : AlwaysAvailableRuntimeDynamicAccessMetadata.ALWAYS_AVAILABLE_NOT_PRESERVED;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeDynamicAccessMetadata createHosted(AccessCondition condition, boolean preserved) {
        return addCondition(null, condition, preserved);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeDynamicAccessMetadata addCondition(RuntimeDynamicAccessMetadata current, AccessCondition cnd, boolean preserved) {
        VMError.guarantee(cnd instanceof TypeReachabilityCondition, "Only TypeReachabilityCondition conditions can be used in RuntimeConditionSet.");
        TypeReachabilityCondition reachabilityCondition = (TypeReachabilityCondition) cnd;
        VMError.guarantee(reachabilityCondition.isRuntimeChecked(), "Only runtime conditions can be added to the ConditionalRuntimeValue.");
        boolean mergedPreserved = current == null ? preserved : current.preserved && preserved;
        if (reachabilityCondition.isAlwaysTrue() || current != null && current.isAlwaysAvailable()) {
            return alwaysAvailable(mergedPreserved);
        }
        Object newRuntimeCondition = createRuntimeCondition(cnd);
        Object[] mergedConditions;
        if (current == null || current.conditions == null || current.conditions.length == 0) {
            mergedConditions = new Object[]{newRuntimeCondition};
        } else {
            Object[] oldConditions = current.conditions;
            for (Object oldCondition : oldConditions) {
                if (oldCondition.equals(newRuntimeCondition)) {
                    return current.preserved == mergedPreserved ? current : intern(oldConditions, mergedPreserved);
                }
            }
            mergedConditions = Arrays.copyOf(oldConditions, oldConditions.length + 1);
            mergedConditions[oldConditions.length] = newRuntimeCondition;
        }
        return intern(mergedConditions, mergedPreserved);
    }

    public boolean isAlwaysAvailable() {
        return conditions == null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public EconomicSet<Class<?>> getTypesForEncoding() {
        if (conditions == null || satisfied) {
            return EconomicSet.emptySet();
        } else {
            EconomicSet<Class<?>> types = EconomicSet.create();
            for (Object condition : conditions) {
                types.addAll(getTypesForEncoding(condition));
            }
            return types;
        }
    }

    public static RuntimeDynamicAccessMetadata unmodifiableEmptyMetadata() {
        return UnmodifiableRuntimeDynamicAccessMetadata.UNMODIFIABLE_EMPTY_METADATA;
    }

    public static RuntimeDynamicAccessMetadata createDecoded(Object[] conditions, boolean preserved) {
        if (conditions == null || conditions.length == 0) {
            return alwaysAvailable(preserved);
        }
        return new RuntimeDynamicAccessMetadata(conditions, preserved);
    }

    /**
     * Checks if any of the conditions has been satisfied. Once a condition becomes satisfied, the
     * result is cached. This code can be concurrently executed, however there are no concurrency
     * primitives beyond the volatile cache. The implementation relies on the fact that checking if a
     * condition is satisfied is an idempotent operation.
     *
     * @return <code>true</code> if any of the elements is satisfied.
     */
    public boolean satisfied() {
        boolean result = satisfied;
        if (!result) {
            final var localConditions = conditions;
            if (localConditions == null) {
                satisfied = result = true;
            } else {
                for (Object condition : localConditions) {
                    if (isSatisfied(condition)) {
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

    /*
     * Used in snippets, returns true only if the condition was already satisfied beforehand.
     */
    public final boolean fastPathSatisfied() {
        return satisfied;
    }

    public boolean isPreserved() {
        return preserved;
    }

    @Override
    public String toString() {
        String conditionsString = this.conditions == null ? "[]" : Arrays.toString(this.conditions);
        return conditionsString + " = " + satisfied;
    }

    private RuntimeDynamicAccessMetadata(Object[] conditions, boolean preserved) {
        this.conditions = conditions == null || conditions.length == 0 ? null : canonicalConditions(conditions);
        this.satisfied = this.conditions == null;
        this.preserved = preserved;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static RuntimeDynamicAccessMetadata intern(Object[] conditions, boolean preserved) {
        Object[] canonicalConditions = canonicalConditions(conditions);
        MetadataKey key = new MetadataKey(canonicalConditions, preserved);
        return INTERNED.computeIfAbsent(key, _ -> new RuntimeDynamicAccessMetadata(canonicalConditions, preserved));
    }

    private static Object[] canonicalConditions(Object[] conditions) {
        Object[] result = Arrays.copyOf(conditions, conditions.length);
        Arrays.sort(result, (a, b) -> ((Class<?>) a).getName().compareTo(((Class<?>) b).getName()));
        return result;
    }

    private record MetadataKey(Object[] conditions, boolean preserved) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof MetadataKey other && preserved == other.preserved && Arrays.equals(conditions, other.conditions);
        }

        @Override
        public int hashCode() {
            return 31 * Boolean.hashCode(preserved) + Arrays.hashCode(conditions);
        }
    }

    private static Object createRuntimeCondition(AccessCondition cnd) {
        VMError.guarantee(cnd instanceof TypeReachabilityCondition, "Only TypeReachabilityCondition conditions can be used in RuntimeConditionSet.");
        TypeReachabilityCondition reachabilityCondition = (TypeReachabilityCondition) cnd;
        if (reachabilityCondition.isAlwaysTrue() || !reachabilityCondition.isRuntimeChecked()) {
            throw VMError.shouldNotReachHere("We should never create run-time conditions from conditions that are always true at build time. Condition: " + cnd);
        }
        return reachabilityCondition.getType();
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

    public static final class UnmodifiableRuntimeDynamicAccessMetadata extends RuntimeDynamicAccessMetadata {
        private static final RuntimeDynamicAccessMetadata UNMODIFIABLE_EMPTY_METADATA = new UnmodifiableRuntimeDynamicAccessMetadata(new Object[0]);

        private UnmodifiableRuntimeDynamicAccessMetadata(Object[] conditions) {
            super(conditions, false);
        }
    }

    public static final class AlwaysAvailableRuntimeDynamicAccessMetadata extends RuntimeDynamicAccessMetadata {
        private static final RuntimeDynamicAccessMetadata ALWAYS_AVAILABLE_NOT_PRESERVED = new AlwaysAvailableRuntimeDynamicAccessMetadata(false);
        private static final RuntimeDynamicAccessMetadata ALWAYS_AVAILABLE_PRESERVED = new AlwaysAvailableRuntimeDynamicAccessMetadata(true);

        private AlwaysAvailableRuntimeDynamicAccessMetadata(boolean preserved) {
            super(null, preserved);
        }
    }
}
