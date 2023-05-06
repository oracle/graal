/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.hosted.substitute;

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oracle.svm.core.util.UserError.guarantee;

public class AspectProcessor {
    private final ImageClassLoader imageClassLoader;
    private AdviceAliasClassGenerator adviceAliasClassGenerator;

    private Map<Class<?>, List<Class<?>>> matcherClassMap = new HashMap<>();
    private Map<Class<?>, List<Class<?>>> superClassMap = new HashMap<>();
    private Map<Class<?>, List<Class<?>>> interfaceClassMap = new HashMap<>();

    public AspectProcessor(ImageClassLoader imageClassLoader) {
        this.imageClassLoader = imageClassLoader;
        adviceAliasClassGenerator = new AdviceAliasClassGenerator(imageClassLoader);
    }

    public void registerAspects() {
        List<Class<?>> aspectList = imageClassLoader.findAnnotatedClasses(Aspect.class, false);
        for (Class<?> annotatedClass : aspectList) {
            Aspect aspect = annotatedClass.getAnnotation(Aspect.class);
            String superClassName = aspect.subClassOf();
            String interfaceName = aspect.implementInterface();
            String[] matchers = aspect.matchers();
            int v1 = superClassName.isBlank() ? 0 : 1;
            int v2 = matchers.length == 0 ? 0 : 1;
            int v3 = interfaceName.isBlank() ? 0 : 1;
            guarantee(v1 + v2 + v3 == 1, "only one of subClassOf, matchers and implementInterface can be set at one time");
            if (!superClassName.isBlank()) {
                TypeResult<Class<?>> superClass = imageClassLoader.findClass(superClassName);
                superClassMap.computeIfAbsent(superClass.get(), k -> new ArrayList<>()).add(annotatedClass);
            } else if (matchers.length > 0) {
                for (String matcher : matchers) {
                    Class<?> originalClass = imageClassLoader.findClass(matcher).get();
                    if (originalClass != null) {
                        matcherClassMap.computeIfAbsent(originalClass, k -> new ArrayList<>()).add(annotatedClass);
                    } else {
                        LogUtils.warning("Class %s declared in @Aspect annotated on %s is not found.", matcher, annotatedClass.getName());
                    }
                }
            } else if (!interfaceName.isBlank()) {
                TypeResult<Class<?>> interfaceClass = imageClassLoader.findClass(interfaceName);
                interfaceClassMap.computeIfAbsent(interfaceClass.get(), k -> new ArrayList<>()).add(annotatedClass);
            }
        }
    }

    public void handleMatchersAspect() {
        List<AdviceAliasClassGenerator.AliasClassInfo> aliasClassInfos = new ArrayList<>();
        for (Map.Entry<Class<?>, List<Class<?>>> classListEntry : matcherClassMap.entrySet()) {
            for (Class<?> annotatedClass : classListEntry.getValue()) {
                aliasClassInfos.add(new AdviceAliasClassGenerator.AliasClassInfo(classListEntry.getKey(), annotatedClass, true));
            }
        }
        adviceAliasClassGenerator.generateAliasClasses(aliasClassInfos);
    }

    public List<Class<?>> handleSubClassAspect(Class<?> originalClass) {
        List<Map.Entry<Class<?>, List<Class<?>>>> classSubstitutes = new ArrayList<>();
        // Check super classes
        for (Map.Entry<Class<?>, List<Class<?>>> classListEntry : superClassMap.entrySet()) {
            if (classListEntry.getKey().isAssignableFrom(originalClass)) {
                classSubstitutes.add(classListEntry);
                break;
            }
        }

        // Check interfaces
        for (Map.Entry<Class<?>, List<Class<?>>> classListEntry : interfaceClassMap.entrySet()) {
            if (Advice.isInterfaceOf(classListEntry.getKey(), originalClass)) {
                classSubstitutes.add(classListEntry);
                break;
            }
        }
        if (!classSubstitutes.isEmpty()) {
            List<AdviceAliasClassGenerator.AliasClassInfo> aliasClassInfos = new ArrayList<>();
            for (Map.Entry<Class<?>, List<Class<?>>> classSubstituteEntry : classSubstitutes) {
                for (Class<?> annotatedClass : classSubstituteEntry.getValue()) {
                    aliasClassInfos.add(new AdviceAliasClassGenerator.AliasClassInfo(originalClass, annotatedClass, false));
                }
            }
            return adviceAliasClassGenerator.generateAliasClasses(aliasClassInfos);
        } else {
            return null;
        }
    }
}
