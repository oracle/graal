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

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.config.ConfigurationFileCollection;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.FieldInfo;
import com.oracle.svm.configure.config.PredefinedClassesConfiguration;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.core.util.VMError;

public class OmitPreviousConfigTests {

    private static final String PREVIOUS_CONFIG_DIR_NAME = "prev-config-dir";
    private static final String CURRENT_CONFIG_DIR_NAME = "config-dir";

    private static ConfigurationSet loadTraceProcessorFromResourceDirectory(String resourceDirectory, ConfigurationSet omittedConfig) {
        try {
            ConfigurationFileCollection configurationFileCollection = new ConfigurationFileCollection();
            configurationFileCollection.addDirectory(resourceFileName -> {
                try {
                    String resourceName = resourceDirectory + "/" + resourceFileName;
                    URL resourceURL = OmitPreviousConfigTests.class.getResource(resourceName);
                    return (resourceURL != null) ? resourceURL.toURI() : null;
                } catch (Exception e) {
                    throw VMError.shouldNotReachHere("Unexpected error while locating the configuration files.", e);
                }
            });

            Function<IOException, Exception> handler = e -> {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Assert.fail("Exception occurred while loading configuration: " + e + System.lineSeparator() + sw);
                return e;
            };
            Predicate<String> shouldExcludeClassesWithHash = null;
            if (omittedConfig != null) {
                shouldExcludeClassesWithHash = omittedConfig.getPredefinedClassesConfiguration()::containsClassWithHash;
            }
            return configurationFileCollection.loadConfigurationSet(handler, null, shouldExcludeClassesWithHash);
        } catch (Exception e) {
            throw VMError.shouldNotReachHere("Unexpected error while loading the configuration files.", e);
        }
    }

    @Test
    public void testSameConfig() {
        ConfigurationSet omittedConfig = loadTraceProcessorFromResourceDirectory(PREVIOUS_CONFIG_DIR_NAME, null);
        ConfigurationSet config = loadTraceProcessorFromResourceDirectory(PREVIOUS_CONFIG_DIR_NAME, omittedConfig);
        config = config.copyAndSubtract(omittedConfig);

        assertTrue(config.getJniConfiguration().isEmpty());
        assertTrue(config.getReflectionConfiguration().isEmpty());
        assertTrue(config.getProxyConfiguration().isEmpty());
        assertTrue(config.getResourceConfiguration().isEmpty());
        assertTrue(config.getSerializationConfiguration().isEmpty());
        assertTrue(config.getPredefinedClassesConfiguration().isEmpty());
    }

    @Test
    public void testConfigDifference() {
        ConfigurationSet omittedConfig = loadTraceProcessorFromResourceDirectory(PREVIOUS_CONFIG_DIR_NAME, null);
        ConfigurationSet config = loadTraceProcessorFromResourceDirectory(CURRENT_CONFIG_DIR_NAME, omittedConfig);
        config = config.copyAndSubtract(omittedConfig);

        doTestGeneratedTypeConfig();
        doTestTypeConfig(config.getJniConfiguration());

        doTestProxyConfig(config.getProxyConfiguration());

        doTestResourceConfig(config.getResourceConfiguration());

        doTestSerializationConfig(config.getSerializationConfiguration());

        doTestPredefinedClassesConfig(config.getPredefinedClassesConfiguration());
    }

    private static void doTestGeneratedTypeConfig() {
        TypeMethodsWithFlagsTest typeMethodsWithFlagsTestDeclared = new TypeMethodsWithFlagsTest(ConfigurationMemberDeclaration.DECLARED);
        typeMethodsWithFlagsTestDeclared.doTest();

        TypeMethodsWithFlagsTest typeMethodsWithFlagsTestPublic = new TypeMethodsWithFlagsTest(ConfigurationMemberDeclaration.PUBLIC);
        typeMethodsWithFlagsTestPublic.doTest();

        TypeMethodsWithFlagsTest typeMethodsWithFlagsTestDeclaredPublic = new TypeMethodsWithFlagsTest(ConfigurationMemberDeclaration.DECLARED_AND_PUBLIC);
        typeMethodsWithFlagsTestDeclaredPublic.doTest();
    }

    private static void doTestTypeConfig(TypeConfiguration typeConfig) {
        doTestExpectedMissingTypes(typeConfig);

        doTestTypeFlags(typeConfig);

        doTestFields(typeConfig);

        doTestMethods(typeConfig);
    }

    private static void doTestExpectedMissingTypes(TypeConfiguration typeConfig) {
        Assert.assertNull(typeConfig.get(ConfigurationCondition.alwaysTrue(), "FlagTestA"));
        Assert.assertNull(typeConfig.get(ConfigurationCondition.alwaysTrue(), "FlagTestB"));
    }

