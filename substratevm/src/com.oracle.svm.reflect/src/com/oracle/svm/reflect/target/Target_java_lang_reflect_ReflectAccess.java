/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

// Checkstyle: allow reflection

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * These substitutions are needed to set the genericInfo field on Method, Field, Constructor. The
 * genericInfo is eagerly loaded at image build time. The Method, Field, Constructor elements, when
 * accessed via reflection a copy is returned. The original implementation of copy() doesn't
 * propagate the genericInfo.
 */

@TargetClass(className = "java.lang.reflect.ReflectAccess")
public final class Target_java_lang_reflect_ReflectAccess {

    @Substitute
    @SuppressWarnings("static-method")
    public Target_java_lang_reflect_Method copyMethod(Target_java_lang_reflect_Method method) {
        Target_java_lang_reflect_Method copy = method.copy();
        copy.genericInfo = method.genericInfo;
        return copy;
    }

    @Substitute
    @SuppressWarnings("static-method")
    public Target_java_lang_reflect_Field copyField(Target_java_lang_reflect_Field field) {
        Target_java_lang_reflect_Field copy = field.copy();
        copy.genericInfo = field.genericInfo;
        return copy;
    }

    @Substitute
    @SuppressWarnings("static-method")
    public Target_java_lang_reflect_Constructor copyConstructor(Target_java_lang_reflect_Constructor constructor) {
        Target_java_lang_reflect_Constructor copy = constructor.copy();
        copy.genericInfo = constructor.genericInfo;
        return copy;
    }
}
