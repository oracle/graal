/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.config;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.oracle.svm.configure.config.PredefinedClassesConfiguration;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.config.ConfigurationMemberKind;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.FieldInfo;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.util.UserError;

public class OmitPreviousConfigTests {

    private static final String PREVIOUS_CONFIG_DIR_NAME = "prev-config-dir";
    private static final String CURRENT_CONFIG_DIR_NAME = "config-dir";

    private static TraceProcessor loadTraceProcessorFromResourceDirectory(String resourceDirectory, TraceProcessor previous) {
        try {
            ConfigurationSet configurationSet = new ConfigurationSet();
            configurationSet.addDirectory(resourceFileName -> {
                try {
                    String resourceName = resourceDirectory + "/" + resourceFileName;
                    URL resourceURL = OmitPreviousConfigTests.class.getResource(resourceName);
                    if (resourceURL == null) {
                        Assert.fail("Configuration file " + resourceName + " does not exist. Make sure that the test or the config directory have not been moved.");
                    }
                    return resourceURL.toURI();
                } catch (Exception e) {
                    throw UserError.abort(e, "Unexpected error while locating the configuration files.");
                }
            });

            AccessAdvisor unusedAdvisor = new AccessAdvisor();

            Function<IOException, Exception> handler = e -> {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Assert.fail("Exception occurred while loading configuration: " + e + System.lineSeparator() + sw);
                return e;
            };
            Predicate<String> shouldExcludeClassesWithHash = null;
            if (previous != null) {
                shouldExcludeClassesWithHash = previous.getPredefinedClassesConfiguration()::containsClassWithHash;
            }
            return new TraceProcessor(unusedAdvisor, configurationSet.loadJniConfig(handler), configurationSet.loadReflectConfig(handler), configurationSet.loadProxyConfig(handler),
                            configurationSet.loadResourceConfig(handler), configurationSet.loadSerializationConfig(handler),
                            configurationSet.loadPredefinedClassesConfig(null, shouldExcludeClassesWithHash, handler), previous);
        } catch (Exception e) {
            throw UserError.abort(e, "Unexpected error while loading the configuration files.");
        }
    }

    @Test
    public void testSameConfig() {
        TraceProcessor previousConfigProcessor = loadTraceProcessorFromResourceDirectory(PREVIOUS_CONFIG_DIR_NAME, null);
        TraceProcessor sameConfigProcessor = loadTraceProcessorFromResourceDirectory(PREVIOUS_CONFIG_DIR_NAME, previousConfigProcessor);

        assertTrue(sameConfigProcessor.getJniConfiguration().isEmpty());
        assertTrue(sameConfigProcessor.getReflectionConfiguration().isEmpty());
        assertTrue(sameConfigProcessor.getProxyConfiguration().isEmpty());
        assertTrue(sameConfigProcessor.getResourceConfiguration().isEmpty());
        assertTrue(sameConfigProcessor.getSerializationConfiguration().isEmpty());
        assertTrue(sameConfigProcessor.getPredefinedClassesConfiguration().isEmpty());
    }

    @Test
    public void testConfigDifference() {
        TraceProcessor previousConfigProcessor = loadTraceProcessorFromResourceDirectory(PREVIOUS_CONFIG_DIR_NAME, null);
        TraceProcessor currentConfigProcessor = loadTraceProcessorFromResourceDirectory(CURRENT_CONFIG_DIR_NAME, previousConfigProcessor);

        doTestGeneratedTypeConfig();
        doTestTypeConfig(currentConfigProcessor.getJniConfiguration());

        doTestProxyConfig(currentConfigProcessor.getProxyConfiguration());

        doTestResourceConfig(currentConfigProcessor.getResourceConfiguration());

        doTestSerializationConfig(currentConfigProcessor.getSerializationConfiguration());

        doTestPredefinedClassesConfig(currentConfigProcessor.getPredefinedClassesConfiguration());
    }

    private static void doTestGeneratedTypeConfig() {
        TypeMethodsWithFlagsTest typeMethodsWithFlagsTestDeclared = new TypeMethodsWithFlagsTest(ConfigurationMemberKind.DECLARED);
        typeMethodsWithFlagsTestDeclared.doTest();

        TypeMethodsWithFlagsTest typeMethodsWithFlagsTestPublic = new TypeMethodsWithFlagsTest(ConfigurationMemberKind.PUBLIC);
        typeMethodsWithFlagsTestPublic.doTest();

        TypeMethodsWithFlagsTest typeMethodsWithFlagsTestDeclaredPublic = new TypeMethodsWithFlagsTest(ConfigurationMemberKind.DECLARED_AND_PUBLIC);
        typeMethodsWithFlagsTestDeclaredPublic.doTest();
    }

    private static void doTestTypeConfig(TypeConfiguration typeConfig) {
        doTestExpectedMissingTypes(typeConfig);

        doTestTypeFlags(typeConfig);

        doTestFields(typeConfig);

        doTestMethods(typeConfig);
    }

