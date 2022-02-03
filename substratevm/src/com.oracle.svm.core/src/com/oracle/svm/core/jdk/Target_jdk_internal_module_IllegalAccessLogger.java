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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Avoid making the code for logging reachable. We do not need it, and it only increases code size.
 * If we ever want to enable logging, we also need to define a way to create the logger at run time,
 * in the JDK the logger is created as part of the module system bootstrapping.
 *
 * The logging code is only present in JDK 11, all logging was removed for JDK 17.
 */
@TargetClass(className = "jdk.internal.module.IllegalAccessLogger", onlyWith = JDK11OrEarlier.class)
final class Target_jdk_internal_module_IllegalAccessLogger {

    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private static Target_jdk_internal_module_IllegalAccessLogger logger;

    @Substitute
    private static Target_jdk_internal_module_IllegalAccessLogger illegalAccessLogger() {
        return null;
    }
}
