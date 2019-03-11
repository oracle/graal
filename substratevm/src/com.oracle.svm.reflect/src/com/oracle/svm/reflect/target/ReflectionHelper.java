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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

class ReflectionHelper {
    static Target_java_lang_reflect_Executable getHolder(Target_java_lang_reflect_Executable executable) {
        Target_java_lang_reflect_Executable holder = getRoot(executable);
        if (holder == null) {
            holder = executable;
        }
        return holder;
    }

    static Target_java_lang_reflect_Constructor getHolder(Target_java_lang_reflect_Constructor constructor) {
        Target_java_lang_reflect_Constructor holder = asConstructor(getRoot(asExecutable(constructor)));
        if (holder == null) {
            holder = constructor;
        }

        return holder;
    }

    static Target_java_lang_reflect_Field getHolder(Target_java_lang_reflect_Field field) {
        Target_java_lang_reflect_Field holder = field.root;
        if (holder == null) {
            holder = field;
        }

        return holder;
    }

    static <T> T requireNonNull(T object, String errorMessage) {
        if (object == null) {
            throw VMError.shouldNotReachHere(errorMessage);
        }
        return object;
    }

    private static Target_java_lang_reflect_Constructor asConstructor(Target_java_lang_reflect_Executable executable) {
        return KnownIntrinsics.unsafeCast(executable, Target_java_lang_reflect_Constructor.class);
    }

    private static Target_java_lang_reflect_Executable asExecutable(Object executable) {
        return KnownIntrinsics.unsafeCast(executable, Target_java_lang_reflect_Executable.class);
    }

    private static Target_java_lang_reflect_Executable getRoot(Target_java_lang_reflect_Executable executable) {
        if (JavaVersionUtil.Java8OrEarlier) {
            return executable.getRoot();
        } else {
            return asExecutable(KnownIntrinsics.unsafeCast(executable, Target_java_lang_reflect_AccessibleObject.class).getRoot());
        }
    }
}
