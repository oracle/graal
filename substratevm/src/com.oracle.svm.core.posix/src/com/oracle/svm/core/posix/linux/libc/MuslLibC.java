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
package com.oracle.svm.core.posix.linux.libc;

import java.util.Collections;
import java.util.List;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.util.UserError;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

public class MuslLibC implements LibCBase {

    public MuslLibC() {
        if (!SubstrateOptions.StaticExecutable.getValue()) {
            throw UserError.abort("Musl can only be used for statically linked executables.");
        }
        if (JavaVersionUtil.JAVA_SPEC != 11) {
            throw UserError.abort("Musl can only be used with labsjdk 11.");
        }
    }

    public static final String NAME = "musl";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<String> getAdditionalQueryCodeCompilerOptions() {
        /* Avoid the dependency to muslc for builds cross compiling to muslc. */
        return Collections.singletonList("--static");
    }

    @Override
    public String getTargetCompiler() {
        return "musl-gcc";
    }

    @Override
    public boolean hasIsolatedNamespaces() {
        return false;
    }

    @Override
    public boolean requiresLibCSpecificStaticJDKLibraries() {
        return true;
    }
}
