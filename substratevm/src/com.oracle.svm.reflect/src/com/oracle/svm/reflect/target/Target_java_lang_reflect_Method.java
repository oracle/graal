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

// Checkstyle: allow reflection

import java.lang.reflect.Method;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.reflect.hosted.AccessorComputer;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.reflect.generics.repository.MethodRepository;

@TargetClass(value = Method.class)
public final class Target_java_lang_reflect_Method {

    @Alias MethodRepository genericInfo;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = AccessorComputer.class) //
    Target_jdk_internal_reflect_MethodAccessor methodAccessor;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = DefaultValueComputer.class) //
    Object defaultValue;

    @Alias
    native Target_java_lang_reflect_Method copy();

    @Substitute
    Target_jdk_internal_reflect_MethodAccessor acquireMethodAccessor() {
        if (methodAccessor == null) {
            throw VMError.unsupportedFeature("Runtime reflection is not supported.");
        }
        return methodAccessor;
    }

    @Substitute
    public Object getDefaultValue() {
        Target_java_lang_reflect_Method holder = ReflectionHelper.getHolder(this);
        return holder.defaultValue;
    }

    public static final class DefaultValueComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Method method = (Method) receiver;
            return method.getDefaultValue();
        }
    }

}
