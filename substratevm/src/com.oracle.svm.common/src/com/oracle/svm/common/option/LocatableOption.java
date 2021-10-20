/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.common.option;

public final class LocatableOption {

    final String name;
    final String origin;

    static LocatableOption from(String rawOptionName) {
        return new LocatableOption(rawOptionName);
    }

    private LocatableOption(String rawOptionName) {
        int annotationIndex = rawOptionName.indexOf('@');
        if (annotationIndex != -1) {
            name = rawOptionName.substring(0, annotationIndex);
            origin = rawOptionName.substring(annotationIndex + 1);
        } else {
            name = rawOptionName;
            origin = null;
        }
    }

    @Override
    public String toString() {
        String result = "'" + name + "'";
        if (origin == null) {
            return result;
        }
        return result + " from '" + origin + "'";
    }

    private static final class LocatableOptionValue {
        private final Object value;
        private final String origin;

        private LocatableOptionValue(Object value, String origin) {
            this.value = value;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return "LocatableOptionValue(" + value + ", " + origin + ')';
        }
    }

    public static Object value(Object value, String origin) {
        return new LocatableOptionValue(value, origin);
    }

    public static Object value(Object value) {
        return value(value, null);
    }

    public static Object rawValue(Object value) {
        if (value instanceof LocatableOptionValue) {
            return ((LocatableOptionValue) value).value;
        }
        return value;
    }

    public static String valueOrigin(Object value) {
        if (value instanceof LocatableOptionValue) {
            return ((LocatableOptionValue) value).origin;
        }
        return null;
    }
}
