/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReference;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(value = jdk.internal.loader.BuiltinClassLoader.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_internal_loader_BuiltinClassLoader {

    @Substitute
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    @Substitute
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Target_java_lang_ClassLoader self = SubstrateUtil.cast(this, Target_java_lang_ClassLoader.class);
        Class<?> clazz = self.findLoadedClass(name);
        if (clazz == null) {
            throw new ClassNotFoundException(name);
        }
        return clazz;
    }

    @Substitute
    public URL findResource(String mn, String name) {
        return ResourcesHelper.nameToResourceURL(mn, name);
    }

    @Substitute
    public InputStream findResourceAsStream(String mn, String name) throws IOException {
        return ResourcesHelper.nameToResourceInputStream(name);
    }

    @Substitute
    public URL findResource(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    public Enumeration<URL> findResources(String name) {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }

    @Substitute
    private List<URL> findMiscResource(String name) {
        return ResourcesHelper.nameToResourceListURLs(name);
    }

    @Substitute
    private URL findResource(ModuleReference mref, String name) {
        return ResourcesHelper.nameToResourceURL(mref.descriptor().name(), name);
    }

    @Substitute
    private URL findResourceOnClassPath(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    private Enumeration<URL> findResourcesOnClassPath(String name) {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }
}
