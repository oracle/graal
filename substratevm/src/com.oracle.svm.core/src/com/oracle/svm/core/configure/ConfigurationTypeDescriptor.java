/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.util.Collection;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.vm.ci.meta.MetaUtil;

/**
 * Provides a representation of a Java type based on String type names. This is used to parse types
 * in configuration files. The supported types are:
 *
 * <ul>
 * <li>Named types: regular Java types described by their fully qualified name.</li>
 * </ul>
 */
public interface ConfigurationTypeDescriptor extends Comparable<ConfigurationTypeDescriptor>, JsonPrintable {
    static String canonicalizeTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        String name = typeName;
        if (name.indexOf('[') != -1) {
            /* accept "int[][]", "java.lang.String[]" */
            name = MetaUtil.internalNameToJava(MetaUtil.toInternalName(name), true, true);
        }
        return name;
    }

    enum Kind {
        NAMED,
        PROXY
    }

    Kind getDescriptorType();

    @Override
    String toString();

    /**
     * Returns the qualified names of all named Java types (excluding proxy classes, lambda classes
     * and similar anonymous classes) required for this type descriptor to properly describe its
     * type. This is used to filter configurations based on a String-based class filter.
     */
    Collection<String> getAllQualifiedJavaNames();

    static String checkQualifiedJavaName(String javaName) {
        if (ImageInfo.inImageBuildtimeCode() && !(javaName.indexOf('/') == -1 || javaName.indexOf('/') > javaName.lastIndexOf('.'))) {
            LogUtils.warning("Type descriptor requires qualified Java name, not internal representation: %s", javaName);
        }
        return canonicalizeTypeName(javaName);
    }
}
