/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedAccessCondition;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.configure.test.AddExports;
import com.oracle.svm.configure.trace.AccessAdvisor;

import jdk.graal.compiler.util.json.JsonParser;

@AddExports({"org.graalvm.nativeimage/org.graalvm.nativeimage.impl",
                "jdk.graal.compiler/jdk.graal.compiler.phases.common",
                "jdk.graal.compiler/jdk.graal.compiler.util",
                "jdk.graal.compiler/jdk.graal.compiler.util.json",
                "jdk.internal.vm.ci/jdk.vm.ci.meta"})
public class URLProtocolTraceProcessorTest {
    private static final String JAR_HANDLER = "sun.net.www.protocol.jar.Handler";
    private static final String JRT_HANDLER = "sun.net.www.protocol.jrt.Handler";

    @Test
    public void createURLStreamHandlerRegistersJrtHandlerConstructor() throws Exception {
        assertURLStreamHandlerConstructorRegistered("createURLStreamHandler", JRT_HANDLER);
    }

    @Test
    public void getURLStreamHandlerRegistersJarHandlerConstructor() throws Exception {
        assertURLStreamHandlerConstructorRegistered("getURLStreamHandler", JAR_HANDLER);
    }

    private static void assertURLStreamHandlerConstructorRegistered(String function, String handlerClassName) throws Exception {
        ConfigurationSet configurationSet = new ConfigurationSet();
        Object processor = newReflectionProcessor();

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "%s",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "com.example.UrlAgentRepro",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(function, handlerClassName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        ConfigurationType handlerType = getConfigurationType(reflectionConfiguration, handlerClassName);
        Assert.assertNotNull(handlerType);
        ConfigurationMemberInfo constructorInfo = getConstructorInfo(handlerType);
        Assert.assertEquals("DECLARED", constructorInfo.getDeclaration().toString());
        Assert.assertEquals("ACCESSED", constructorInfo.getAccessibility().toString());

        ConfigurationType factoryType = getConfigurationType(reflectionConfiguration, "java.net.URL$DefaultFactory");
        Assert.assertNull(factoryType);
    }

    private static Object newReflectionProcessor() throws Exception {
        Class<?> reflectionProcessorClass = Class.forName("com.oracle.svm.configure.trace.ReflectionProcessor");
        Constructor<?> constructor = reflectionProcessorClass.getDeclaredConstructor(AccessAdvisor.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new AccessAdvisor(false, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private static void processTrace(Object processor, ConfigurationSet configurationSet,
                    String trace) throws Exception {
        JsonParser parser = new JsonParser(new StringReader(trace));
        List<EconomicMap<String, Object>> entries = (List<EconomicMap<String, Object>>) parser.parse();
        Method processEntry = processor.getClass().getDeclaredMethod("processEntry", EconomicMap.class,
                        ConfigurationSet.class);
        processEntry.setAccessible(true);
        for (EconomicMap<String, Object> entry : entries) {
            processEntry.invoke(processor, entry, configurationSet);
        }
    }

    private static ConfigurationType getConfigurationType(TypeConfiguration reflectionConfiguration, String className) {
        return reflectionConfiguration.get(UnresolvedAccessCondition.unconditional(),
                        NamedConfigurationTypeDescriptor.fromReflectionName(className));
    }

    private static ConfigurationMemberInfo getConstructorInfo(ConfigurationType configurationType) {
        ConfigurationMethod constructorMethod = new ConfigurationMethod("<init>", "()V");
        return ConfigurationType.TestBackdoor.getMethodInfoIfPresent(configurationType, constructorMethod);
    }
}
