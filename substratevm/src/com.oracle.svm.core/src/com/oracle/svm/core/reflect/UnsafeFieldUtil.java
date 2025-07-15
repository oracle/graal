/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Field;

import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_AccessibleObject;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_Field;

public class UnsafeFieldUtil {
    public static long getFieldOffset(Target_java_lang_reflect_Field field) {
        if (field == null) {
            throw new NullPointerException();
        }
        if (MetadataTracer.enabled()) {
            traceFieldAccess(SubstrateUtil.cast(field, Field.class));
        }
        int offset = field.root == null ? field.offset : field.root.offset;
        boolean conditionsSatisfied = SubstrateUtil.cast(field, Target_java_lang_reflect_AccessibleObject.class).conditions.satisfied();
        if (offset <= 0 || !conditionsSatisfied) {
            throw MissingReflectionRegistrationUtils.reportAccessedField(SubstrateUtil.cast(field, Field.class));
        }
        return offset;
    }

    private static void traceFieldAccess(Field f) {
        MetadataTracer.singleton().traceFieldAccess(f.getDeclaringClass(), f.getName(), ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
    }
}
