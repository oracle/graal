/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

public final class ConfigurationMemberInfo {
    private static final ConfigurationMemberInfo[][] cache = new ConfigurationMemberInfo[ConfigurationMemberDeclaration.values().length][ConfigurationMemberAccessibility.values().length];

    static {
        for (ConfigurationMemberDeclaration memberKind : ConfigurationMemberDeclaration.values()) {
            for (ConfigurationMemberAccessibility accessKind : ConfigurationMemberAccessibility.values()) {
                cache[memberKind.ordinal()][accessKind.ordinal()] = new ConfigurationMemberInfo(memberKind, accessKind);
            }
        }
    }

    private final ConfigurationMemberDeclaration declaration;
    private final ConfigurationMemberAccessibility accessibility;

    private ConfigurationMemberInfo(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility) {
        this.declaration = declaration;
        this.accessibility = accessibility;
    }

    public ConfigurationMemberDeclaration getDeclaration() {
        return declaration;
    }

    public ConfigurationMemberAccessibility getAccessibility() {
        return accessibility;
    }

    public static ConfigurationMemberInfo get(ConfigurationMemberDeclaration memberKind, ConfigurationMemberAccessibility accessKind) {
        return cache[memberKind.ordinal()][accessKind.ordinal()];
    }

    public enum ConfigurationMemberDeclaration {
        /**
         * The member is public and declared in the type in question.
         */
        DECLARED_AND_PUBLIC,

        /**
         * The member is declared in the type in question.
         */
        DECLARED,

        /**
         * The member is public and is either declared or inherited in the type in question.
         */
        PUBLIC,

        /**
         * The member is either declared or inherited in the type in question.
         */
        PRESENT;

        private boolean isMoreSpecificThan(ConfigurationMemberDeclaration other) {
            return other == null || ordinal() < other.ordinal();
        }

        public ConfigurationMemberDeclaration intersect(ConfigurationMemberDeclaration other) {
            if (equals(DECLARED) && PUBLIC.equals(other) || equals(PUBLIC) && DECLARED.equals(other)) {
                return DECLARED_AND_PUBLIC;
            }
            return this.isMoreSpecificThan(other) ? this : other;
        }

        private ConfigurationMemberDeclaration union(ConfigurationMemberDeclaration other) {
            return equals(other) ? this : PRESENT;
        }

        public boolean includes(ConfigurationMemberDeclaration other) {
            return switch (this) {
                case PRESENT -> true;
                case DECLARED_AND_PUBLIC -> other == DECLARED || other == PUBLIC;
                default -> this == other;
            };
        }
    }

    public enum ConfigurationMemberAccessibility {
        /**
         * The member is not accessed reflectively.
         */
        NONE,

        /**
         * The member is queried reflectively but never invoked (only for methods and constructors).
         */
        QUERIED,

        /**
         * The member is fully accessed reflectively.
         */
        ACCESSED;

        public ConfigurationMemberAccessibility combine(ConfigurationMemberAccessibility other) {
            return other.includes(this) ? other : this;
        }

        public ConfigurationMemberAccessibility intersect(ConfigurationMemberAccessibility other) {
            return other.includes(this) ? this : other;
        }

        public ConfigurationMemberAccessibility remove(ConfigurationMemberAccessibility other) {
            return other.includes(this) ? NONE : this;
        }

        public boolean includes(ConfigurationMemberAccessibility other) {
            return ordinal() >= other.ordinal();
        }
    }

    public ConfigurationMemberInfo intersect(ConfigurationMemberInfo other) {
        return get(declaration.intersect(other.declaration), accessibility.combine(other.accessibility));
    }

    public ConfigurationMemberInfo union(ConfigurationMemberInfo other) {
        return get(declaration.union(other.declaration), accessibility.combine(other.accessibility));
    }

    public boolean includes(ConfigurationMemberInfo other) {
        return declaration.includes(other.declaration) && accessibility.includes(other.accessibility);
    }
}
