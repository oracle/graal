/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.headers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.BasedOnJDKFile;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+9/src/hotspot/share/prims/jvmti.xml")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+11/src/hotspot/share/prims/jvmtiH.xsl")
class JvmtiDirectives implements CContext.Directives {
    private final Path jdkIncludeDir = Paths.get(System.getProperty("java.home")).resolve("include");

    @Override
    public boolean isInConfiguration() {
        return SubstrateOptions.JVMTI.getValue();
    }

    @Override
    public List<String> getHeaderFiles() {
        return Collections.singletonList("\"" + jdkIncludeDir.resolve("jvmti.h") + "\"");
    }

    @Override
    public List<String> getOptions() {
        return Collections.singletonList("-I" + jdkIncludeDir.resolve(OS.getCurrent() == OS.WINDOWS ? "win32" : OS.getCurrent().asPackageName()));
    }
}
