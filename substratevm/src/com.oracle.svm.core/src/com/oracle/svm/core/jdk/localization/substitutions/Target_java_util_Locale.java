/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.substitutions;

import java.util.Locale;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(java.util.Locale.class)
final class Target_java_util_Locale {
    @Alias @InjectAccessors(DefaultLocaleAccessors.class) //
    private static Locale defaultLocale;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Locale defaultDisplayLocale;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Locale defaultFormatLocale;

    @Alias
    private static native Locale initDefault();

    @SuppressWarnings("unused")
    private static final class DefaultLocaleAccessors {
        static Locale get() {
            if (Util_java_util_Locale.injectedDefaultLocale == null) {
                Util_java_util_Locale.injectedDefaultLocale = Target_java_util_Locale.initDefault();
            }
            return Util_java_util_Locale.injectedDefaultLocale;
        }

        static void set(Locale defaultLocale) {
            Util_java_util_Locale.injectedDefaultLocale = defaultLocale;
        }
    }
}

final class Util_java_util_Locale {
    static Locale injectedDefaultLocale;
}
