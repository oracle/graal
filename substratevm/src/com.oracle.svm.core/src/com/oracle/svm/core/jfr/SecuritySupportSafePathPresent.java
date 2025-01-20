/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

import java.util.function.BooleanSupplier;

/**
 * A predicate that returns {@code true} if {@code boolean jdk.jfr.internal.SecuritySupport.SafePath} exists.
 */
final class SecuritySupportSafePathPresent implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        try {
            Class.forName("jdk.jfr.internal.SecuritySupport$SafePath");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

final class SecuritySupportSafePathAbsent implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !new SecuritySupportSafePathPresent().getAsBoolean();
    }
}

@TargetClass(className = "jdk.jfr.internal.SecuritySupport$SafePath", onlyWith = SecuritySupportSafePathPresent.class)
final class Target_jdk_jfr_internal_SecuritySupport_SafePath {
    public Target_jdk_jfr_internal_SecuritySupport_SafePath(String p) {
        originalConstructor(p);
    }
    @Alias
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void originalConstructor(String p);
}