    private static void doTestTypeFlags(TypeConfiguration typeConfig) {
        ConfigurationType flagTestHasDeclaredType = getConfigTypeOrFail(typeConfig, "FlagTestC");
        Assert.assertTrue(ConfigurationType.TestBackdoor.haveAllDeclaredClasses(flagTestHasDeclaredType) || ConfigurationType.TestBackdoor.haveAllDeclaredFields(flagTestHasDeclaredType) ||
                        ConfigurationType.TestBackdoor.getAllDeclaredConstructors(flagTestHasDeclaredType) == ConfigurationMemberAccessibility.ACCESSED);

        ConfigurationType flagTestHasPublicType = getConfigTypeOrFail(typeConfig, "FlagTestD");
        Assert.assertTrue(ConfigurationType.TestBackdoor.haveAllPublicClasses(flagTestHasPublicType) || ConfigurationType.TestBackdoor.haveAllPublicFields(flagTestHasPublicType) ||
                        ConfigurationType.TestBackdoor.getAllPublicConstructors(flagTestHasPublicType) == ConfigurationMemberAccessibility.ACCESSED);
    }

    private static void doTestFields(TypeConfiguration typeConfig) {
        ConfigurationType fieldTestType = getConfigTypeOrFail(typeConfig, "MethodAndFieldTest");

        Assert.assertNull(ConfigurationType.TestBackdoor.getFieldInfoIfPresent(fieldTestType, "SimpleField"));
        Assert.assertNull(ConfigurationType.TestBackdoor.getFieldInfoIfPresent(fieldTestType, "AllowWriteField"));

        FieldInfo newField = ConfigurationType.TestBackdoor.getFieldInfoIfPresent(fieldTestType, "NewField");
        Assert.assertFalse(newField.isFinalButWritable());

        FieldInfo newWritableField = getFieldInfoOrFail(fieldTestType, "NewAllowWriteField");
        Assert.assertTrue(newWritableField.isFinalButWritable());

        FieldInfo newlyWritableField = getFieldInfoOrFail(fieldTestType, "NewNowWritableField");
        Assert.assertTrue(newlyWritableField.isFinalButWritable());
    }

    private static void doTestMethods(TypeConfiguration typeConfig) {
        ConfigurationType methodTestType = getConfigTypeOrFail(typeConfig, "MethodAndFieldTest");

        Assert.assertNull(ConfigurationType.TestBackdoor.getMethodInfoIfPresent(methodTestType, new ConfigurationMethod("<init>", "(I)V")));
        Assert.assertNotNull(ConfigurationType.TestBackdoor.getMethodInfoIfPresent(methodTestType, new ConfigurationMethod("method", "()V")));
    }

    private static void doTestProxyConfig(ProxyConfiguration proxyConfig) {
        ConfigurationCondition condition = ConfigurationCondition.alwaysTrue();
        Assert.assertFalse(proxyConfig.contains(condition, "testProxySeenA", "testProxySeenB", "testProxySeenC"));
        Assert.assertTrue(proxyConfig.contains(condition, "testProxyUnseen"));
    }

    private static void doTestResourceConfig(ResourceConfiguration resourceConfig) {
        Assert.assertFalse(resourceConfig.anyResourceMatches("seenResource.txt"));
        Assert.assertTrue(resourceConfig.anyResourceMatches("unseenResource.txt"));

        ConfigurationCondition condition = ConfigurationCondition.alwaysTrue();
        Assert.assertFalse(resourceConfig.anyBundleMatches(condition, "seenBundle"));
        Assert.assertTrue(resourceConfig.anyBundleMatches(condition, "unseenBundle"));
    }

    private static void doTestSerializationConfig(SerializationConfiguration serializationConfig) {
        ConfigurationCondition condition = ConfigurationCondition.alwaysTrue();
        Assert.assertFalse(serializationConfig.contains(condition, "seenType", null));
        Assert.assertTrue(serializationConfig.contains(condition, "unseenType", null));
    }

    private static ConfigurationType getConfigTypeOrFail(TypeConfiguration typeConfig, String typeName) {
        ConfigurationType type = typeConfig.get(ConfigurationCondition.alwaysTrue(), typeName);
        Assert.assertNotNull(type);
        return type;
    }

