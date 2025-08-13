/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.word.Word;

public final class SubstratePerfMapWriter implements InstalledCodeObserver {

    static final class Factory implements InstalledCodeObserver.Factory {
        @Override
        public InstalledCodeObserver create(DebugContext debugContext, SharedMethod method, CompilationResult compilation, Pointer code, int codeSize) {
            try {
                return new SubstratePerfMapWriter(debugContext, code, codeSize, method);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }
    }

    private SubstratePerfMapWriter(DebugContext debug, Pointer code, int codeSize, SharedMethod method) {
        long codeAddress = code.rawValue();
        String methodName = method.format("%R %H.%n(%P)");
        Path perfMapPath = Paths.get("/", "tmp", "perf-" + ProcessProperties.getProcessID() + ".map");

        try {
            Files.write(perfMapPath, String.format("%x %x %s%n", codeAddress, codeSize, methodName).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            debug.log("Failed to write jit info for " + methodName);
        }
    }

    @Override
    public InstalledCodeObserverHandle install() {
        // We don't need a handle, as the new symbol is already written to the perf map.
        return Word.nullPointer();
    }
}
