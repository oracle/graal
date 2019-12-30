/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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
package com.oracle.svm.core.jdk.serialize;

import java.security.AccessController;
import java.security.PrivilegedAction;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

@TargetClass(className = "sun.reflect.MethodAccessorGenerator", onlyWith = JDK8OrEarlier.class)
public final class Target_sun_reflect_MethodAccessorGenerator {
    @Alias private static volatile int constructorSymnum = 0;

    @Alias private static volatile int methodSymnum = 0;

    /**
     * Using a fixed name for the class that is supposed to be generated at runtime. The runtime
     * generated class must be dumped by Agent in an advanced run, and the classes must have been
     * added to the build time classpath.
     *
     * @param declaringClass
     * @param name
     * @param parameterTypes
     * @param returnType
     * @param checkedExceptions
     * @param modifiers
     * @param isConstructor
     * @param forSerialization
     * @param serializationTargetClass
     * @return A class cached in native-image heap
     */
    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    private Target_sun_reflect_MagicAccessorImpl generate(final Class<?> declaringClass, String name, Class<?>[] parameterTypes,
                    Class<?> returnType, Class<?>[] checkedExceptions, int modifiers, boolean isConstructor,
                    boolean forSerialization, Class<?> serializationTargetClass) {
        final String generatedName = MethodAccessorNameGenerator.generateClassName(isConstructor, forSerialization, declaringClass.getName());
        return AccessController.doPrivileged(new PrivilegedAction<Target_sun_reflect_MagicAccessorImpl>() {
            @Override
            public Target_sun_reflect_MagicAccessorImpl run() {
                try {
                    return (Target_sun_reflect_MagicAccessorImpl) ClassForNameSupport.forName(generatedName.replace('/', '.'), true).newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw new InternalError(e);
                }
            }
        });
    }
}

@TargetClass(className = "sun.reflect.MagicAccessorImpl")
final class Target_sun_reflect_MagicAccessorImpl {
}
