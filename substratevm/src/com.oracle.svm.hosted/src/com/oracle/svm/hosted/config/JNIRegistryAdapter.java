/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.List;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.ReflectionRegistry;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;

public class JNIRegistryAdapter extends RegistryAdapter {
    public JNIRegistryAdapter(ReflectionRegistry registry, ImageClassLoader classLoader) {
        super(registry, classLoader);
    }

    @Override
    public void registerPublicClasses(AccessCondition condition, Class<?> type) {
        registry.register(condition, type.getClasses());
    }

    @Override
    public void registerDeclaredClasses(AccessCondition condition, Class<?> type) {
        registry.register(condition, type.getDeclaredClasses());
    }

    @Override
    public void registerRecordComponents(AccessCondition condition, Class<?> type) {
        VMError.shouldNotReachHere("Record components cannot be accessed through JNI registrations");
    }

    @Override
    public void registerPermittedSubclasses(AccessCondition condition, Class<?> type) {
        VMError.shouldNotReachHere("Permitted subclasses cannot be accessed through JNI registrations");
    }

    @Override
    public void registerNestMembers(AccessCondition condition, Class<?> type) {
        VMError.shouldNotReachHere("Nest members cannot be accessed through JNI registrations");
    }

    @Override
    public void registerSigners(AccessCondition condition, Class<?> type) {
        VMError.shouldNotReachHere("Signers cannot be accessed through JNI registrations");
    }

    @Override
    public void registerPublicFields(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        ensureJniAccessible(jniAccessible);
        super.registerPublicFields(condition, queriedOnly, false, type);
    }

    @Override
    public void registerDeclaredFields(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        ensureJniAccessible(jniAccessible);
        super.registerDeclaredFields(condition, queriedOnly, false, type);
    }

    @Override
    public void registerPublicMethods(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        ensureJniAccessible(jniAccessible);
        super.registerPublicMethods(condition, queriedOnly, false, type);
    }

    @Override
    public void registerDeclaredMethods(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        ensureJniAccessible(jniAccessible);
        super.registerDeclaredMethods(condition, queriedOnly, false, type);
    }

    @Override
    public void registerPublicConstructors(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        ensureJniAccessible(jniAccessible);
        super.registerPublicConstructors(condition, queriedOnly, false, type);
    }

    @Override
    public void registerDeclaredConstructors(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        ensureJniAccessible(jniAccessible);
        super.registerDeclaredConstructors(condition, queriedOnly, false, type);
    }

    @Override
    protected void registerField(AccessCondition condition, boolean allowWrite, boolean jniAccessible, Field field) {
        ensureJniAccessible(jniAccessible);
        super.registerField(condition, allowWrite, true, field);
    }

    @Override
    protected void registerFieldNegativeQuery(AccessCondition condition, boolean jniAccessible, Class<?> type, String fieldName) {
        ensureJniAccessible(jniAccessible);
        super.registerFieldNegativeQuery(condition, true, type, fieldName);
    }

    @Override
    protected void registerExecutable(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Executable... executable) {
        ensureJniAccessible(jniAccessible);
        super.registerExecutable(condition, queriedOnly, true, executable);
    }

    @Override
    protected void registerMethodNegativeQuery(AccessCondition condition, boolean jniAccessible, Class<?> type, String methodName, List<Class<?>> methodParameterTypes) {
        ensureJniAccessible(jniAccessible);
        super.registerMethodNegativeQuery(condition, true, type, methodName, methodParameterTypes);
    }

    @Override
    protected void registerConstructorNegativeQuery(AccessCondition condition, boolean jniAccessible, Class<?> type, List<Class<?>> constructorParameterTypes) {
        ensureJniAccessible(jniAccessible);
        super.registerConstructorNegativeQuery(condition, true, type, constructorParameterTypes);
    }

    @Override
    public void registerAsSerializable(AccessCondition condition, Class<?> clazz) {
        VMError.shouldNotReachHere("serializable cannot be set on JNI registrations");
    }

    private static void ensureJniAccessible(boolean jniAccessible) {
        VMError.guarantee(jniAccessible, "JNIRegistryAdapter can only be used for JNI queries");
    }
}
