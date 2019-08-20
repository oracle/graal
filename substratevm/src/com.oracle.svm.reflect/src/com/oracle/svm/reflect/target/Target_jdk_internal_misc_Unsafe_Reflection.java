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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.Package_jdk_internal_misc;
import com.oracle.svm.core.util.VMError;

import java.lang.reflect.Field;

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "Unsafe")
@SuppressWarnings({"static-method"})
public final class Target_jdk_internal_misc_Unsafe_Reflection {

    @Substitute
    public long objectFieldOffset(Target_java_lang_reflect_Field field) {
        int offset = field.root == null ? field.offset : field.root.offset;
        if (offset > 0) {
            return offset;
        }
        throw VMError.unsupportedFeature("The offset of " + field + " is accessed without the field being first registered as unsafe accessed. " +
                        "Please register the field as unsafe accessed. You can do so with a reflection configuration that " +
                        "contains an entry for the field with the attribute \"allowUnsafeAccess\": true. Such a configuration " +
                        "file can be generated for you. Read CONFIGURE.md and REFLECTION.md for details.");
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    public long objectFieldOffset(Class<?> c, String name) {
        if (c == null || name == null) {
            throw new NullPointerException();
        }
        try {
            Field field = c.getDeclaredField(name);
            Target_java_lang_reflect_Field cast = SubstrateUtil.cast(field, Target_java_lang_reflect_Field.class);
            return objectFieldOffset(cast);
        } catch (NoSuchFieldException nse) {
            throw new InternalError();
        }
    }
}
