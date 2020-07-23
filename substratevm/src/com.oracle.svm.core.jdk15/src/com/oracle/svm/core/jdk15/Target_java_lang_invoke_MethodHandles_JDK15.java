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
package com.oracle.svm.core.jdk15;

import java.lang.invoke.MethodHandles;
// Checkstyle: stop
import sun.invoke.util.VerifyAccess;
// Checkstyle: resume
import jdk.internal.misc.Unsafe;

import com.oracle.svm.core.jdk.JDK15OrLater;
import com.oracle.svm.core.jdk.JDK15OrEarlier;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.invoke.MethodHandles", innerClass = "Lookup", onlyWith = {JDK15OrLater.class, JDK15OrEarlier.class})
final class Target_java_lang_invoke_MethodHandles_Lookup_JDK15 {

    @Alias //
    private Class<?> lookupClass;

    @Alias //
    private Class<?> prevLookupClass;

    @Alias //
    private int allowedModes;

    @Substitute
    public Class<?> lookupClass() {
        return lookupClass;
    }

    /**
     * Eliminate the dependencies on MemberName.
     */
    @Substitute
    public Class<?> ensureInitialized(Class<?> targetClass) throws IllegalAccessException {
        if (targetClass.isPrimitive()) {
            throw new IllegalArgumentException(targetClass + " is a primitive class");
        }
        if (targetClass.isArray()) {
            throw new IllegalArgumentException(targetClass + " is an array class");
        }
        if (!VerifyAccess.isClassAccessible(targetClass, lookupClass, prevLookupClass, allowedModes)) {
            // throw new MemberName(targetClass).makeAccessException("access violation", this);
            String message = "access violation: " + targetClass;
            if (this == SubstrateUtil.cast(MethodHandles.publicLookup(), Target_java_lang_invoke_MethodHandles_Lookup_JDK15.class)) {
                message += ", from public Lookup";
            } else {
                Module m = lookupClass().getModule();
                message += ", from " + lookupClass() + " (" + m + ")";
                if (prevLookupClass != null) {
                    message += ", previous lookup " +
                                    prevLookupClass.getName() + " (" + prevLookupClass.getModule() + ")";
                }
            }
            throw new IllegalAccessException(message);
        }

        // This call is a noop without the security manager
        // checkSecurityManager(targetClass, null);

        // ensure class initialization
        Unsafe.getUnsafe().ensureClassInitialized(targetClass);
        return targetClass;
    }

}

public class Target_java_lang_invoke_MethodHandles_JDK15 {
}
