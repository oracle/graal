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

import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.hosted.code.CompileQueue.CompileTask;
import com.oracle.svm.hosted.meta.HostedMethod;

class CodeBreakdownProvider {
    private final Map<String, Long> codeBreakdown;

    CodeBreakdownProvider(Collection<CompileTask> compilationTasks) {
        Map<String, Long> nameToSizeMap = new HashMap<>();
        for (CompileTask task : compilationTasks) {
            String key = null;
            Class<?> javaClass = task.method.getDeclaringClass().getJavaClass();
            Module module = javaClass.getModule();
            if (module.isNamed()) {
                key = module.getName();
                if ("org.graalvm.nativeimage.builder".equals(key)) {
                    key = "svm.jar (Native Image)";
                }
            } else {
                key = findJARFile(javaClass);
                if (key == null) {
                    key = findPackageOrClassName(task.method);
                }
            }
            nameToSizeMap.merge(key, (long) task.result.getTargetCodeSize(), Long::sum);
        }
        codeBreakdown = Collections.unmodifiableMap(nameToSizeMap);
    }

    public static Map<String, Long> get() {
        return ImageSingletons.lookup(CodeBreakdownProvider.class).codeBreakdown;
    }

    private static String findJARFile(Class<?> javaClass) {
        CodeSource codeSource = javaClass.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            String path = codeSource.getLocation().getPath();
            if (path.endsWith(".jar")) {
                // Use String API to determine basename of path to handle both / and \.
                return path.substring(Math.max(path.lastIndexOf('/') + 1, path.lastIndexOf('\\') + 1));
            }
        }
        return null;
    }

    private static String findPackageOrClassName(HostedMethod method) {
        String qualifier = method.format("%H");
        int lastDotIndex = qualifier.lastIndexOf('.');
        if (lastDotIndex > 0) {
            qualifier = qualifier.substring(0, lastDotIndex);
        }
        return qualifier;
    }
}
