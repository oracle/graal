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
package com.oracle.svm.core.invoke;

import java.lang.invoke.MethodType;
import java.lang.reflect.Member;

import org.graalvm.nativeimage.MissingReflectionRegistrationError;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "java.lang.invoke.MemberName")
public final class Target_java_lang_invoke_MemberName {
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public Member reflectAccess;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public MethodHandleIntrinsic intrinsic;

    @Alias public String name;
    @Alias public int flags;
    @Alias public Object resolution;

    @Alias
    @SuppressWarnings("hiding")
    public native void init(Class<?> defClass, String name, Object type, int flags);

    @Alias
    public native boolean isStatic();

    @Alias
    public native boolean isMethod();

    @Alias
    public native boolean isConstructor();

    @Alias
    public native boolean isField();

    @Alias
    public native boolean isInvocable();

    @Alias
    public native Class<?> getDeclaringClass();

    @Alias
    public native MethodType getMethodType();

    @Alias
    public native Class<?> getFieldType();

    @Alias
    public native MethodType getInvocationType();

    @Alias
    public native byte getReferenceKind();

    @SuppressWarnings("static-method")
    @Substitute
    boolean vminfoIsConsistent() {
        return true; /* The substitution class doesn't use the same internals as the JDK */
    }

    @Alias
    @Override
    protected native Target_java_lang_invoke_MemberName clone();

    @Alias
    native void ensureTypeVisible(Class<?> refc);

    @Alias
    public native boolean isResolved();

    @Alias
    native boolean referenceKindIsConsistent();

    @Alias
    native void initResolved(boolean isResolved);
}

@TargetClass(className = "java.lang.invoke.MemberName", innerClass = "Factory")
final class Target_java_lang_invoke_MemberName_Factory {
    @Substitute
    @SuppressWarnings("static-method")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/b685ea54081fcf54a6567dddb49b63435a6e1ea4/src/java.base/share/classes/java/lang/invoke/MemberName.java#L937-L973")
    private Target_java_lang_invoke_MemberName resolve(byte refKind, Target_java_lang_invoke_MemberName ref, Class<?> lookupClass, int allowedModes,
                    boolean speculativeResolve) {
        Target_java_lang_invoke_MemberName m = ref.clone();
        assert (refKind == m.getReferenceKind());
        try {
            m = Target_java_lang_invoke_MethodHandleNatives.resolve(m, lookupClass, allowedModes, speculativeResolve);
            if (m == null) {
                VMError.guarantee(speculativeResolve, "non-speculative resolution should return member name or throw");
                return null;
            }
            m.ensureTypeVisible(m.getDeclaringClass());
            m.resolution = null;
        } catch (ClassNotFoundException | LinkageError ex) {
            if (ex instanceof MissingReflectionRegistrationError e) {
                /* Bypass the LinkageError catch below */
                throw e;
            }
            VMError.guarantee(m != null, "speculative resolution should not throw");
            assert (!m.isResolved());
            m.resolution = ex;
            return m;
        }
        assert (m.referenceKindIsConsistent());
        m.initResolved(true);
        assert (m.vminfoIsConsistent());
        return m;
    }
}
