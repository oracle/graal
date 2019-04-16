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

public enum ConfigurationMemberKind {
    /** The member is public and declared in the type in question. */
    DECLARED_AND_PUBLIC,

    /** The member is declared in the type in question. */
    DECLARED,

    /** The member is public and is either declared or inherited in the type in question. */
    PUBLIC,

    /** The member is either declared or inherited in the type in question. */
    PRESENT;

    private boolean isMoreSpecificThan(ConfigurationMemberKind other) {
        return other == null || ordinal() < other.ordinal();
    }

    public ConfigurationMemberKind intersect(ConfigurationMemberKind other) {
        if (equals(DECLARED) && PUBLIC.equals(other) || equals(PUBLIC) && DECLARED.equals(other)) {
            return DECLARED_AND_PUBLIC;
        }
        return this.isMoreSpecificThan(other) ? this : other;
    }

    public ConfigurationMemberKind union(ConfigurationMemberKind other) {
        return equals(other) ? this : PRESENT;
    }

    public boolean includes(ConfigurationMemberKind other) {
        if (equals(DECLARED_AND_PUBLIC)) {
            return DECLARED.equals(other) || PUBLIC.equals(other);
        }
        if (equals(PRESENT)) {
            return true;
        }
        return equals(other);
    }
}
