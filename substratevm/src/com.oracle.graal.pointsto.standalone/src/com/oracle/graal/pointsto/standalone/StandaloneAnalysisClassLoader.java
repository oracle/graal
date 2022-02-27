/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.type.TypeResult;
import com.oracle.svm.common.util.ClassUtils;

public class StandaloneAnalysisClassLoader extends URLClassLoader {
    private List<String> analysisClassPath;
    private List<String> analysisModulePath;

    public StandaloneAnalysisClassLoader(List<String> classPath, List<String> modulePath, ClassLoader parent) {
        super(pathToUrl(classPath, modulePath), parent);
        analysisClassPath = classPath;
        analysisModulePath = modulePath;
    }

    public List<String> getClassPath() {
        return analysisClassPath;
    }

    public List<String> getModulePath() {
        return analysisModulePath;
    }

    public Class<?> defineClassFromOtherClassLoader(Class<?> clazz) {
        String classFile = "/" + clazz.getName().replace('.', '/') + ".class";
        InputStream clazzStream = clazz.getResourceAsStream(classFile);
        Class<?> newlyDefinedClass = null;
        if (clazzStream != null) {
            try {
                byte[] classBytes = clazzStream.readAllBytes();
                newlyDefinedClass = defineClass(clazz.getName(), classBytes, 0, classBytes.length);
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere(e);
            }
        }
        return newlyDefinedClass;
    }

    public TypeResult<Class<?>> findClass(String name, boolean allowPrimitives) {
        try {
            if (allowPrimitives && name.indexOf('.') == -1) {
                TypeResult<Class<?>> primitiveType = ClassUtils.getPrimitiveTypeByName(name);
                if (primitiveType != null) {
                    return primitiveType;
                }
            }
            return TypeResult.forClass(Class.forName(name, false, this));
        } catch (ClassNotFoundException | LinkageError ex) {
            return TypeResult.forException(name, ex);
        }
    }

    private static URL[] pathToUrl(List<String> classPath, List<String> modulePath) {
        List<URL> urls = new ArrayList<>();
        Stream.concat(classPath.stream(), modulePath.stream())
                        .forEach(cp -> {
                            try {
                                urls.add(new File(cp).toURI().toURL());
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                        });
        return urls.toArray(new URL[0]);
    }
}
