/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.dcmd;

public class DCmdOption<T> {
    private final Class<T> type;
    private final String name;
    private final String description;
    private final boolean required;
    private final T defaultValue;

    public DCmdOption(Class<T> type, String name, String description, boolean required, T defaultValue) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public Class<?> getType() {
        return type;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    boolean isRequired() {
        return required;
    }

    T getDefaultValue() {
        return defaultValue;
    }
}
