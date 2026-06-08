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
import static com.oracle.svm.core.configure.ConfigurationFiles.Options.TrackConditionSatisfied;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.StringJoiner;
import java.util.Set;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.TypeReachabilityCondition;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.util.json.JsonWriter;

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

    private Object[] conditions;
    private boolean satisfied;
    private volatile boolean preserved;

    public static RuntimeDynamicAccessMetadata alwaysAllow(boolean preserved) {
        return new RuntimeDynamicAccessMetadata(new Object[0], preserved);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeDynamicAccessMetadata createHosted(AccessCondition condition, boolean preserved) {
        var metadata = new RuntimeDynamicAccessMetadata(new Object[0], preserved);
        metadata.addCondition(condition);
        return metadata;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void addCondition(AccessCondition cnd) {
        VMError.guarantee(cnd instanceof TypeReachabilityCondition, "Only TypeReachabilityCondition conditions can be used in RuntimeConditionSet.");
        TypeReachabilityCondition reachabilityCondition = (TypeReachabilityCondition) cnd;
        VMError.guarantee(reachabilityCondition.isRuntimeChecked(), "Only runtime conditions can be added to the ConditionalRuntimeValue.");
        if (satisfied) {
            maybeTrackConditionSatisfied(reachabilityCondition, true);
            return;
        } else if (reachabilityCondition.isAlwaysTrue()) {
            maybeTrackConditionSatisfied(conditions, true);

            conditions = null;
            satisfied = true;
            return;
        }

        Object newRuntimeCondition = createRuntimeCondition(cnd);
        Set<Object> existingConditions = conditions == null ? new HashSet<>() : new HashSet<>(Arrays.asList(conditions)); // noEconomicSet(temp)
        existingConditions.add(newRuntimeCondition);
        setConditions(existingConditions.toArray());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public EconomicSet<Class<?>> getTypesForEncoding() {
        if (conditions == null) {
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
        return new RuntimeDynamicAccessMetadata(conditions, preserved);
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
                        maybeTrackConditionSatisfied(condition);
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

    private static void maybeTrackConditionSatisfied(Object condition) {
        maybeTrackConditionSatisfied(condition, false);
    }

    /**
     * Logs satisfied conditions matching {@link #trackConditionSatisfied()}. The option is read and
     * checked before inspecting the condition so the disabled case does not pay for type formatting or
     * array traversal.
     */
    private static void maybeTrackConditionSatisfied(Object condition, boolean ignoredAtBuildTime) {
        String trackedType = trackConditionSatisfied();
        if (trackedType == null || condition == null) {
            return;
        }
        maybeTrackConditionSatisfied(condition, ignoredAtBuildTime, trackedType);
    }

    private static void maybeTrackConditionSatisfied(Object condition, boolean ignoredAtBuildTime, String trackedType) {
        if (condition == null) {
            return;
        } else if (condition instanceof Object[] conditionsArray) {
            for (Object singleCondition : conditionsArray) {
                maybeTrackConditionSatisfied(singleCondition, ignoredAtBuildTime, trackedType);
            }
            return;
        }

        String typeName = null;
        if (condition instanceof Class<?> reachedTypeCondition) {
            typeName = reachedTypeCondition.getTypeName();
        } else if (condition instanceof TypeReachabilityCondition reachedTypeCondition) {
            typeName = reachedTypeCondition.getType().getTypeName();
        }
        if (trackedType != null && typeName != null && ("*".equals(trackedType) || typeName.equals(trackedType)) && !"java.lang.Object".equals(typeName)) {
            if (ignoredAtBuildTime) {
                LogUtils.info("Tracked runtime condition reached at build time and ignored: type = " + typeName);
            } else {
                LogUtils.info("Tracked runtime condition reached: type = " + typeName);
            }
        }
    }

    @Fold
    public static String trackConditionSatisfied() {
        return TrackConditionSatisfied.getValue();
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

    /**
     * Returns a user-facing description of unresolved runtime conditions.
     */
    public String formatUnsatisfiedConditions() {
        Object[] localConditions = conditions;
        if (satisfied || localConditions == null) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (Object condition : localConditions) {
            String formattedCondition = formatUnsatisfiedCondition(condition);
            if (formattedCondition != null) {
                joiner.add(formattedCondition);
            }
        }
        return joiner.toString();
    }

    /**
     * Returns unresolved runtime conditions formatted as JSON.
     */
    public String formatUnsatisfiedConditionsAsJson() {
        Object[] localConditions = conditions;
        if (satisfied || localConditions == null) {
            return "";
        }

        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        boolean wroteAny = false;
        for (Object condition : localConditions) {
            if (wroteAny) {
                builder.append(lineSeparator);
            }
            appendUnsatisfiedConditionAsJson(builder, condition);
            wroteAny = true;
        }
        if (!wroteAny) {
            return "";
        }
        /*
         * Remove trailing line separator to keep spacing deterministic in user-facing messages.
         */
        builder.setLength(builder.length() - lineSeparator.length());
        return builder.toString();
    }

    private static String formatUnsatisfiedCondition(Object condition) {
        if (condition instanceof Class<?> reachedTypeCondition) {
            return "typeReached(" + DynamicHub.fromClass(reachedTypeCondition).getTypeName() + ")";
        }
        throw VMError.shouldNotReachHere("Only typeReached condition is supported.");
    }

    private static void appendUnsatisfiedConditionAsJson(StringBuilder builder, Object condition) {
        if (condition instanceof Class<?> reachedTypeCondition) {
            builder.append("    ").append(quoteJsonString("typeReached")).append(": ").append(quoteJsonString(DynamicHub.fromClass(reachedTypeCondition).getTypeName()));
        } else {
            throw VMError.shouldNotReachHere("Only typeReached condition is supported.");
        }
        builder.append(System.lineSeparator());
    }

    private static String quoteJsonString(String value) {
        try (StringWriter stringWriter = new StringWriter(); JsonWriter writer = new JsonWriter(stringWriter)) {
            writer.quote(value);
            return stringWriter.toString();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Writing JSON to memory", e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setNotPreserved() {
        this.preserved = false;
    }

    @Override
    public String toString() {
        String conditionsString = this.conditions == null ? "[]" : Arrays.toString(this.conditions);
        return conditionsString + " = " + satisfied;
    }

    private RuntimeDynamicAccessMetadata(Object[] conditions, boolean preserved) {
        setConditions(conditions);
        this.preserved = preserved;
    }

    private void setConditions(Object[] conditions) {
        if (conditions.length == 0) {
            this.conditions = null;
        } else {
            this.conditions = conditions;
        }
        satisfied = false;
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

        @Override
        public synchronized void addCondition(AccessCondition cnd) {
            throw new UnsupportedOperationException("Can't add conditions to an unmodifiable set of conditions.");
        }
    }
}
