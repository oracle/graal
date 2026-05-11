/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.misc.Unsafe;

@TargetClass(className = "java.lang.invoke.VarHandles")
final class Target_java_lang_invoke_VarHandles {

    /**
     * Resolves the static field in {@code declaringClass} whose static field offset is
     * {@code offset} and whose type is {@code fieldType}. Static fields are stored in separate
     * primitive and object Native Image storage arrays; see {@link StaticFieldsSupport}. Fields
     * using different arrays can therefore have the same numeric
     * {@link Unsafe#staticFieldOffset(Field) static field offset}.
     */
    @Substitute
    static Field getStaticFieldFromBaseAndOffset(Class<?> declaringClass, long offset, Class<?> fieldType) {
        Unsafe unsafe = Unsafe.getUnsafe();
        for (Field f : declaringClass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (offset == unsafe.staticFieldOffset(f) && f.getType() == fieldType) {
                return f;
            }
        }
        throw new InternalError("Static field not found at offset");
    }
}
