/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import org.graalvm.nativeimage.impl.ReflectionRegistry;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.common.type.TypeResult;
import com.oracle.svm.configure.AbstractRegistryAdapter;
import com.oracle.svm.hosted.ImageClassLoader;

public class RegistryAdapter extends AbstractRegistryAdapter {
    private final ImageClassLoader classLoader;

    public static RegistryAdapter create(ReflectionRegistry registry, ImageClassLoader classLoader) {
        if (registry instanceof RuntimeReflectionSupport) {
            return new ReflectionRegistryAdapter((RuntimeReflectionSupport) registry, classLoader);
        } else {
            return new RegistryAdapter(registry, classLoader);
        }
    }

    RegistryAdapter(ReflectionRegistry registry, ImageClassLoader classLoader) {
        super(registry);
        this.classLoader = classLoader;
    }

    @Override
    protected TypeResult<Class<?>> findClass(String name) {
        return classLoader.findClass(name);
    }

    @Override
    protected TypeResult<Class<?>> findClass(String name, boolean allowPrimitives) {
        return classLoader.findClass(name, allowPrimitives);
    }
}
