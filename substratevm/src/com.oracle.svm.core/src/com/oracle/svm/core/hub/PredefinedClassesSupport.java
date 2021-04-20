/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

public final class PredefinedClassesSupport {

    static PredefinedClassesSupport singleton() {
        return ImageSingletons.lookup(PredefinedClassesSupport.class);
    }

    public static String hash(byte[] classData, int offset, int length) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(classData, offset, length);
            return SubstrateUtil.toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /** Predefined classes by hash. */
    private final EconomicMap<String, Class<?>> predefinedClasses = ImageHeapMap.create();

    /** Predefined classes which have already been loaded, by name. */
    private final EconomicMap<String, Class<?>> loadedClasses = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(String hash, Class<?> clazz) {
        Class<?> existing = singleton().predefinedClasses.putIfAbsent(hash, clazz);
        VMError.guarantee(existing == null, "Can define only one class per hash");
    }

    public static Class<?> getPredefinedClass(String expectedName, byte[] data, int offset, int length) {
        String hash = hash(data, offset, length);
        Class<?> clazz = singleton().predefinedClasses.get(hash);
        if (clazz == null) {
            String name = (expectedName != null) ? expectedName : "(name not specified)";
            throw VMError.unsupportedFeature("Defining a class from new bytecodes at run time is not supported. Class " + name +
                            " with hash " + hash + " was not provided during the image build. Please see BuildConfiguration.md.");
        }
        singleton().loadedClasses.put(clazz.getName(), clazz);
        return clazz;
    }

    static Class<?> getLoadedForName(String name) {
        return singleton().loadedClasses.get(name);
    }
}

@AutomaticFeature
final class ClassDefinitionFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PredefinedClassesSupport.class, new PredefinedClassesSupport());
    }
}