    private static FieldInfo getFieldInfoOrFail(ConfigurationType type, String field) {
        FieldInfo fieldInfo = ConfigurationType.TestBackdoor.getFieldInfoIfPresent(type, field);
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

    final ConfigurationMemberDeclaration methodKind;

    final Map<ConfigurationMethod, ConfigurationMemberDeclaration> methodsThatMustExist = new HashMap<>();
    final Map<ConfigurationMethod, ConfigurationMemberDeclaration> methodsThatMustNotExist = new HashMap<>();

    final TypeConfiguration previousConfig = new TypeConfiguration("");
    final TypeConfiguration currentConfig = new TypeConfiguration("");

    TypeMethodsWithFlagsTest(ConfigurationMemberDeclaration methodKind) {
        this.methodKind = methodKind;
        generateTestMethods();
        populateConfig();
    }

    void generateTestMethods() {
        Map<ConfigurationMethod, ConfigurationMemberDeclaration> targetMap;

        targetMap = getMethodsMap(ConfigurationMemberDeclaration.DECLARED);
        targetMap.put(new ConfigurationMethod("<init>", INTERNAL_SIGNATURE_ONE), ConfigurationMemberDeclaration.DECLARED);
        targetMap.put(new ConfigurationMethod("testMethodDeclaredSpecificSignature", INTERNAL_SIGNATURE_ONE), ConfigurationMemberDeclaration.DECLARED);
        targetMap.put(new ConfigurationMethod("testMethodDeclaredMatchesAllSignature", null), ConfigurationMemberDeclaration.DECLARED);

        targetMap = getMethodsMap(ConfigurationMemberDeclaration.PUBLIC);
        targetMap.put(new ConfigurationMethod("<init>", INTERNAL_SIGNATURE_TWO), ConfigurationMemberDeclaration.PUBLIC);
        targetMap.put(new ConfigurationMethod("testMethodPublicSpecificSignature", INTERNAL_SIGNATURE_ONE), ConfigurationMemberDeclaration.PUBLIC);
        targetMap.put(new ConfigurationMethod("testMethodPublicMatchesAllSignature", null), ConfigurationMemberDeclaration.PUBLIC);
    }

    Map<ConfigurationMethod, ConfigurationMemberDeclaration> getMethodsMap(ConfigurationMemberDeclaration otherKind) {
        if (methodKind.equals(otherKind) || methodKind.equals(ConfigurationMemberDeclaration.DECLARED_AND_PUBLIC)) {
            return methodsThatMustNotExist;
        }
        return methodsThatMustExist;
    }

    void populateConfig() {
        ConfigurationType oldType = new ConfigurationType(ConfigurationCondition.alwaysTrue(), getTypeName());
        setFlags(oldType);
        previousConfig.add(oldType);

        ConfigurationType newType = new ConfigurationType(ConfigurationCondition.alwaysTrue(), getTypeName());
        for (Map.Entry<ConfigurationMethod, ConfigurationMemberDeclaration> methodEntry : methodsThatMustExist.entrySet()) {
            newType.addMethod(methodEntry.getKey().getName(), methodEntry.getKey().getInternalSignature(), methodEntry.getValue());
        }
        for (Map.Entry<ConfigurationMethod, ConfigurationMemberDeclaration> methodEntry : methodsThatMustNotExist.entrySet()) {
            newType.addMethod(methodEntry.getKey().getName(), methodEntry.getKey().getInternalSignature(), methodEntry.getValue());
        }
        currentConfig.add(newType);
    }

    void setFlags(ConfigurationType config) {
        if (methodKind.equals(ConfigurationMemberDeclaration.DECLARED) || methodKind.equals(ConfigurationMemberDeclaration.DECLARED_AND_PUBLIC)) {
            config.setAllDeclaredClasses();
            config.setAllDeclaredConstructors(ConfigurationMemberAccessibility.ACCESSED);
            config.setAllDeclaredMethods(ConfigurationMemberAccessibility.ACCESSED);
            config.setAllDeclaredFields();
        }
        if (methodKind.equals(ConfigurationMemberDeclaration.PUBLIC) || methodKind.equals(ConfigurationMemberDeclaration.DECLARED_AND_PUBLIC)) {
            config.setAllPublicClasses();
            config.setAllPublicConstructors(ConfigurationMemberAccessibility.ACCESSED);
            config.setAllPublicMethods(ConfigurationMemberAccessibility.ACCESSED);
            config.setAllPublicFields();
        }
    }

    String getTypeName() {
        return TEST_CLASS_NAME_PREFIX + "_" + methodKind.name();
    }

    void doTest() {
        TypeConfiguration currentConfigWithoutPrevious = currentConfig.copyAndSubtract(previousConfig);

        String name = getTypeName();
        ConfigurationType configurationType = currentConfigWithoutPrevious.get(ConfigurationCondition.alwaysTrue(), name);
        if (methodsThatMustExist.size() == 0) {
            Assert.assertNull("Generated configuration type " + name + " exists. Expected it to be cleared as it is empty.", configurationType);
        } else {
            Assert.assertNotNull("Generated configuration type " + name + " does not exist. Has the test code changed?", configurationType);

            for (Map.Entry<ConfigurationMethod, ConfigurationMemberDeclaration> methodEntry : methodsThatMustExist.entrySet()) {
                ConfigurationMemberDeclaration kind = ConfigurationType.TestBackdoor.getMethodInfoIfPresent(configurationType, methodEntry.getKey()).getDeclaration();
                Assert.assertNotNull("Method " + methodEntry.getKey() + " unexpectedly NOT found in the new configuration.", kind);
                Assert.assertEquals("Method " + methodEntry.getKey() + " contains a different kind than expected in the new configuration.", kind, methodEntry.getValue());
            }
            for (Map.Entry<ConfigurationMethod, ConfigurationMemberDeclaration> methodEntry : methodsThatMustNotExist.entrySet()) {
                ConfigurationMemberInfo kind = ConfigurationType.TestBackdoor.getMethodInfoIfPresent(configurationType, methodEntry.getKey());
                Assert.assertNull("Method " + methodEntry.getKey() + " unexpectedly found in the new configuration.", kind);
            }
        }
    }
}
