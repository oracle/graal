/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config;

import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;

public final class FieldInfo {
    private static final FieldInfo[][] FINAL_NOT_WRITABLE_CACHE;
    static {
        ConfigurationMemberDeclaration[] declarations = ConfigurationMemberDeclaration.values();
        ConfigurationMemberAccessibility[] accessibilities = ConfigurationMemberAccessibility.values();
        FINAL_NOT_WRITABLE_CACHE = new FieldInfo[declarations.length][accessibilities.length];
        for (ConfigurationMemberDeclaration declaration : declarations) {
            for (ConfigurationMemberAccessibility accessibility : accessibilities) {
                FINAL_NOT_WRITABLE_CACHE[declaration.ordinal()][accessibility.ordinal()] = new FieldInfo(declaration, accessibility, false);
            }
        }
    }

    static FieldInfo get(ConfigurationMemberInfo kind, boolean finalButWritable) {
        return get(kind.getDeclaration(), kind.getAccessibility(), finalButWritable);
    }

    static FieldInfo get(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility, boolean finalButWritable) {
        if (finalButWritable) { // assumed to be rare
            return new FieldInfo(declaration, accessibility, finalButWritable);
        }
        return FINAL_NOT_WRITABLE_CACHE[declaration.ordinal()][accessibility.ordinal()];
    }

    private final ConfigurationMemberInfo kind;
    private final boolean finalButWritable;

    private FieldInfo(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility, boolean finalButWritable) {
        this.kind = ConfigurationMemberInfo.get(declaration, accessibility);
        this.finalButWritable = finalButWritable;
    }

    public FieldInfo newMergedWith(FieldInfo other) {
        assert kind.equals(other.kind);
        if (finalButWritable == other.finalButWritable) {
            return this;
        }
        return get(kind, finalButWritable || other.finalButWritable);
    }

    public FieldInfo newWithDifferencesFrom(FieldInfo other) {
        assert kind.equals(other.kind);
        boolean newFinalButWritable = finalButWritable && !other.finalButWritable;
        if (!newFinalButWritable) {
            return null;
        }
        return get(kind, newFinalButWritable);
    }

    public FieldInfo newIntersectedWith(FieldInfo other) {
        assert kind.equals(other.kind);
        boolean newFinalButWritable = finalButWritable && other.finalButWritable;
        return get(kind, newFinalButWritable);
    }

    public ConfigurationMemberInfo getKind() {
        return kind;
    }

    public boolean isFinalButWritable() {
        return finalButWritable;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != this && obj instanceof FieldInfo) {
            FieldInfo other = (FieldInfo) obj;
            return kind.equals(other.kind) && finalButWritable == other.finalButWritable;
        }
        return (obj == this);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(finalButWritable) * 31 + kind.hashCode();
    }
}
