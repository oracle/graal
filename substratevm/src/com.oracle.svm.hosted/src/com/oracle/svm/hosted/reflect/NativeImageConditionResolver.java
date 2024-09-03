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
package com.oracle.svm.hosted.reflect;

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.configure.ConfigurationConditionResolver;
import com.oracle.svm.core.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

public class NativeImageConditionResolver implements ConfigurationConditionResolver<ConfigurationCondition> {
    private final ImageClassLoader classLoader;
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) private final ClassInitializationSupport classInitializationSupport;

    public NativeImageConditionResolver(ImageClassLoader classLoader, ClassInitializationSupport classInitializationSupport) {
        this.classLoader = classLoader;
        this.classInitializationSupport = classInitializationSupport;
    }

    @Override
    public TypeResult<ConfigurationCondition> resolveCondition(UnresolvedConfigurationCondition unresolvedCondition) {
        String canonicalizedName = ConfigurationTypeDescriptor.canonicalizeTypeName(unresolvedCondition.getTypeName());
        TypeResult<Class<?>> clazz = classLoader.findClass(canonicalizedName);
        return clazz.map(type -> {
            /*
             * We don't want to track always reached types: we convert them into build-time
             * reachability checks.
             */
            var runtimeChecked = !classInitializationSupport.isAlwaysReached(type) && unresolvedCondition.isRuntimeChecked();
            if (runtimeChecked) {
                classInitializationSupport.addForTypeReachedTracking(type);
            }
            return ConfigurationCondition.create(type, runtimeChecked);
        });
    }

    @Override
    public ConfigurationCondition alwaysTrue() {
        return ConfigurationCondition.alwaysTrue();
    }
}
