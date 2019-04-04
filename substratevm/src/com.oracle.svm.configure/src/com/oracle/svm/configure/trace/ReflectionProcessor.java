/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.trace;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ReflectionConfiguration;
import com.oracle.svm.configure.config.ReflectionMemberSet;
import com.oracle.svm.configure.config.ReflectionMethod;
import com.oracle.svm.configure.config.ReflectionType;
import com.oracle.svm.configure.config.ResourceConfiguration;

class ReflectionProcessor extends AbstractProcessor {
    private final ReflectionConfiguration configuration = new ReflectionConfiguration();
    private final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
    private final ResourceConfiguration resourceConfiguration = new ResourceConfiguration();
    private final AccessAdvisor advisor;

    ReflectionProcessor(AccessAdvisor advisor) {
        this.advisor = advisor;
    }

    public ReflectionConfiguration getConfiguration() {
        return configuration;
    }

    public ProxyConfiguration getProxyConfiguration() {
        return proxyConfiguration;
    }

    public ResourceConfiguration getResourceConfiguration() {
        return resourceConfiguration;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void processEntry(Map<String, ?> entry) {
        boolean invalidResult = Boolean.FALSE.equals(entry.get("result"));
        if (invalidResult) {
            return;
        }
        String function = (String) entry.get("function");
        List<?> args = (List<?>) entry.get("args");
        switch (function) {
            // These are called via java.lang.Class or via the class loader hierarchy, so we would
            // always filter based on the caller class.
            case "getResource":
            case "getResourceAsStream":
            case "getSystemResource":
            case "getSystemResourceAsStream":
                resourceConfiguration.add(singleElement(args));
                return;

            case "getResources":
            case "getSystemResources":
                resourceConfiguration.addLocationIndependent(singleElement(args));
                return;
        }
        String clazz = (String) entry.get("class");
        String callerClass = (String) entry.get("caller_class");
        if (advisor.shouldIgnore(() -> callerClass)) {
            return;
        }
        String declaringClass = (String) entry.get("declaring_class");
        boolean declared = false;
        switch (function) {
            case "forName": {
                assert clazz.equals("java.lang.Class");
                expectSize(args, 1);
                String name = (String) args.get(0);
                configuration.getOrCreateType(name);
                break;
            }

            case "getDeclaredFields":
                declared = true; // fall through
            case "getFields": {
                getMemberSet(clazz, declared).getFields().includeAll();
                break;
            }
            case "getDeclaredMethods":
                declared = true; // fall through
            case "getMethods": {
                getMemberSet(clazz, declared).getMethods().includeAll();
                break;
            }
            case "getDeclaredConstructors":
                declared = true; // fall through
            case "getConstructors": {
                getMemberSet(clazz, declared).getConstructors().includeAll();
                break;
            }

            case "getDeclaredField":
                declared = true;
                clazz = (declaringClass != null) ? declaringClass : clazz;
                // fall through
            case "getField": {
                getMemberSet(clazz, declared).getFields().add(singleElement(args));
                break;
            }
            case "getDeclaredMethod":
                declared = true;
                clazz = (declaringClass != null) ? declaringClass : clazz;
                // fall through
            case "getMethod": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                List<?> parameterTypes = (List<?>) args.get(1);
                getMemberSet(clazz, declared).getMethods().add(new ReflectionMethod(name, parameterTypes.toArray(new String[0])));
                break;
            }
            case "getDeclaredConstructor":
                declared = true; // fall through
            case "getConstructor": {
                List<String> parameterTypes = singleElement(args);
                ReflectionMethod constructor = new ReflectionMethod(ReflectionMethod.CONSTRUCTOR_NAME, parameterTypes.toArray(new String[0]));
                getMemberSet(clazz, declared).getConstructors().add(constructor);
                break;
            }

            case "getProxyClass": {
                expectSize(args, 2);
                addDynamicProxy((List<?>) args.get(1));
                break;
            }
            case "newProxyInstance": {
                expectSize(args, 3);
                addDynamicProxy((List<?>) args.get(1));
                break;
            }

            case "getEnclosingConstructor":
            case "getEnclosingMethod": {
                String result = (String) entry.get("result");
                addFullyQualifiedDeclaredMethod(result);
                break;
            }

            case "newInstance": {
                getMemberSet(clazz, declared).getConstructors().add(new ReflectionMethod(ReflectionMethod.CONSTRUCTOR_NAME, new String[0]));
                break;
            }
        }
    }

    private void addFullyQualifiedDeclaredMethod(String descriptor) {
        int sigbegin = descriptor.indexOf('(');
        int classend = descriptor.lastIndexOf('.', sigbegin - 1);
        String qualifiedClass = descriptor.substring(0, classend);
        String methodName = descriptor.substring(classend + 1, sigbegin);
        String signature = descriptor.substring(sigbegin + 1);
        if (methodName.equals(ReflectionMethod.CONSTRUCTOR_NAME)) {
            getMemberSet(qualifiedClass, true).getConstructors().add(new ReflectionMethod(ReflectionMethod.CONSTRUCTOR_NAME, signature));
        } else {
            getMemberSet(qualifiedClass, true).getMethods().add(new ReflectionMethod(methodName, signature));
        }
    }

    @SuppressWarnings("unchecked")
    private void addDynamicProxy(List<?> interfaceSet) {
        proxyConfiguration.add(new HashSet<>((List<String>) interfaceSet));
    }

    private ReflectionMemberSet getMemberSet(String clazz, boolean declared) {
        ReflectionType type = configuration.getOrCreateType(clazz);
        return declared ? type.getDeclared() : type.getPublic();
    }
}
