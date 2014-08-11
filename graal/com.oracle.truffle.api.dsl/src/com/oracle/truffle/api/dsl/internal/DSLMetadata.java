/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.internal;

/**
 * This is NOT public API. Do not use directly. This code may change without notice.
 */
public final class DSLMetadata {

    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[]{};
    public static final DSLMetadata NONE = new DSLMetadata(null, EMPTY_CLASS_ARRAY, EMPTY_CLASS_ARRAY, EMPTY_CLASS_ARRAY, 0, 0);

    private final Class<?> specializationClass;
    private final Class<?>[] includes;
    private final Class<?>[] excludedBy;
    private final Class<?>[] specializedTypes;

    private final int costs;
    private final int order;

    public DSLMetadata(Class<?> specializationClass, Class<?>[] includes, Class<?>[] excludes, Class<?>[] specializedTypes, int costs, int order) {
        this.specializationClass = specializationClass;
        this.includes = includes;
        this.excludedBy = excludes;
        this.specializedTypes = specializedTypes;
        this.costs = costs;
        this.order = order;
    }

    public Class<?> getSpecializationClass() {
        return specializationClass;
    }

    public Class<?>[] getSpecializedTypes() {
        return specializedTypes;
    }

    Class<?>[] getIncludes() {
        return includes;
    }

    Class<?>[] getExcludedBy() {
        return excludedBy;
    }

    int getCosts() {
        return costs;
    }

    int getOrder() {
        return order;
    }
}
