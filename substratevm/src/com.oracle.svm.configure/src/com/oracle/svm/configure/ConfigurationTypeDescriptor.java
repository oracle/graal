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
package com.oracle.svm.configure;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.util.json.JsonPrintable;

/**
 * Provides a representation of a Java type based on String type names. This is used to parse types
 * in configuration files. The supported types are:
 *
 * <ul>
 * <li>Named types: regular Java types described by their fully qualified name.</li>
 * <li>Proxy types: proxy classes described by the names of the implemented interface(s).</li>
 * </ul>
 */
public interface ConfigurationTypeDescriptor extends Comparable<ConfigurationTypeDescriptor>, JsonPrintable {
    enum Kind {
        NAMED,
        PROXY,
        LAMBDA
    }

    static ConfigurationTypeDescriptor fromClass(Class<?> clazz) {
        Stream<String> interfacesStream = Arrays.stream(clazz.getInterfaces())
                        .map(Class::getTypeName);
        if (Proxy.isProxyClass(clazz)) {
            return ProxyConfigurationTypeDescriptor.fromInterfaceReflectionNames(interfacesStream.toList());
        } else if (LambdaUtils.isLambdaClass(clazz)) {
            String declaringClass = StringUtil.split(clazz.getTypeName(), LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING)[0];
            return LambdaConfigurationTypeDescriptor.fromReflectionNames(declaringClass, interfacesStream.toList());
        } else {
            return NamedConfigurationTypeDescriptor.fromReflectionName(clazz.getTypeName());
        }
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
}
