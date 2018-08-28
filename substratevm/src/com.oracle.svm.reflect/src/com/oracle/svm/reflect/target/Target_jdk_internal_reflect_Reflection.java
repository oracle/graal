/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.target;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.reflect.hosted.ReflectionFeature;

@TargetClass(classNameProvider = Package_jdk_internal_reflect.class, className = "Reflection", onlyWith = ReflectionFeature.IsEnabled.class)
public final class Target_jdk_internal_reflect_Reflection {

    @Substitute
    public static Class<?> getCallerClass() {
        return null;
    }

    @Substitute
    public static int getClassAccessFlags(Class<?> cls) {
        return cls.getModifiers();
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    @SuppressWarnings({"unused"})
    public static /* native */ boolean areNestMates(Class<?> currentClass, Class<?> memberClass) {
        throw VMError.unsupportedFeature("JDK9OrLater: Target_jdk_internal_reflect_Reflection.areNestMates");
    }
}
