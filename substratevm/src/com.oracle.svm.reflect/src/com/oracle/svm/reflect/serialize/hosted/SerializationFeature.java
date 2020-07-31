/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.reflect.serialize.hosted;

// Checkstyle: allow reflection

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.configure.SerializationConfigurationParser.SerializationParserFunction;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.serialize.SerializationSupport;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.Externalizable;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

@AutomaticFeature
public class SerializationFeature implements Feature {
    private int loadedConfigurations;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();

        SerializationSupport serializationSupport = new SerializationSupport(imageClassLoader);
        ImageSingletons.add(SerializationRegistry.class, serializationSupport);

        SerializationParserFunction serializationAdapter = (strTargetSerializationClass, checksum) -> {
            Class<?> serializationTargetClass = resolveClass(strTargetSerializationClass, access);
            UserError.guarantee(serializationTargetClass != null, "Cannot find serialization target class %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                            " Please make sure it is in the classpath", strTargetSerializationClass);
            if (Serializable.class.isAssignableFrom(serializationTargetClass)) {
                Class<?> targetConstructor = serializationSupport.addSerializationConstructorAccessorClass(serializationTargetClass, checksum, access);
                addReflections(serializationTargetClass, targetConstructor);
            }
        };

        SerializationConfigurationParser parser = new SerializationConfigurationParser(serializationAdapter);
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFiles.SERIALIZATION_NAME);
    }

    public static void addReflections(Class<?> serializationTargetClass, Class<?> targetConstructorClass) {
        if (targetConstructorClass != null) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(targetConstructorClass));
        }

        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(serializationTargetClass, (Class<?>[]) null));
        }

        RuntimeReflection.register(serializationTargetClass);
        /**
         * ObjectStreamClass.computeDefaultSUID is always called at runtime to verify serialization
         * class consistency, so need to register all constructors, methods and fields/
         */
        RuntimeReflection.register(serializationTargetClass.getDeclaredConstructors());
        registerMethods(serializationTargetClass);
        registerFields(serializationTargetClass);
    }

    private static void registerMethods(Class<?> serializationTargetClass) {
        RuntimeReflection.register(serializationTargetClass.getDeclaredMethods());
        // computeDefaultSUID will be reflectively called at runtime to verify class consistency
        Method computeDefaultSUID = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "computeDefaultSUID", Class.class);
        RuntimeReflection.register(computeDefaultSUID);
    }

    private static void registerFields(Class<?> serializationTargetClass) {
        int staticFinalMask = Modifier.STATIC | Modifier.FINAL;
        int privateStaticFinalMask = Modifier.PRIVATE | staticFinalMask;

        Set<String> serialPersistentFieldNames = new HashSet<>();
        try {
            Field f = ReflectionUtil.lookupField(serializationTargetClass, "serialPersistentFields");
            if ((f.getModifiers() & privateStaticFinalMask) == privateStaticFinalMask) {
                ObjectStreamField[] serialPersistentFields = (ObjectStreamField[]) f.get(null);
                for (int i = 0; i < serialPersistentFields.length; i++) {
                    serialPersistentFieldNames.add(serialPersistentFields[i].getName());
                }
            }
        } catch (ReflectionUtil.ReflectionUtilError | IllegalAccessException e) {
            // No serialPersistentFields field or failed to get the field value, continue
        }

        for (Field f : serializationTargetClass.getDeclaredFields()) {
            int modifiers = f.getModifiers();
            boolean allowWrite = false;
            boolean allowUnsafeAccess = false;
            if ((modifiers & staticFinalMask) != staticFinalMask) {
                allowWrite = Modifier.isFinal(f.getModifiers());
                allowUnsafeAccess = !Modifier.isStatic(f.getModifiers());
            }
            RuntimeReflection.register(allowWrite, allowUnsafeAccess, f);
        }
    }

    private static Class<?> resolveClass(String typeName, FeatureAccess a) {
        Class<?> ret = a.findClassByName(typeName);
        if (ret == null) {
            handleError("Could not resolve " + typeName + " for serialization configuration.");
        }
        return ret;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest serializationFallback = ImageSingletons.lookup(FallbackFeature.class).serializationFallback;
        if (serializationFallback != null && loadedConfigurations == 0) {
            throw serializationFallback;
        }
    }

    private static void handleError(String message) {
        // Checkstyle: stop
        boolean allowIncompleteClasspath = NativeImageOptions.AllowIncompleteClasspath.getValue();
        if (allowIncompleteClasspath) {
            System.out.println("WARNING: " + message);
        } else {
            throw new JSONParserException(message + " To allow unresolvable reflection configuration, use option -H:+AllowIncompleteClasspath");
        }
        // Checkstyle: resume
    }
}
