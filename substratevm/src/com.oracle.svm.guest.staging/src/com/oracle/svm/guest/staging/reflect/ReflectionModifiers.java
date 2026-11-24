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
package com.oracle.svm.guest.staging.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.oracle.svm.guest.staging.reflect.target.Target_java_lang_reflect_Constructor;
import com.oracle.svm.guest.staging.reflect.target.Target_java_lang_reflect_Field;
import com.oracle.svm.guest.staging.reflect.target.Target_java_lang_reflect_Method;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

/** Reads raw class-file modifiers before the reflection facade masks implementation bits. */
public final class ReflectionModifiers {
    private ReflectionModifiers() {
    }

    public static int getRawModifiers(Field field) {
        assert field != null;
        return SubstrateUtil.cast(field, Target_java_lang_reflect_Field.class).modifiers;
    }

    public static int getRawModifiers(Method method) {
        assert method != null;
        return SubstrateUtil.cast(method, Target_java_lang_reflect_Method.class).modifiers;
    }

    public static int getRawModifiers(Constructor<?> constructor) {
        assert constructor != null;
        return SubstrateUtil.cast(constructor, Target_java_lang_reflect_Constructor.class).modifiers;
    }

    public static int getRawModifiers(Executable executable) {
        assert executable != null;
        if (executable instanceof Method method) {
            return getRawModifiers(method);
        } else if (executable instanceof Constructor<?> constructor) {
            return getRawModifiers(constructor);
        } else {
            throw VMError.shouldNotReachHere("Unexpected executable type");
        }
    }
}
