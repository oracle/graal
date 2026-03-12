/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * A method variant is an alternative implementation of the original Java method. This functionality
 * is used to create the different graph shapes based on the original graph needed for different
 * compilation scenarios.
 */
public interface MethodVariant {

    static boolean isOriginalMethod(ResolvedJavaMethod method) {
        if (method instanceof MethodVariant methodVariant) {
            return methodVariant.isOriginalMethod();
        }
        return false;
    }

    /**
     * Key for accessing a method variant.
     */
    interface MethodVariantKey {
    }

    MethodVariantKey ORIGINAL_METHOD = new MethodVariantKey() {
        @Override
        public String toString() {
            return "O";
        }
    };

    /**
     * Each method is assigned a unique key which denotes the purpose of the method.
     */
    MethodVariantKey getMethodVariantKey();

    /**
     * @return method implementation with the requested key, creating it if necessary.
     */
    MethodVariant getOrCreateMethodVariant(MethodVariantKey key);

    /**
     * @return method implementation with the requested key, or null if it does not exist.
     */
    MethodVariant getMethodVariant(MethodVariantKey key);

    /**
     * @return all implementations of this method.
     */
    Collection<MethodVariant> getAllMethodVariants();

    default boolean isOriginalMethod() {
        return getMethodVariantKey() == ORIGINAL_METHOD;
    }
}
