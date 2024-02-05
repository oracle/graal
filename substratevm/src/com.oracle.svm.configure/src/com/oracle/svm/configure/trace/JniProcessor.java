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

import static com.oracle.svm.configure.trace.LazyValueUtils.lazyValue;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.phases.common.LazyValue;
import jdk.vm.ci.meta.MetaUtil;

class JniProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;

    JniProcessor(AccessAdvisor advisor) {
        this.advisor = advisor;
    }

    @Override
    @SuppressWarnings("fallthrough")
    void processEntry(EconomicMap<String, ?> entry, ConfigurationSet configurationSet) {
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
            String lookupName = singleElement(args);
            String internalName = (lookupName.charAt(0) != '[') ? ('L' + lookupName + ';') : lookupName;
            String forNameString = MetaUtil.internalNameToJava(internalName, true, true);
            if (!advisor.shouldIgnore(lazyValue(forNameString), callerClassLazyValue)) {
                if (function.equals("FindClass")) {
                    configurationSet.getJniConfiguration().getOrCreateType(condition, forNameString);
                } else if (!AccessAdvisor.PROXY_CLASS_NAME_PATTERN.matcher(lookupName).matches()) { // DefineClass
                    LogUtils.warning("Unsupported JNI function DefineClass used to load class " + forNameString);
                }
            }
            return;
        }
        String clazz = (String) entry.get("class");
        if (advisor.shouldIgnore(lazyValue(clazz), callerClassLazyValue)) {
            return;
        }
        String declaringClass = (String) entry.get("declaring_class");
        String declaringClassOrClazz = (declaringClass != null) ? declaringClass : clazz;
        ConfigurationMemberDeclaration declaration = (declaringClass != null) ? ConfigurationMemberDeclaration.DECLARED : ConfigurationMemberDeclaration.PRESENT;
        TypeConfiguration config = configurationSet.getJniConfiguration();
        switch (function) {
            case "GetStaticMethodID":
            case "GetMethodID": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                String signature = (String) args.get(1);
                if (!advisor.shouldIgnoreJniMethodLookup(lazyValue(clazz), lazyValue(name), lazyValue(signature), callerClassLazyValue)) {
                    config.getOrCreateType(condition, declaringClassOrClazz).addMethod(name, signature, declaration);
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
                config.getOrCreateType(condition, declaringClassOrClazz).addField(name, declaration, false);
                if (!declaringClassOrClazz.equals(clazz)) {
                    config.getOrCreateType(condition, clazz);
                }
                break;
            }
            case "ThrowNew": {
                expectSize(args, 1); // exception message, ignore
                String name = ConfigurationMethod.CONSTRUCTOR_NAME;
                String signature = "(Ljava/lang/String;)V";
                if (!advisor.shouldIgnoreJniMethodLookup(lazyValue(clazz), lazyValue(name), lazyValue(signature), callerClassLazyValue)) {
                    config.getOrCreateType(condition, declaringClassOrClazz).addMethod(name, signature, declaration);
                    assert declaringClassOrClazz.equals(clazz) : "Constructor can only be accessed via declaring class";
                }
                break;
            }
            case "ToReflectedField":
                config = configurationSet.getReflectionConfiguration(); // fall through
            case "FromReflectedField": {
                expectSize(args, 1);
                String name = (String) args.get(0);
                config.getOrCreateType(condition, declaringClassOrClazz).addField(name, declaration, false);
                break;
            }
            case "ToReflectedMethod":
                config = configurationSet.getReflectionConfiguration(); // fall through
            case "FromReflectedMethod": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                String signature = (String) args.get(1);
                config.getOrCreateType(condition, declaringClassOrClazz).addMethod(name, signature, declaration);
                break;
            }
            case "NewObjectArray": {
                expectSize(args, 0);
                /* Array class name is already in Class.forName format */
                config.getOrCreateType(condition, clazz);
                break;
            }
        }
    }

}
