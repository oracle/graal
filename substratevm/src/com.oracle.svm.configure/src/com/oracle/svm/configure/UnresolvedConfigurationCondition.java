/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import java.util.Objects;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

/**
 * Represents a {@link ConfigurationCondition} during parsing before it is resolved in a context of
 * the classpath.
 */
public final class UnresolvedConfigurationCondition implements Comparable<UnresolvedConfigurationCondition> {
    private static final UnresolvedConfigurationCondition JAVA_LANG_OBJECT_REACHED = new UnresolvedConfigurationCondition(
                    NamedConfigurationTypeDescriptor.fromTypeName(Object.class.getTypeName()), true);
    public static final String TYPE_REACHED_KEY = "typeReached";
    public static final String TYPE_REACHABLE_KEY = "typeReachable";
    private final NamedConfigurationTypeDescriptor type;
    private final boolean runtimeChecked;

    public static UnresolvedConfigurationCondition create(NamedConfigurationTypeDescriptor type) {
        return create(type, true);
    }

    public static UnresolvedConfigurationCondition create(NamedConfigurationTypeDescriptor type, boolean runtimeChecked) {
        Objects.requireNonNull(type);
        if (JAVA_LANG_OBJECT_REACHED.getTypeName().equals(type.name())) {
            return JAVA_LANG_OBJECT_REACHED;
        }
        return new UnresolvedConfigurationCondition(type, runtimeChecked);
    }

    private UnresolvedConfigurationCondition(NamedConfigurationTypeDescriptor type, boolean runtimeChecked) {
        this.type = type;
        this.runtimeChecked = runtimeChecked;
    }

    public static UnresolvedConfigurationCondition alwaysTrue() {
        return JAVA_LANG_OBJECT_REACHED;
    }

    public String getTypeName() {
        return type.name();
    }

    public boolean isRuntimeChecked() {
        return runtimeChecked;
    }

    public boolean isAlwaysTrue() {
        return getTypeName().equals(JAVA_LANG_OBJECT_REACHED.getTypeName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnresolvedConfigurationCondition that = (UnresolvedConfigurationCondition) o;
        return runtimeChecked == that.runtimeChecked && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, runtimeChecked);
    }

    @Override
    public int compareTo(UnresolvedConfigurationCondition o) {
        int res = Boolean.compare(runtimeChecked, o.runtimeChecked);
        if (res != 0) {
            return res;
        }
        return type.compareTo(o.type);
    }

    @Override
    public String toString() {
        var field = runtimeChecked ? TYPE_REACHED_KEY : TYPE_REACHABLE_KEY;
        return "[" + field + ": \"" + getTypeName() + "\"" + "]";
    }

}
