/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.reflect.dynamicclasses.hosted;

// Checkstyle: allow reflection

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.DynamicClassesConfigurationParser;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.MultiClassLoaderReporter;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AutomaticFeature
public class DynamicClassesFeature implements Feature {

    class DynamicClassContainer {
        Class<?> clazz;
        String name;
        String checksum;

        DynamicClassContainer(Class<?> clazz, String name, String checksum) {
            this.clazz = clazz;
            this.name = name;
            this.checksum = checksum;
        }
    }

    private Map<String, DynamicClassContainer> dynamicClassContainers = new HashMap<>();
    private List<StringBuilder> errMsgs = new ArrayList<>();

    /**
     * Reflectively call protected method ClassLoader.defineClass(String. byte[], int, int)
     *
     * @param name target class name
     * @param classLoader the class loader
     * @param classData class data byte array
     * @return the class defined with the given name
     */
    private static Class<?> callDefineClass(String name, ClassLoader classLoader, byte[] classData) throws IllegalAccessException, InvocationTargetException {
        Method defineClass = ReflectionUtil.lookupMethod(ClassLoader.class, "defineClass", String.class, byte[].class, int.class, int.class);
        return (Class<?>) defineClass.invoke(classLoader, name, classData, 0, classData.length);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        ClassLoader imageClassLoader = access.getImageClassLoader().getClassLoader();
        DynamicClassesConfigurationParser.DynamicClassesParserFunction adapter = (definedClassName, dumpedFilePath, checksum) -> {
            try (
                            FileInputStream fio = new FileInputStream(dumpedFilePath.toFile());
                            FileChannel fileChannel = fio.getChannel();) {
                ByteBuffer byteBuffer = ByteBuffer.allocate((int) fileChannel.size());
                while (fileChannel.read(byteBuffer) > 0) {
                    // Reading file. Do nothing
                }
                Class<?> generatedClass = callDefineClass(definedClassName, imageClassLoader, byteBuffer.array());
                UserError.guarantee(generatedClass != null,
                                "Cannot find pre-dumped class file %s for dynamic generated class %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                                                " Please make sure it is in the classpath",
                                dumpedFilePath, definedClassName);
                UserError.guarantee(generatedClass.getName().equals(definedClassName), "Pre-dumped generated class file %s's class name is %s, which does not match with configured name %s",
                                dumpedFilePath, generatedClass.getName(), definedClassName);
                DynamicClassContainer exitingEntity = dynamicClassContainers.putIfAbsent(definedClassName, new DynamicClassContainer(generatedClass, definedClassName, checksum));
                if (exitingEntity != null && !exitingEntity.checksum.equals(checksum)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Suspicious multiple-classloader usage is detected from dynamicClasses configurations:\n");
                    sb.append("Dynamically defined class (name=").append(definedClassName).append(", checksum=").append(checksum).append(")");
                    sb.append(" is already registered with checksum ").append(exitingEntity.checksum);
                    errMsgs.add(sb);
                }
            } catch (Exception e) {
                VMError.shouldNotReachHere("Cannot load required class " + definedClassName, e);
            }
        };

        DynamicClassesConfigurationParser parser = new DynamicClassesConfigurationParser(adapter);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "dynamicClasses",
                        ConfigurationFiles.Options.DynamicClassesConfigurationFiles, ConfigurationFiles.Options.DynamicClassesConfigurationResources,
                        ConfigurationFiles.DYNAMIC_CLASSES_NAME);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (errMsgs.size() > 0) {
            for (StringBuilder sb : errMsgs) {
                MultiClassLoaderReporter.reportError(((FeatureImpl.DuringSetupAccessImpl) access).getBigBang(), MultiClassLoaderReporter.MULTIPLE_CHECKSUMS, sb.toString());
            }
        }
        for (DynamicClassContainer d : dynamicClassContainers.values()) {
            ClassForNameSupport.registerDynamicGeneratedClass(d.clazz, d.name, d.checksum);
        }
    }
}
