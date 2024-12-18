/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.interpreter.DebuggerSupport;
import com.oracle.svm.jdwp.bridge.ClassStatusConstants;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utility methods for ResolvedJavaType.
 */
public final class ClassUtils {

    private ClassUtils() {
        throw new UnsupportedOperationException("Do not instantiate");
    }

    /**
     * Returns a String representation of the name of the class.
     * 
     * @return the name of the class
     */
    public static String getNameAsString(ResolvedJavaType type) {
        return type.toClassName();
    }

    /**
     * Returns the String representation of the type of the class.
     * 
     * @return the class type descriptor name
     */
    public static String getTypeAsString(ResolvedJavaType type) {
        return type.getName(); // toJavaName();
    }

    /**
     * Returns the String representation of the generic type of the class.
     *
     * @return the generic class type descriptor name
     */
    public static String getGenericTypeAsString(@SuppressWarnings("unused") ResolvedJavaType type) {
        // TODO(peterssen): GR-55068 The JDWP debugger should provide generic type names and
        // signatures.
        // An empty string means no information available.
        return "";
    }

    /**
     * @return the status according to ClassStatusConstants of the class
     */
    public static int getStatus(@SuppressWarnings("unused") ResolvedJavaType type) {
        Class<?> clazz = DebuggerSupport.singleton().getUniverse().lookupClass(type);

        // Classes are always prepared and verified under closed-world assumptions.
        int status = ClassStatusConstants.PREPARED | ClassStatusConstants.VERIFIED;

        DynamicHub hub = DynamicHub.fromClass(clazz);
        if (hub.isLoaded()) {
            // Just for completeness since ClassStatusConstants.LOADED is 0.
            status |= ClassStatusConstants.LOADED;
        }
        if (hub.isInitialized()) {
            status |= ClassStatusConstants.INITIALIZED;
        }
        if (hub.getClassInitializationInfo().isInErrorState()) {
            status |= ClassStatusConstants.ERROR;
        }

        return status;
    }
}