    private static void doTestExpectedMissingTypes(TypeConfiguration typeConfig) {
        Assert.assertNull(typeConfig.get("FlagTestA"));
        Assert.assertNull(typeConfig.get("FlagTestB"));
    }

    private static void doTestTypeFlags(TypeConfiguration typeConfig) {
        ConfigurationType flagTestHasDeclaredType = getConfigTypeOrFail(typeConfig, "FlagTestC");
        Assert.assertTrue(flagTestHasDeclaredType.haveAllDeclaredClasses() || flagTestHasDeclaredType.haveAllDeclaredFields() || flagTestHasDeclaredType.haveAllDeclaredConstructors());

        ConfigurationType flagTestHasPublicType = getConfigTypeOrFail(typeConfig, "FlagTestD");
        Assert.assertTrue(flagTestHasPublicType.haveAllPublicClasses() || flagTestHasPublicType.haveAllPublicFields() || flagTestHasPublicType.haveAllPublicConstructors());
    }

    private static void doTestFields(TypeConfiguration typeConfig) {
        ConfigurationType fieldTestType = getConfigTypeOrFail(typeConfig, "MethodAndFieldTest");

        Assert.assertNull(fieldTestType.getFieldInfoIfPresent("SimpleField"));
        Assert.assertNull(fieldTestType.getFieldInfoIfPresent("AllowWriteField"));

        FieldInfo newField = fieldTestType.getFieldInfoIfPresent("NewField");
        Assert.assertFalse(newField.isFinalButWritable());

        FieldInfo newWritableField = getFieldInfoOrFail(fieldTestType, "NewAllowWriteField");
        Assert.assertTrue(newWritableField.isFinalButWritable());

        FieldInfo newlyWritableField = getFieldInfoOrFail(fieldTestType, "NewNowWritableField");
        Assert.assertTrue(newlyWritableField.isFinalButWritable());
    }

    private static void doTestMethods(TypeConfiguration typeConfig) {
        ConfigurationType methodTestType = getConfigTypeOrFail(typeConfig, "MethodAndFieldTest");

        Assert.assertNull(methodTestType.getMethodKindIfPresent(new ConfigurationMethod("<init>", "(I)V")));
        Assert.assertNotNull(methodTestType.getMethodKindIfPresent(new ConfigurationMethod("method", "()V")));
    }

    private static void doTestProxyConfig(ProxyConfiguration proxyConfig) {
        Assert.assertFalse(proxyConfig.contains("testProxySeenA", "testProxySeenB", "testProxySeenC"));
        Assert.assertTrue(proxyConfig.contains("testProxyUnseen"));
    }

    private static void doTestResourceConfig(ResourceConfiguration resourceConfig) {
        Assert.assertFalse(resourceConfig.anyResourceMatches("seenResource.txt"));
        Assert.assertTrue(resourceConfig.anyResourceMatches("unseenResource.txt"));

        Assert.assertFalse(resourceConfig.anyBundleMatches("seenBundle"));
        Assert.assertTrue(resourceConfig.anyBundleMatches("unseenBundle"));
    }

    private static void doTestSerializationConfig(SerializationConfiguration serializationConfig) {
        Assert.assertFalse(serializationConfig.contains("seenType", null));
        Assert.assertTrue(serializationConfig.contains("unseenType", null));
    }

    private static ConfigurationType getConfigTypeOrFail(TypeConfiguration typeConfig, String typeName) {
        ConfigurationType type = typeConfig.get(typeName);
        Assert.assertNotNull(type);
        return type;
    }

    private static FieldInfo getFieldInfoOrFail(ConfigurationType type, String field) {
        FieldInfo fieldInfo = type.getFieldInfoIfPresent(field);
        Assert.assertNotNull(fieldInfo);
        return fieldInfo;
    }

    private static void doTestPredefinedClassesConfig(PredefinedClassesConfiguration predefinedClassesConfig) {
        Assert.assertFalse("Must not contain a previously seen class.", predefinedClassesConfig.containsClassWithName("previouslySeenClass"));
        Assert.assertTrue("Must contain a newly seen class.", predefinedClassesConfig.containsClassWithName("unseenClass"));
    }
}

class TypeMethodsWithFlagsTest {

    static final String TEST_CLASS_NAME_PREFIX = "MethodsWithFlagsType";
    static final String INTERNAL_SIGNATURE_ONE = "([Ljava/lang/String;)V";
    static final String INTERNAL_SIGNATURE_TWO = "([Ljava/lang/String;Ljava/lang/String;)V";

    final ConfigurationMemberKind methodKind;

    final Map<ConfigurationMethod, ConfigurationMemberKind> methodsThatMustExist = new HashMap<>();
    final Map<ConfigurationMethod, ConfigurationMemberKind> methodsThatMustNotExist = new HashMap<>();

