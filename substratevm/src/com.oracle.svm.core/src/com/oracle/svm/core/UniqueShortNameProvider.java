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
package com.oracle.svm.core;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * An abstract provider class that defines a protocol for generating unique short names for Java
 * methods and fields which are compatible for use as local linker symbols. An instance of this
 * class may be registered as an image singleton in order to ensure that a specific naming
 * convention is adopted.
 */
public interface UniqueShortNameProvider {
    static UniqueShortNameProvider singleton() {
        return ImageSingletons.lookup(UniqueShortNameProvider.class);
    }

    /**
     * Returns a short, reasonably descriptive, but still unique name for a method as characterized
     * by its loader name, owner class, method selector, signature and status as a method proper or
     * a constructor.
     * 
     * @param loader The method's class loader
     * @param declaringClass The class which owns the method
     * @param methodName The name of the method
     * @param methodSignature The method's signature
     * @param isConstructor True if the method is a constructor otherwise false
     * @return A unique short name for the method
     */
    String uniqueShortName(ClassLoader loader, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor);

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided
     * {@link Method}, {@link Constructor}, or {@link Field}.
     * 
     * @param m The member whose short name is required
     * @return A unique short name for the member
     */
    String uniqueShortName(Member m);

    /**
     * Returns a unique short name for the supplied class loader or an empty string for any loader
     * whose delegation policy will not be responsible for class duplication.
     * 
     * @param classLoader the loader whose unique short name is desired
     * @return a unique short name for the loader or an empty string
     */
    String uniqueShortLoaderName(ClassLoader classLoader);
}
