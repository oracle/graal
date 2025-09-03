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
package com.oracle.svm.hosted;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.hosted.code.CompileQueue.CompileTask;

class CodeBreakdownProvider {
    private Map<Class<?>, Long> codeBreakdown;

    CodeBreakdownProvider(Collection<CompileTask> compilationTasks) {
        codeBreakdown = Map.copyOf(compilationTasks.stream().collect(
                        Collectors.groupingBy(
                                        compileTask -> compileTask.method.getDeclaringClass().getJavaClass(),
                                        Collectors.summingLong(compileTask -> compileTask.result.getTargetCodeSize()))));
    }

    /**
     * Provides the code breakdown as map from name to size, and clears the cache to avoid memory
     * footprint.
     *
     * @return the code breakdown
     */
    public static Map<Class<?>, Long> getAndClear() {
        CodeBreakdownProvider singleton = ImageSingletons.lookup(CodeBreakdownProvider.class);
        var map = singleton.codeBreakdown;
        singleton.codeBreakdown = null;
        return map;
    }
}
