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
import java.util.function.BiConsumer;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.LogUtils;

public class ProxyRegistry extends ConditionalConfigurationRegistry implements BiConsumer<ConfigurationCondition, List<String>> {
    private final DynamicProxyRegistry dynamicProxySupport;
    private final ImageClassLoader imageClassLoader;

    public ProxyRegistry(DynamicProxyRegistry dynamicProxySupport, ImageClassLoader imageClassLoader) {
        this.dynamicProxySupport = dynamicProxySupport;
        this.imageClassLoader = imageClassLoader;
    }

    @Override
    public void accept(ConfigurationCondition condition, List<String> proxies) {
        Class<?>[] interfaces = checkIfInterfacesAreValid(proxies);
        if (interfaces != null) {
            registerConditionalConfiguration(condition, (cnd) -> {
                /* The interfaces array can be empty. The java.lang.reflect.Proxy API allows it. */
                dynamicProxySupport.addProxyClass(cnd, interfaces);
            });
        }
    }

    public Class<?> createProxyClassForSerialization(List<String> proxies) {
        Class<?>[] interfaces = checkIfInterfacesAreValid(proxies);
        if (interfaces != null) {
            return dynamicProxySupport.getProxyClassHosted(interfaces);
        }

        return null;
    }

    private Class<?>[] checkIfInterfacesAreValid(List<String> proxyInterfaceNames) {
        Class<?>[] interfaces = new Class<?>[proxyInterfaceNames.size()];
        for (int i = 0; i < proxyInterfaceNames.size(); i++) {
            String interfaceName = proxyInterfaceNames.get(i);
            Class<?> clazz = imageClassLoader.findClass(interfaceName).get();
            if (!checkClass(proxyInterfaceNames, interfaceName, clazz)) {
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
