/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.hosted.image;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.ImageSingletons;

import java.nio.file.Path;

public class DebugInfoProviderHelper {

    public static String getFileName(Path fullFilePath) {
        if (fullFilePath != null) {
            Path filename = fullFilePath.getFileName();
            if (filename != null) {
                return filename.toString();
            }
        }
        return "";
    }

    public static Path getFilePath(Path fullFilePath) {
        if (fullFilePath != null) {
            return fullFilePath.getParent();
        }
        return null;
    }

    public static Path getFullFilePathFromMethod(ResolvedJavaMethod method, DebugContext debugContext) {
        ResolvedJavaType javaType;
        if (method instanceof HostedMethod) {
            javaType = NativeImageDebugInfoProvider.getDeclaringClass((HostedMethod) method, false);
        } else {
            javaType = method.getDeclaringClass();
        }
        Class<?> clazz = null;
        if (javaType instanceof OriginalClassProvider) {
            clazz = ((OriginalClassProvider) javaType).getJavaClass();
        }
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        Path fullFilePath;
        try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", javaType)) {
            fullFilePath =  sourceManager.findAndCacheSource(javaType, clazz, debugContext);
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
        return fullFilePath;
    }

    public static int getLineNumber(ResolvedJavaMethod method, int bci) {
        LineNumberTable lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable != null && bci >= 0) {
            return lineNumberTable.getLineNumber(bci);
        }
        return -1;
    }

    public static ResolvedJavaMethod getOriginalMethod(ResolvedJavaMethod method) {
        // unwrap to an original method as far as we can
        ResolvedJavaMethod targetMethod = method;
        while (targetMethod instanceof WrappedJavaMethod) {
            targetMethod = ((WrappedJavaMethod) targetMethod).getWrapped();
        }
        // if we hit a substitution then we can translate to the original
        // for identity otherwise we use whatever we unwrapped to.
        if (targetMethod instanceof SubstitutionMethod) {
            targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
        }
        return targetMethod;
    }

    public static String getMethodName(ResolvedJavaMethod method) {
        ResolvedJavaMethod targetMethod = getOriginalMethod(method);
        String name = targetMethod.getName();
        if (name.equals("<init>")) {
            if (method instanceof HostedMethod) {
                name = NativeImageDebugInfoProvider.getDeclaringClass((HostedMethod) method, true).toJavaName();
                if (name.indexOf('.') >= 0) {
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            } else {
                name = targetMethod.format("%h");
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
        }
        return name;
    }
}
