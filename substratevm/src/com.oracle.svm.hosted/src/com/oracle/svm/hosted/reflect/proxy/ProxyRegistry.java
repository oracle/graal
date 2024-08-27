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
package com.oracle.svm.hosted.reflect.proxy;

import java.util.List;
import java.util.function.Consumer;

import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.ConfigurationTypeResolver;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.LogUtils;

public class ProxyRegistry extends ConditionalConfigurationRegistry implements Consumer<ConditionalElement<List<String>>> {
    private final ConfigurationTypeResolver typeResolver;
    private final DynamicProxyRegistry dynamicProxySupport;
    private final ImageClassLoader imageClassLoader;

    public ProxyRegistry(ConfigurationTypeResolver typeResolver, DynamicProxyRegistry dynamicProxySupport, ImageClassLoader imageClassLoader) {
        this.typeResolver = typeResolver;
        this.dynamicProxySupport = dynamicProxySupport;
        this.imageClassLoader = imageClassLoader;
    }

    @Override
    public void accept(ConditionalElement<List<String>> proxies) {
        Class<?>[] interfaces = checkIfInterfacesAreValid(proxies);
        if (interfaces != null) {
            registerConditionalConfiguration(proxies.getCondition(), () -> {
                /* The interfaces array can be empty. The java.lang.reflect.Proxy API allows it. */
                dynamicProxySupport.addProxyClass(interfaces);
            });
        }
    }

    public Class<?> createProxyClassForSerialization(ConditionalElement<List<String>> proxies) {
        Class<?>[] interfaces = checkIfInterfacesAreValid(proxies);
        if (interfaces != null) {
            return dynamicProxySupport.getProxyClassHosted(interfaces);
        }

        return null;
    }

    private Class<?>[] checkIfInterfacesAreValid(ConditionalElement<List<String>> proxies) {
        if (typeResolver.resolveConditionType(proxies.getCondition().getTypeName()) == null) {
            return null;
        }
        List<String> interfaceNames = proxies.getElement();
        Class<?>[] interfaces = new Class<?>[interfaceNames.size()];
        for (int i = 0; i < interfaceNames.size(); i++) {
            String className = interfaceNames.get(i);
            Class<?> clazz = imageClassLoader.findClass(className).get();
            if (!checkClass(interfaceNames, className, clazz)) {
                return null;
            }
            interfaces[i] = clazz;
        }

        return interfaces;
    }

    private static boolean checkClass(List<String> interfaceNames, String className, Class<?> clazz) {
        if (clazz == null) {
            warning(interfaceNames, "Class " + className + " not found.");
            return false;
        } else if (!clazz.isInterface()) {
            warning(interfaceNames, "Class " + className + " is not an interface.");
            return false;
        }
        return true;
    }

    private static void warning(List<String> interfaceNames, String reason) {
        LogUtils.warning("Cannot register dynamic proxy for interface list: %s. Reason: %s.", String.join(", ", interfaceNames), reason);
    }
}
