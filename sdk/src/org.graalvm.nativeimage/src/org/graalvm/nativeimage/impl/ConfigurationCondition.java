/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.impl;

import java.util.Objects;

/**
 * A condition that describes if a reflectively-accessed element in Native Image is visible by the
 * user at run time.
 * <p>
 * Currently, there is only two types of condition:
 * <li><code>typeReached</code> (the default) that signifies that the type must be both reachable by
 * static analysis at build time, and reached at run time. A type is reached at run time, right
 * before the class-initialization routine starts for that type, or any of the type's subtypes are
 * reached.</li>
 * <li><code>typeReachable</code> (legacy) that signifies that the type must be reachable by static
 * analysis at build time.</li>
 * <p>
 * When {@link ConfigurationCondition#runtimeChecked} is <code>true</code> denotes that this is a
 * <code>typeReached</code> condition.
 */
public final class ConfigurationCondition {

    /* Cached to save space: it is used as a marker for all non-conditional elements */
    private static final ConfigurationCondition JAVA_LANG_OBJECT_REACHED = new ConfigurationCondition(Object.class, true);

    public static ConfigurationCondition alwaysTrue() {
        return JAVA_LANG_OBJECT_REACHED;
    }

    private final Class<?> type;

    private final boolean runtimeChecked;

    /**
     * Creates the default type-reached condition that is satisfied when the type is reached at
     * runtime.
     *
     * @param type that has to be reached for this condition to be satisfied
     * @return instance of the condition
     */
    public static ConfigurationCondition create(Class<?> type) {
        return create(type, true);
    }

    /**
     * Creates either a type-reached condition ({@code runtimeChecked = true}) or a type-reachable
     * condition.
     *
     * @param type that has to be reached (or reachable) for this condition to be satisfied
     * @param runtimeChecked makes this a type-reachable condition when false
     * @return instance of the condition
     */
    public static ConfigurationCondition create(Class<?> type, boolean runtimeChecked) {
        Objects.requireNonNull(type);
        if (JAVA_LANG_OBJECT_REACHED.getType().equals(type)) {
            return JAVA_LANG_OBJECT_REACHED;
        }
        return new ConfigurationCondition(type, runtimeChecked);
    }

    public boolean isAlwaysTrue() {
        return ConfigurationCondition.alwaysTrue().equals(this);
    }

    private ConfigurationCondition(Class<?> type, boolean runtimeChecked) {
        this.runtimeChecked = runtimeChecked;
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isRuntimeChecked() {
        return runtimeChecked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigurationCondition that = (ConfigurationCondition) o;
        return runtimeChecked == that.runtimeChecked && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, runtimeChecked);
    }

    @Override
    public String toString() {
        return "ConfigurationCondition(" +
                        "type=" + type +
                        ", runtimeChecked=" + runtimeChecked +
                        ')';
    }
}
