/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.common.meta;

import java.util.Collection;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Multi-methods can have multiple implementations of the original Java method. This functionality
 * is used to create the different variants needed for different compilation scenarios.
 */
public interface MultiMethod {

    static boolean isOriginalMethod(ResolvedJavaMethod method) {
        if (method instanceof MultiMethod multiMethod) {
            return multiMethod.isOriginalMethod();
        }
        return false;
    }

    /**
     * Key for accessing a multi-method.
     */
    interface MultiMethodKey {
    }

    MultiMethodKey ORIGINAL_METHOD = new MultiMethodKey() {
        @Override
        public String toString() {
            return "O";
        }
    };

    /**
     * Each method is assigned a unique key which denotes the purpose of the method.
     */
    MultiMethodKey getMultiMethodKey();

    /**
     * @return method implementation with the requested key, creating it if necessary.
     */
    MultiMethod getOrCreateMultiMethod(MultiMethodKey key);

    /**
     * @return method implementation with the requested key, or null if it does not exist.
     */
    MultiMethod getMultiMethod(MultiMethodKey key);

    /**
     * @return all implementations of this method.
     */
    Collection<MultiMethod> getAllMultiMethods();

    default boolean isOriginalMethod() {
        return getMultiMethodKey() == ORIGINAL_METHOD;
    }
}
