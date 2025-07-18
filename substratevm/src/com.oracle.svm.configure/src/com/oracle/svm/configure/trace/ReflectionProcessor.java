/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.configure.trace.LazyValueUtils.lazyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SignatureUtil;
import com.oracle.svm.configure.config.TypeConfiguration;

import jdk.graal.compiler.phases.common.LazyValue;
import jdk.vm.ci.meta.MetaUtil;

class ReflectionProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;
    private boolean trackReflectionMetadata = true;

    ReflectionProcessor(AccessAdvisor advisor) {
        this.advisor = advisor;
    }

    public void setTrackReflectionMetadata(boolean trackReflectionMetadata) {
        this.trackReflectionMetadata = trackReflectionMetadata;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void processEntry(EconomicMap<String, Object> entry, ConfigurationSet configurationSet) {
        boolean invalidResult = Boolean.FALSE.equals(entry.get("result"));
        UnresolvedConfigurationCondition condition = UnresolvedConfigurationCondition.alwaysTrue();
        if (invalidResult) {
            return;
        }

        String function = (String) entry.get("function");
        List<?> args = (List<?>) entry.get("args");
        ResourceConfiguration resourceConfiguration = configurationSet.getResourceConfiguration();
        switch (function) {
            // These are called via java.lang.Class or via the class loader hierarchy, so we would
            // always filter based on the caller class.
            case "findResource", "findResourceAsStream" -> {
                expectSize(args, 2);
                String module = (String) args.get(0);
                String resource = (String) args.get(1);
                if (!advisor.shouldIgnoreResourceLookup(lazyValue(resource), entry)) {
                    resourceConfiguration.addGlobPattern(condition, resource, module);
                }
                return;
            }
            case "getResource", "getSystemResource", "getSystemResourceAsStream", "getResources", "getSystemResources", "getEntry" -> {
                String literal = singleElement(args);
                if (!advisor.shouldIgnoreResourceLookup(lazyValue(literal), entry)) {
                    resourceConfiguration.addGlobPattern(condition, literal, null);
                }
                return;
            }
        }
        TypeConfiguration configuration = configurationSet.getReflectionConfiguration();
        String callerClass = (String) entry.get("caller_class");
        boolean isLoadClass = function.equals("loadClass") || function.equals("findSystemClass");
        if (isLoadClass || function.equals("forName") || function.equals("findClass")) {
            String name = singleElement(args);
            if (isLoadClass) { // different array syntax
                name = MetaUtil.internalNameToJava(MetaUtil.toInternalName(name), true, true);
            }
            if (!advisor.shouldIgnore(lazyValue(name), lazyValue(callerClass), entry) &&
                            !(isLoadClass && advisor.shouldIgnoreLoadClass(lazyValue(name), lazyValue(callerClass), entry)) &&
                            ClassNameSupport.isValidReflectionName(name)) {
                configuration.getOrCreateType(condition, NamedConfigurationTypeDescriptor.fromReflectionName(name));
            }
            return;
        } else if (function.equals("methodTypeDescriptor")) {
            List<String> typeNames = singleElement(args);
            for (String type : typeNames) {
                if (!advisor.shouldIgnore(lazyValue(type), lazyValue(callerClass), copyWithUniqueEntry(entry, "ignoredDescriptorType", type))) {
                    configuration.getOrCreateType(condition, NamedConfigurationTypeDescriptor.fromReflectionName(type));
                }
            }
            return;
        }
        ConfigurationTypeDescriptor clazz = descriptorForClass(entry.get("class"));
        if (clazz != null) {
            for (String className : clazz.getAllQualifiedJavaNames()) {
                if (advisor.shouldIgnore(lazyValue(className), lazyValue(callerClass), false, copyWithUniqueEntry(entry, "ignoredClassName", className))) {
                    return;
                }
            }
        }
        ConfigurationMemberDeclaration declaration = ConfigurationMemberDeclaration.PUBLIC;
        ConfigurationMemberAccessibility accessibility = ConfigurationMemberAccessibility.QUERIED;
        ConfigurationTypeDescriptor clazzOrDeclaringClass = entry.containsKey("declaring_class") ? descriptorForClass(entry.get("declaring_class")) : clazz;
        switch (function) {
            case "getDeclaredFields":
            case "getFields":
            case "getDeclaredMethods":
            case "getMethods":
            case "getDeclaredConstructors":
            case "getConstructors":
            case "getDeclaredClasses":
            case "getClasses":
            case "getRecordComponents":
            case "getPermittedSubclasses":
            case "getNestMembers":
            case "getSigners": {
                configuration.getOrCreateType(condition, clazz);
                break;
            }

            case "getDeclaredField":
            case "getField":
            case "getDeclaredMethod":
            case "getMethod":
            case "getDeclaredConstructor":
            case "getConstructor": {
                configuration.getOrCreateType(condition, clazz);
                if (!clazzOrDeclaringClass.equals(clazz)) {
                    configuration.getOrCreateType(condition, clazz);
                }
                break;
            }

            case "accessField": {
                expectSize(args, 1);
                String name = (String) args.get(0);
                configuration.getOrCreateType(condition, clazz).addField(name, ConfigurationMemberDeclaration.DECLARED, false);
                break;
            }

            case "asInterfaceInstance": {
                accessibility = ConfigurationMemberAccessibility.ACCESSED;
                configuration.getOrCreateType(condition, clazz).setAllPublicMethods(accessibility);
                break;
            }

            case "objectFieldOffset":
            case "findFieldHandle":
            case "unreflectField": {
                declaration = "findFieldHandle".equals(function) ? ConfigurationMemberDeclaration.PRESENT : ConfigurationMemberDeclaration.DECLARED;
                configuration.getOrCreateType(condition, clazzOrDeclaringClass).addField(singleElement(args), declaration, false);
                if (!clazzOrDeclaringClass.equals(clazz)) {
                    configuration.getOrCreateType(condition, clazz);
                }
                break;
            }

            case "findMethodHandle":
            case "invokeMethod": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                List<?> parameterTypes = (List<?>) args.get(1);
                if (parameterTypes == null) { // tolerated and equivalent to no parameter types
                    parameterTypes = Collections.emptyList();
                }
                configuration.getOrCreateType(condition, clazzOrDeclaringClass).addMethod(name, SignatureUtil.toInternalSignature(parameterTypes), ConfigurationMemberDeclaration.PRESENT,
                                ConfigurationMemberAccessibility.ACCESSED);
                if (!trackReflectionMetadata && !clazzOrDeclaringClass.equals(clazz)) {
                    configuration.getOrCreateType(condition, clazz);
                }
                break;
            }

            case "findConstructorHandle":
            case "invokeConstructor": {
                declaration = ConfigurationMemberDeclaration.PRESENT;
                accessibility = ConfigurationMemberAccessibility.ACCESSED;
                List<String> parameterTypes = singleElement(args);
                if (parameterTypes == null) { // tolerated and equivalent to no parameter types
                    parameterTypes = Collections.emptyList();
                }
                String signature = SignatureUtil.toInternalSignature(parameterTypes);
                assert clazz.equals(clazzOrDeclaringClass) : "Constructor can only be accessed via declaring class";
                configuration.getOrCreateType(condition, clazzOrDeclaringClass).addMethod(ConfigurationMethod.CONSTRUCTOR_NAME, signature, declaration, accessibility);
                break;
            }

            case "getProxyClass": {
                expectSize(args, 2);
                addDynamicProxy((List<?>) args.get(1), lazyValue(callerClass), configuration, entry);
                break;
            }
            case "newProxyInstance": {
                expectSize(args, 3);
                addDynamicProxy((List<?>) args.get(1), lazyValue(callerClass), configuration, entry);
                break;
            }
            case "newMethodHandleProxyInstance": {
                expectSize(args, 1);
                addDynamicProxyUnchecked((List<?>) args.get(0), Collections.singletonList("sun.invoke.WrapperInstance"), lazyValue(callerClass), configuration, entry);
                break;
            }

            case "getEnclosingConstructor":
            case "getEnclosingMethod": {
                String result = (String) entry.get("result");
                addFullyQualifiedDeclaredMethod(result, configuration);
                break;
            }

            case "newInstance": {
                if (clazz.toString().equals("java.lang.reflect.Array")) { // reflective array
                                                                          // instantiation
                    configuration.getOrCreateType(condition, descriptorForClass(args.get(0)));
                } else {
                    configuration.getOrCreateType(condition, clazz).addMethod(ConfigurationMethod.CONSTRUCTOR_NAME, "()V", ConfigurationMemberDeclaration.DECLARED,
                                    ConfigurationMemberAccessibility.ACCESSED);
                }
                break;
            }

            case "getBundleImpl": {
                expectSize(args, 5);
                String baseName = (String) args.get(2);
                if (baseName != null) {
                    resourceConfiguration.addBundle(condition, baseName);
                }
                break;
            }
            case "allocateInstance": {
                configuration.getOrCreateType(condition, clazz).setUnsafeAllocated();
                break;
            }
            default:
                System.err.println("Unsupported reflection method: " + function);
        }
    }

    private static void addFullyQualifiedDeclaredMethod(String descriptor, TypeConfiguration configuration) {
        int sigbegin = descriptor.indexOf('(');
        int classend = descriptor.lastIndexOf('.', sigbegin - 1);
        String qualifiedClass = descriptor.substring(0, classend);
        String methodName = descriptor.substring(classend + 1, sigbegin);
        String signature = descriptor.substring(sigbegin);
        configuration.getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), NamedConfigurationTypeDescriptor.fromReflectionName(qualifiedClass))
                        .addMethod(methodName, signature, ConfigurationMemberDeclaration.DECLARED);
    }

    private void addDynamicProxy(List<?> interfaceList, LazyValue<String> callerClass, TypeConfiguration configuration, EconomicMap<String, Object> entry) {
        ConfigurationTypeDescriptor typeDescriptor = descriptorForClass(interfaceList);
        for (String iface : typeDescriptor.getAllQualifiedJavaNames()) {
            if (advisor.shouldIgnore(lazyValue(iface), callerClass, copyWithUniqueEntry(entry, "ignoredInterface", iface))) {
                return;
            }
        }
        configuration.getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), typeDescriptor);
    }

    private void addDynamicProxyUnchecked(List<?> checkedInterfaceList, List<?> uncheckedInterfaceList, LazyValue<String> callerClass, TypeConfiguration configuration,
                    EconomicMap<String, Object> entry) {
        @SuppressWarnings("unchecked")
        List<String> checkedInterfaces = (List<String>) checkedInterfaceList;
        for (String iface : checkedInterfaces) {
            if (advisor.shouldIgnore(lazyValue(iface), callerClass, copyWithUniqueEntry(entry, "ignoredInterface", iface))) {
                return;
            }
        }
        @SuppressWarnings("unchecked")
        List<String> uncheckedInterfaces = (List<String>) uncheckedInterfaceList;

        List<String> interfaces = new ArrayList<>();
        interfaces.addAll(checkedInterfaces);
        interfaces.addAll(uncheckedInterfaces);
        configuration.getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), descriptorForClass(interfaces));
    }

}
