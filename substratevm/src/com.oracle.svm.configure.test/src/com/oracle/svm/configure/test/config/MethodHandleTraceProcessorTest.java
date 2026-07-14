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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedAccessCondition;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.configure.filters.ConfigurationFilter;
import com.oracle.svm.configure.filters.HierarchyFilterNode;
import com.oracle.svm.configure.test.AddExports;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;

@AddExports({"org.graalvm.nativeimage/org.graalvm.nativeimage.impl",
                "jdk.graal.compiler/jdk.graal.compiler.phases.common",
                "jdk.graal.compiler/jdk.graal.compiler.util",
                "jdk.graal.compiler/jdk.graal.compiler.util.json",
                "jdk.internal.vm.ci/jdk.vm.ci.meta"})
public class MethodHandleTraceProcessorTest {
    @Test
    public void excludedMethodHandleCallerRegistersAccessedApplicationMethod() throws Exception {
        String testClassName = "com.example.SpliteratorTest";
        ConfigurationSet configurationSet = new ConfigurationSet();
        TraceProcessor processor = newTraceProcessor();

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "findMethodHandle",
                            "class": "%s",
                            "declaring_class": "%s",
                            "caller_class": "java.lang.invoke.MethodHandleImpl$1",
                            "result": true,
                            "args": ["sortedSpliterators", []]
                          }
                        ]
                        """.formatted(testClassName, testClassName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        ConfigurationType testType = getConfigurationType(reflectionConfiguration, testClassName);
        Assert.assertNotNull(testType);
        ConfigurationMemberInfo methodInfo = getMethodInfo(testType, "sortedSpliterators");
        Assert.assertEquals("PRESENT", methodInfo.getDeclaration().toString());
        Assert.assertEquals("ACCESSED", methodInfo.getAccessibility().toString());
    }

    @Test
    public void customExcludedMethodHandleCallerDoesNotRegisterAccessedApplicationMethod() throws Exception {
        String testClassName = "com.example.Service";
        ConfigurationSet configurationSet = new ConfigurationSet();
        HierarchyFilterNode callerFilter = HierarchyFilterNode.createInclusiveRoot();
        callerFilter.addOrGetChildren("org.framework.Invoker", ConfigurationFilter.Inclusion.Exclude);
        TraceProcessor processor = newTraceProcessor(callerFilter);

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "findMethodHandle",
                            "class": "%s",
                            "declaring_class": "%s",
                            "caller_class": "org.framework.Invoker",
                            "result": true,
                            "args": ["run", []]
                          }
                        ]
                        """.formatted(testClassName, testClassName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        Assert.assertNull(getConfigurationType(reflectionConfiguration, testClassName));
    }

    @Test
    public void excludedMethodHandleCallerRegistersNonEnumValuesMethod() throws Exception {
        String testClassName = "com.example.ValueContainer";
        ConfigurationSet configurationSet = new ConfigurationSet();
        TraceProcessor processor = newTraceProcessor();

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "findMethodHandle",
                            "class": "%s",
                            "declaring_class": "%s",
                            "class_is_enum": false,
                            "caller_class": "java.lang.invoke.MethodHandleImpl$1",
                            "result": true,
                            "args": ["values", []]
                          }
                        ]
                        """.formatted(testClassName, testClassName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        ConfigurationType testType = getConfigurationType(reflectionConfiguration, testClassName);
        Assert.assertNotNull(testType);
        ConfigurationMemberInfo methodInfo = getMethodInfo(testType, "values");
        Assert.assertEquals("PRESENT", methodInfo.getDeclaration().toString());
        Assert.assertEquals("ACCESSED", methodInfo.getAccessibility().toString());
    }

    @Test
    public void excludedMethodHandleCallerDoesNotRegisterEnumValuesHelper() throws Exception {
        String enumClassName = "com.example.TestMode";
        ConfigurationSet configurationSet = new ConfigurationSet();
        TraceProcessor processor = newTraceProcessor();

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "findMethodHandle",
                            "class": "%s",
                            "declaring_class": "%s",
                            "class_is_enum": true,
                            "caller_class": "java.lang.invoke.MethodHandleImpl$1",
                            "result": true,
                            "args": ["values", []]
                          }
                        ]
                        """.formatted(enumClassName, enumClassName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        Assert.assertNull(getConfigurationType(reflectionConfiguration, enumClassName));
    }

    @Test
    public void includedMethodHandleCallerDoesNotRegisterEnumValuesHelper() throws Exception {
        String enumClassName = "com.example.TestMode";
        ConfigurationSet configurationSet = new ConfigurationSet();
        TraceProcessor processor = newTraceProcessor();

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "findMethodHandle",
                            "class": "%s",
                            "declaring_class": "%s",
                            "class_is_enum": true,
                            "caller_class": "com.example.Application",
                            "result": true,
                            "args": ["values", []]
                          }
                        ]
                        """.formatted(enumClassName, enumClassName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        Assert.assertNull(getConfigurationType(reflectionConfiguration, enumClassName));
    }

    @Test
    public void excludedMethodHandleCallerDoesNotRegisterProxyConstructor() throws Exception {
        String proxyInterfaceName = "com.example.ProxyInterface";
        ConfigurationSet configurationSet = new ConfigurationSet();
        TraceProcessor processor = newTraceProcessor();

        processTrace(processor, configurationSet, """
                        [
                          {
                            "tracer": "reflect",
                            "function": "findConstructorHandle",
                            "class": { "proxy": ["%s"] },
                            "caller_class": "java.lang.invoke.MethodHandleImpl$1",
                            "result": true,
                            "args": [["java.lang.reflect.InvocationHandler"]]
                          }
                        ]
                        """.formatted(proxyInterfaceName));

        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();
        Assert.assertNull(getProxyConfigurationType(reflectionConfiguration, proxyInterfaceName));
    }

    private static TraceProcessor newTraceProcessor() {
        return newTraceProcessor(null);
    }

    private static TraceProcessor newTraceProcessor(ConfigurationFilter callerFilter) {
        return new TraceProcessor(new AccessAdvisor(false, callerFilter, null, null));
    }

    private static void processTrace(TraceProcessor processor, ConfigurationSet configurationSet,
                    String trace) throws Exception {
        processor.process(new StringReader(trace), configurationSet);
    }

    private static ConfigurationType getConfigurationType(TypeConfiguration reflectionConfiguration, String className) {
        return reflectionConfiguration.get(UnresolvedAccessCondition.unconditional(),
                        NamedConfigurationTypeDescriptor.fromReflectionName(className));
    }

    private static ConfigurationType getProxyConfigurationType(TypeConfiguration reflectionConfiguration, String interfaceName) {
        return reflectionConfiguration.get(UnresolvedAccessCondition.unconditional(),
                        ProxyConfigurationTypeDescriptor.fromInterfaceTypeNames(List.of(interfaceName)));
    }

    private static ConfigurationMemberInfo getMethodInfo(ConfigurationType configurationType, String methodName) {
        ConfigurationMethod method = new ConfigurationMethod(methodName, "()V");
        return ConfigurationType.TestBackdoor.getMethodInfoIfPresent(configurationType, method);
    }
}
