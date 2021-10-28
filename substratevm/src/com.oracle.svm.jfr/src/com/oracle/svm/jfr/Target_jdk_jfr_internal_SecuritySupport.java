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
package com.oracle.svm.jfr;

import java.util.List;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

import jdk.jfr.internal.SecuritySupport.SafePath;

@TargetClass(value = jdk.jfr.internal.SecuritySupport.class, onlyWith = JfrEnabled.class)
public final class Target_jdk_jfr_internal_SecuritySupport {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static SafePath JFC_DIRECTORY;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static SafePath USER_HOME;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static SafePath JAVA_IO_TMPDIR;
    // Checkstyle: resume

    @Substitute
    public static List<SafePath> getPredefinedJFCFiles() {
        throw VMError.shouldNotReachHere("Paths from the image build must not be embedded into the Native Image.");
    }

    @Alias
    static native SafePath getPathInProperty(String prop, String subPath);
}
