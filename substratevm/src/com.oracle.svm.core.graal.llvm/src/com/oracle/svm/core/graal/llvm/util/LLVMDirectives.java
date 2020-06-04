/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.SubstrateOptions;

public class LLVMDirectives implements CContext.Directives {
    @Override
    public boolean isInConfiguration() {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public List<String> getHeaderFiles() {
        return Collections.singletonList("<unwind.h>");
    }

    @Override
    public List<String> getLibraries() {
        List<String> libraries = new ArrayList<>();
        libraries.add("m");
        if (Platform.includedIn(Platform.LINUX.class) && LLVMOptions.BitcodeOptimizations.getValue()) {
            libraries.add("atomic");
        }
        return libraries;
    }
}
