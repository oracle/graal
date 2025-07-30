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

import static com.oracle.svm.configure.trace.LazyValueUtils.lazyNull;
import static com.oracle.svm.configure.trace.LazyValueUtils.lazyValue;

import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.phases.common.LazyValue;

class JniProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;

    JniProcessor(AccessAdvisor advisor) {
        this.advisor = advisor;
    }

    @Override
    @SuppressWarnings("fallthrough")
    void processEntry(EconomicMap<String, Object> entry, ConfigurationSet configurationSet) {
        UnresolvedConfigurationCondition condition = UnresolvedConfigurationCondition.alwaysTrue();
        boolean invalidResult = Boolean.FALSE.equals(entry.get("result"));
        if (invalidResult) {
            return;
        }
        String function = (String) entry.get("function");
        String callerClass = (String) entry.get("caller_class");
        List<?> args = (List<?>) entry.get("args");
        LazyValue<String> callerClassLazyValue = lazyValue(callerClass);
        // Special: FindClass and DefineClass take the class in question as a string argument
        if (function.equals("FindClass") || function.equals("DefineClass")) {
            String jniName = singleElement(args);
            if (ClassNameSupport.isValidJNIName(jniName)) {
                ConfigurationTypeDescriptor type = NamedConfigurationTypeDescriptor.fromJNIName(jniName);
                LazyValue<String> reflectionName = lazyValue(ClassNameSupport.jniNameToReflectionName(jniName));
                if (!advisor.shouldIgnore(reflectionName, callerClassLazyValue, entry)) {
                    if (function.equals("FindClass")) {
                        if (!advisor.shouldIgnoreJniLookup(function, reflectionName, lazyNull(), lazyNull(), callerClassLazyValue, entry)) {
                            configurationSet.getReflectionConfiguration().getOrCreateType(condition, type).setJniAccessible();
                        }
                    } else if (!AccessAdvisor.PROXY_CLASS_NAME_PATTERN.matcher(jniName).matches()) { // DefineClass
                        LogUtils.warning("Unsupported JNI function DefineClass used to load class " + jniName);
                    }
                }
            }
            return;
        }
        ConfigurationTypeDescriptor clazz = descriptorForClass(entry.get("class"));
        if (clazz.getAllQualifiedJavaNames().stream().anyMatch(c -> advisor.shouldIgnore(lazyValue(c), callerClassLazyValue, entry))) {
            return;
        }
        boolean hasDeclaringClass = entry.containsKey("declaring_class");
        ConfigurationTypeDescriptor declaringClass = hasDeclaringClass ? descriptorForClass(entry.get("declaring_class")) : null;
        ConfigurationTypeDescriptor declaringClassOrClazz = hasDeclaringClass ? declaringClass : clazz;
        ConfigurationMemberDeclaration declaration = hasDeclaringClass ? ConfigurationMemberDeclaration.DECLARED : ConfigurationMemberDeclaration.PRESENT;
        TypeConfiguration config = configurationSet.getReflectionConfiguration();
        boolean makeTypeJniAccessible = true;
        switch (function) {
            case "AllocObject":
                expectSize(args, 0);
                /*
                 * AllocObject is implemented via Unsafe.allocateInstance, so we need to set the
                 * "unsafe allocated" flag in the reflection configuration file.
                 */
                configurationSet.getReflectionConfiguration().getOrCreateType(condition, clazz).setUnsafeAllocated();
                break;
            case "GetStaticMethodID":
            case "GetMethodID": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                String signature = (String) args.get(1);
                if (clazz.getAllQualifiedJavaNames().stream()
                                .noneMatch(c -> advisor.shouldIgnoreJniLookup(function, lazyValue(c), lazyValue(name), lazyValue(signature), callerClassLazyValue, entry))) {
                    ConfigurationType type = getOrCreateJniAccessibleType(config, condition, declaringClassOrClazz);
                    type.addMethod(name, signature, declaration);
                    if (!declaringClassOrClazz.equals(clazz)) {
                        config.getOrCreateType(condition, clazz);
                    }
                }
                break;
            }
            case "GetFieldID":
            case "GetStaticFieldID": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                String signature = (String) args.get(1);
                if (clazz.getAllQualifiedJavaNames().stream()
                                .noneMatch(c -> advisor.shouldIgnoreJniLookup(function, lazyValue(c), lazyValue(name), lazyValue(signature), callerClassLazyValue, entry))) {
                    ConfigurationType type = getOrCreateJniAccessibleType(config, condition, declaringClassOrClazz);
                    type.addField(name, declaration, false);
                    if (!declaringClassOrClazz.equals(clazz)) {
                        config.getOrCreateType(condition, clazz);
                    }
                }
                break;
            }
            case "ThrowNew": {
                expectSize(args, 1); // exception message, ignore
                String name = ConfigurationMethod.CONSTRUCTOR_NAME;
                String signature = "(Ljava/lang/String;)V";
                if (clazz.getAllQualifiedJavaNames().stream()
                                .noneMatch(c -> advisor.shouldIgnoreJniLookup(function, lazyValue(c), lazyValue(name), lazyValue(signature), callerClassLazyValue, entry))) {
                    ConfigurationType type = getOrCreateJniAccessibleType(config, condition, declaringClassOrClazz);
                    type.addMethod(name, signature, declaration);
                    assert declaringClassOrClazz.equals(clazz) : "Constructor can only be accessed via declaring class";
                }
                break;
            }
            case "ToReflectedField":
                makeTypeJniAccessible = false;
                // fall through
            case "FromReflectedField": {
                expectSize(args, 1);
                String name = (String) args.get(0);
                ConfigurationType type = config.getOrCreateType(condition, declaringClassOrClazz);
                if (makeTypeJniAccessible) {
                    type.setJniAccessible();
                }
                type.addField(name, declaration, false);
                break;
            }
            case "ToReflectedMethod":
                makeTypeJniAccessible = false;
                // fall through
            case "FromReflectedMethod": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                String signature = (String) args.get(1);
                ConfigurationType type = config.getOrCreateType(condition, declaringClassOrClazz);
                if (makeTypeJniAccessible) {
                    type.setJniAccessible();
                }
                type.addMethod(name, signature, declaration);
                break;
            }
            case "NewObjectArray": {
                expectSize(args, 0);
                if (clazz.getAllQualifiedJavaNames().stream().noneMatch(c -> advisor.shouldIgnoreJniLookup(function, lazyValue(c), null, null, callerClassLazyValue, entry))) {
                    /* Array class name is already in Class.forName format */
                    config.getOrCreateType(condition, clazz);
                }
                break;
            }
        }
    }

    private static ConfigurationType getOrCreateJniAccessibleType(TypeConfiguration config, UnresolvedConfigurationCondition condition, ConfigurationTypeDescriptor typeName) {
        ConfigurationType type = config.getOrCreateType(condition, typeName);
        type.setJniAccessible();
        return type;
    }

}
