/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;

@TargetClass(value = MethodHandles.class, innerClass = "Lookup")
final class Target_java_lang_invoke_MethodHandles_Lookup {
    // Checkstyle: stop
    @Delete //
    static ConcurrentHashMap<Target_java_lang_invoke_MemberName, MethodHandle> LOOKASIDE_TABLE;
    // Checkstyle: resume

    @SuppressWarnings("static-method")
    @Substitute
    public Class<?> defineClass(@SuppressWarnings("unused") byte[] bytes) {
        throw unimplemented("Defining new classes at runtime is not supported");
    }

    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    private MethodHandle maybeBindCaller(Target_java_lang_invoke_MemberName method, MethodHandle mh,
                    Target_java_lang_invoke_MethodHandles_Lookup boundCaller)
                    throws IllegalAccessException {
        /* Binding the caller triggers the generation of an invoker */
        return mh;
    }

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private Class<?> lookupClass;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private Class<?> prevLookupClass;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private int allowedModes;

    @Substitute
    private IllegalAccessException makeAccessException(Class<?> targetClass) {
        String message = "access violation: " + targetClass;
        if (this == SubstrateUtil.cast(MethodHandles.publicLookup(), Target_java_lang_invoke_MethodHandles_Lookup.class)) {
            message += ", from public Lookup";
        } else {
            Object m = SubstrateUtil.cast(lookupClass, DynamicHub.class).getModule();
            message += ", from " + lookupClass + " (" + m + ")";
            if (prevLookupClass != null) {
                message += ", previous lookup " +
                                prevLookupClass.getName() + " (" + SubstrateUtil.cast(prevLookupClass, DynamicHub.class).getModule() + ")";
            }
        }
        return new IllegalAccessException(message);
    }

    /** This call is a noop without the security manager. */
    @SuppressWarnings("unused")
    @Substitute
    void checkSecurityManager(Class<?> refc) {
    }
}
