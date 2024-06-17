/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.util.LogUtils;

import jdk.vm.ci.meta.MetaUtil;

public final class ConfigurationTypeResolver {
    private final String configurationType;
    private final ImageClassLoader classLoader;

    public ConfigurationTypeResolver(String configurationType, ImageClassLoader classLoader) {
        this.configurationType = configurationType;
        this.classLoader = classLoader;
    }

    public Class<?> resolveType(String typeName) {
        return resolveType(typeName, true);
    }

    private Class<?> resolveType(String typeName, boolean warn) {
        String name = typeName;
        if (name.indexOf('[') != -1) {
            /* accept "int[][]", "java.lang.String[]" */
            name = MetaUtil.internalNameToJava(MetaUtil.toInternalName(name), true, true);
        }
        TypeResult<Class<?>> typeResult = classLoader.findClass(name);
        if (warn && !typeResult.isPresent()) {
            LogUtils.warning("Could not resolve %s for %s.", name, configurationType);
        }
        return typeResult.get();
    }
}
