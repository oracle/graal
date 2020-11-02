/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.jfc.JFC.nameFromPath;

import java.io.IOException;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.jfr.Configuration;
import jdk.jfr.internal.SecuritySupport;

@TargetClass(className = "jdk.jfr.internal.jfc.JFC$KnownConfiguration", onlyWith = JfrEnabled.class)
public final class Target_jdk_jfr_internal_jfc_JFC_KnownConfiguration {
    @Alias private String content;
    @Alias private String filename;
    @Alias private String name;
    @Alias private Configuration configuration;

    @Alias
    private static native String readContent(SecuritySupport.SafePath knownPath) throws IOException;

    @Substitute
    Target_jdk_jfr_internal_jfc_JFC_KnownConfiguration(SecuritySupport.SafePath knownPath) throws IOException {
        this.name = nameFromPath(knownPath.toPath());
        this.filename = Target_jdk_jfr_internal_jfc_JFC.nullSafeFileName(knownPath.toPath());
        this.configuration = PredefinedJFCSubstitition.knownKonfigurations.get(name);
        if (configuration == null) {
            content = readContent(knownPath);
        }
    }
}
