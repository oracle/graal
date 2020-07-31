/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.configure.SerializationConfigurationParser.SerializationParserFunction;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.hosted.ReflectionDataBuilder;
import com.oracle.svm.reflect.serialize.SerializationSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import java.io.Externalizable;
import java.io.ObjectStreamField;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@AutomaticFeature
public class SerializationFeature implements Feature {
    private int loadedConfigurations;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();

        SerializationSupport serializationSupport;
        if (ImageSingletons.contains(SerializationRegistry.class)) {
            serializationSupport = (SerializationSupport) ImageSingletons.lookup(SerializationRegistry.class);
        } else {
            serializationSupport = new SerializationSupport();
            ImageSingletons.add(SerializationRegistry.class, serializationSupport);
        }

        ReflectionDataBuilder reflectionData;
        if (ImageSingletons.contains(RuntimeReflectionSupport.class)) {
            reflectionData = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
        } else {
            reflectionData = new ReflectionDataBuilder(access);
            ImageSingletons.add(RuntimeReflectionSupport.class, reflectionData);
        }

        SerializationParserFunction serializationAdapter = (strTargetSerializationClass, srtParameterTypes, srtCheckedExceptions, modifiers, strTargetConstructorClass) -> {
            Class<?> serializationTargetClass = resolveClass(strTargetSerializationClass, imageClassLoader);
            UserError.guarantee(serializationTargetClass != null, "Cannot find serialization target class %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                            " Please make sure it is in the classpath", strTargetSerializationClass);
            Class<?>[] parameterTypes = Arrays.stream(srtParameterTypes).map(parameterType -> resolveClass(parameterType, imageClassLoader)).toArray(Class[]::new);
            Class<?>[] checkedExceptions = Arrays.stream(srtCheckedExceptions).map(parameterType -> resolveClass(parameterType, imageClassLoader)).toArray(Class[]::new);
            Class<?> targetConstructor = strTargetConstructorClass.length() == 0 ? null : resolveClass(strTargetConstructorClass, imageClassLoader);
            if (targetConstructor != null) {
                serializationSupport.addSerializationConstructorAccessorClass(serializationTargetClass, parameterTypes, checkedExceptions, modifiers, targetConstructor);
            }
            addReflections(reflectionData, serializationTargetClass, targetConstructor);
        };

        SerializationConfigurationParser parser = new SerializationConfigurationParser(serializationAdapter);
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFiles.SERIALIZATION_NAME);
        String exceptionsMsg = serializationSupport.collectMultiDefinitions();
        // Checkstyle: stop
        if (exceptionsMsg.length() > 0) {
            System.out.println(exceptionsMsg);
            if (!NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                BigBang bb = access.getBigBang();
                bb.getUnsupportedFeatures().addMessage("Unsupported dynamic features", null,
                                "To allow continuing compilation with above unsupported features, set " +
                                                SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+"));
            } else {
                System.out.println("Compilation will continue because " + SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+") +
                                " was set. But the program may behave unexpectedly at runtime.");
            }
        }
        // Checkstyle: resume
    }

    public static void addReflections(ReflectionDataBuilder reflectionData, Class<?> serializationTargetClass, Class<?> targetConstructor) {
        if (targetConstructor != null) {
            try {
                reflectionData.register(targetConstructor.getDeclaredConstructor());
            } catch (NoSuchMethodException e) {
                VMError.shouldNotReachHere();
            }
        }

        if(Externalizable.class.isAssignableFrom(serializationTargetClass)){
            try {
                reflectionData.register(serializationTargetClass.getDeclaredConstructor((Class<?>[]) null));
            }catch (NoSuchMethodException e) {
            }
        }

        reflectionData.register(serializationTargetClass);
        boolean registerAllMethods = false;
        boolean registerAllFieds = false;
        try {
            serializationTargetClass.getDeclaredField("serialVersionUID");
        } catch (NoSuchFieldException e) {
            registerAllMethods = true;
            registerAllFieds = true;
            reflectionData.register(serializationTargetClass.getDeclaredConstructors());
        }

        registerMethods(reflectionData, serializationTargetClass, registerAllMethods);
        registerFields(reflectionData, serializationTargetClass, registerAllFieds);
    }

    private static void registerMethods(ReflectionDataBuilder reflectionData, Class<?> serializationTargetClass, boolean registerAllMethods) {
        for (Method m : serializationTargetClass.getDeclaredMethods()) {
            boolean register;
            switch (m.getName()) {
                case "readObject":
                    register = m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals("java.io.ObjectInputStream");
                    break;
                case "writeObject":
                    register = m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals("java.io.ObjectOutputStream");
                    break;
                case "readObjectNoData":
                case "writeReplace":
                case "readResolve":
                    register = true;
                    break;
                default:
                    register = false;
            }
            if (register || registerAllMethods) {
                reflectionData.register(m);
            }
        }
    }

    private static void registerFields(ReflectionDataBuilder reflectionData, Class<?> serializationTargetClass, boolean registerAllFieds) {
        int mask = Modifier.STATIC | Modifier.TRANSIENT;
        int staticFinalMask = Modifier.STATIC | Modifier.FINAL;
        int privateStaticFinalMask = Modifier.PRIVATE | staticFinalMask;

        Set<String> serialPersistentFieldNames = new HashSet<>();
        try {
            Field f = serializationTargetClass.getDeclaredField("serialPersistentFields");
            if ((f.getModifiers() & privateStaticFinalMask) == privateStaticFinalMask) {
                f.setAccessible(true);
                ObjectStreamField[] serialPersistentFields = (ObjectStreamField[]) f.get(null);
                for (int i = 0; i < serialPersistentFields.length; i++) {
                    serialPersistentFieldNames.add(serialPersistentFields[i].getName());
                }
            }
        } catch (Exception e) {
        }

        for (Field f : serializationTargetClass.getDeclaredFields()) {
            int modifiers = f.getModifiers();
            boolean allowWrite = false;
            boolean allowUnsafeAccess = false;
            if ((modifiers & staticFinalMask) != staticFinalMask) {
                allowWrite = Modifier.isFinal(f.getModifiers());
                allowUnsafeAccess = !Modifier.isStatic(f.getModifiers());
            }
            boolean registerField=false;
            String fieldName = f.getName();
            if(serialPersistentFieldNames.contains(fieldName)){
                registerField = true;
            }else {
                switch (fieldName) {
                    case "serialPersistentFields":
                        if ((modifiers & privateStaticFinalMask) == privateStaticFinalMask) {
                            registerField = true;
                        }
                        break;
                    case "serialVersionUID":
                        if ((modifiers & staticFinalMask) == staticFinalMask) {
                            registerField = true;
                        }
                        break;
                    default:
                        if ((modifiers & mask) == 0) {
                            registerField = true;
                        }
                }
            }
            if(registerField|| registerAllFieds) {
                reflectionData.register(allowWrite, allowUnsafeAccess, f);
            }
        }
    }

    private Class<?> resolveClass(String typeName, ImageClassLoader imageClassLoader) {
        Class<?> ret = imageClassLoader.findClassByName(typeName, false);
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

    private void handleError(String message) {
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
