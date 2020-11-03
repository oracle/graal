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
package com.oracle.svm.methodhandles;

// Checkstyle: stop
import java.lang.reflect.Member;
// Checkstyle: resume

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.invoke.MemberName", onlyWith = MethodHandlesSupported.class)
public final class Target_java_lang_invoke_MemberName {
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Member reflectAccess;

    @Alias public String name;
    @Alias public Object type;
    @Alias public int flags;

    @Alias
    public native boolean isStatic();

    @Alias
    public native boolean isPublic();

    @Alias
    public native boolean isPrivate();

    @Alias
    public native boolean isProtected();

    @Alias
    public native boolean isFinal();

    @Alias
    public native boolean canBeStaticallyBound();

    @Alias
    public native boolean isVolatile();

    @Alias
    public native boolean isAbstract();

    @Alias
    public native boolean isNative();

    @Alias
    public native boolean isInvocable();

    @Alias
    public native boolean isFieldOrMethod();

    @Alias
    public native boolean isMethod();

    @Alias
    public native boolean isConstructor();

    @Alias
    public native boolean isField();

    @Alias
    public native boolean isType();

    @Alias
    public native boolean isPackage();

    @Alias
    public native boolean isCallerSensitive();

    @Alias
    public native Class<?> getDeclaringClass();

    @Alias
    public native byte getReferenceKind();
}

@TargetClass(className = "java.lang.invoke.MemberName", onlyWith = MethodHandlesNotSupported.class)
@Delete("All methods from java.lang.invoke should have been replaced during image building.")
final class Target_java_lang_invoke_MemberName_NotSupported {
}
