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
package com.oracle.svm.core.reflect.target;

import static com.oracle.svm.core.reflect.target.Util_java_lang_reflect_ReflectAccess.copyAccessibleObject;
import static com.oracle.svm.core.reflect.target.Util_java_lang_reflect_ReflectAccess.copyExecutable;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * These substitutions are needed to set the injected fields on Method, Field, Constructor. The
 * metadata for those fields is generated at image build time. The Method, Field, Constructor
 * elements, when accessed via reflection a copy is returned. The original implementation of copy()
 * doesn't propagate the injected fields.
 * 
 * We substitute these methods instead of the copy constructors on the reflection objects to avoid
 * having to copy the contents of those methods. This is ok since the ReflectAccess methods are the
 * only users of those copy constructors.
 */

@TargetClass(className = "java.lang.reflect.ReflectAccess")
public final class Target_java_lang_reflect_ReflectAccess {

    @Substitute
    @SuppressWarnings("static-method")
    public Target_java_lang_reflect_Method copyMethod(Target_java_lang_reflect_Method method) {
        Target_java_lang_reflect_Method copy = method.copy();
        copyExecutable(SubstrateUtil.cast(copy, Target_java_lang_reflect_Executable.class),
                        SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class));
        copy.methodAccessorFromMetadata = method.methodAccessorFromMetadata;
        return copy;
    }

    @Substitute
    @SuppressWarnings("static-method")
    public Target_java_lang_reflect_Field copyField(Target_java_lang_reflect_Field field) {
        Target_java_lang_reflect_Field copy = field.copy();
        copy.offset = field.offset;
        copy.deletedReason = field.deletedReason;
        copyAccessibleObject(SubstrateUtil.cast(copy, Target_java_lang_reflect_AccessibleObject.class),
                        SubstrateUtil.cast(field, Target_java_lang_reflect_AccessibleObject.class));
        return copy;
    }

    @Substitute
    @SuppressWarnings("static-method")
    public Target_java_lang_reflect_Constructor copyConstructor(Target_java_lang_reflect_Constructor constructor) {
        Target_java_lang_reflect_Constructor copy = constructor.copy();
        copyExecutable(SubstrateUtil.cast(copy, Target_java_lang_reflect_Executable.class),
                        SubstrateUtil.cast(constructor, Target_java_lang_reflect_Executable.class));
        return copy;
    }
}

class Util_java_lang_reflect_ReflectAccess {
    static void copyExecutable(Target_java_lang_reflect_Executable copy, Target_java_lang_reflect_Executable executable) {
        copy.rawParameters = executable.rawParameters;
        copyAccessibleObject(SubstrateUtil.cast(copy, Target_java_lang_reflect_AccessibleObject.class),
                        SubstrateUtil.cast(executable, Target_java_lang_reflect_AccessibleObject.class));
    }

    static void copyAccessibleObject(Target_java_lang_reflect_AccessibleObject copy, Target_java_lang_reflect_AccessibleObject accessibleObject) {
        copy.typeAnnotations = accessibleObject.typeAnnotations;
        copy.conditions = accessibleObject.conditions;
    }
}
