/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

public class NativeImageClassLoader extends AbstractNativeImageClassLoader {

    NativeImageClassLoader(String[] classpath, @SuppressWarnings("unused") String[] modulePath) {
        super(classpath);
    }

    @Override
    public List<Path> modulepath() {
        return Collections.emptyList();
    }

    @Override
    public Optional<Object> findModule(String moduleName) {
        return Optional.empty();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return Util.loadClass(classPathClassLoader, name, resolve);
    }

    @Override
    protected URL findResource(String name) {
        return Util.findResource(classPathClassLoader, name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return Util.findResources(classPathClassLoader, name);
    }

    @Override
    public void initAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        new ClassInit(executor, imageClassLoader, this).init();
    }
}