    final TypeConfiguration previousConfig = new TypeConfiguration();
    final TypeConfiguration currentConfig = new TypeConfiguration();

    TypeMethodsWithFlagsTest(ConfigurationMemberKind methodKind) {
        this.methodKind = methodKind;
        generateTestMethods();
        populateConfig();
        currentConfig.removeAll(previousConfig);
    }

    void generateTestMethods() {
        Map<ConfigurationMethod, ConfigurationMemberKind> targetMap;

        targetMap = getMethodsMap(ConfigurationMemberKind.DECLARED);
        targetMap.put(new ConfigurationMethod("<init>", INTERNAL_SIGNATURE_ONE), ConfigurationMemberKind.DECLARED);
        targetMap.put(new ConfigurationMethod("testMethodDeclaredSpecificSignature", INTERNAL_SIGNATURE_ONE), ConfigurationMemberKind.DECLARED);
        targetMap.put(new ConfigurationMethod("testMethodDeclaredMatchesAllSignature", null), ConfigurationMemberKind.DECLARED);

        targetMap = getMethodsMap(ConfigurationMemberKind.PUBLIC);
        targetMap.put(new ConfigurationMethod("<init>", INTERNAL_SIGNATURE_TWO), ConfigurationMemberKind.PUBLIC);
        targetMap.put(new ConfigurationMethod("testMethodPublicSpecificSignature", INTERNAL_SIGNATURE_ONE), ConfigurationMemberKind.PUBLIC);
        targetMap.put(new ConfigurationMethod("testMethodPublicMatchesAllSignature", null), ConfigurationMemberKind.PUBLIC);
    }

    Map<ConfigurationMethod, ConfigurationMemberKind> getMethodsMap(ConfigurationMemberKind otherKind) {
        if (methodKind.equals(otherKind) || methodKind.equals(ConfigurationMemberKind.DECLARED_AND_PUBLIC)) {
            return methodsThatMustNotExist;
        }
        return methodsThatMustExist;
    }

    void populateConfig() {
        ConfigurationType oldType = new ConfigurationType(getTypeName());
        setFlags(oldType);
        previousConfig.add(oldType);

        ConfigurationType newType = new ConfigurationType(getTypeName());
        for (Map.Entry<ConfigurationMethod, ConfigurationMemberKind> methodEntry : methodsThatMustExist.entrySet()) {
            newType.addMethod(methodEntry.getKey().getName(), methodEntry.getKey().getInternalSignature(), methodEntry.getValue());
        }
        for (Map.Entry<ConfigurationMethod, ConfigurationMemberKind> methodEntry : methodsThatMustNotExist.entrySet()) {
            newType.addMethod(methodEntry.getKey().getName(), methodEntry.getKey().getInternalSignature(), methodEntry.getValue());
        }
        currentConfig.add(newType);
    }

    void setFlags(ConfigurationType config) {
        if (methodKind.equals(ConfigurationMemberKind.DECLARED) || methodKind.equals(ConfigurationMemberKind.DECLARED_AND_PUBLIC)) {
            config.setAllDeclaredClasses();
            config.setAllDeclaredConstructors();
            config.setAllDeclaredMethods();
            config.setAllDeclaredFields();
        }
        if (methodKind.equals(ConfigurationMemberKind.PUBLIC) || methodKind.equals(ConfigurationMemberKind.DECLARED_AND_PUBLIC)) {
            config.setAllPublicClasses();
            config.setAllPublicConstructors();
            config.setAllPublicMethods();
            config.setAllPublicFields();
        }
    }

    String getTypeName() {
        return TEST_CLASS_NAME_PREFIX + "_" + methodKind.name();
    }

    void doTest() {
        String name = getTypeName();
        ConfigurationType configurationType = currentConfig.get(name);
        if (methodsThatMustExist.size() == 0) {
            Assert.assertNull("Generated configuration type " + name + " exists. Expected it to be cleared as it is empty.", configurationType);
        } else {
            Assert.assertNotNull("Generated configuration type " + name + " does not exist. Has the test code changed?", configurationType);

            for (Map.Entry<ConfigurationMethod, ConfigurationMemberKind> methodEntry : methodsThatMustExist.entrySet()) {
                ConfigurationMemberKind kind = configurationType.getMethodKindIfPresent(methodEntry.getKey());
                Assert.assertNotNull("Method " + methodEntry.getKey() + " unexpectedly NOT found in the new configuration.", kind);
                Assert.assertEquals("Method " + methodEntry.getKey() + " contains a different kind than expected in the new configuration.", kind, methodEntry.getValue());
            }
            for (Map.Entry<ConfigurationMethod, ConfigurationMemberKind> methodEntry : methodsThatMustNotExist.entrySet()) {
                ConfigurationMemberKind kind = configurationType.getMethodKindIfPresent(methodEntry.getKey());
                Assert.assertNull("Method " + methodEntry.getKey() + " unexpectedly found in the new configuration.", kind);
            }
        }
    }
}
