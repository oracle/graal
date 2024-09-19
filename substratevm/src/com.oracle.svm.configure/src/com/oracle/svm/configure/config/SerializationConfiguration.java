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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.java.LambdaUtils;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.util.json.JsonPrintable;
import com.oracle.svm.core.util.json.JsonWriter;

public final class SerializationConfiguration extends ConfigurationBase<SerializationConfiguration, SerializationConfiguration.Predicate>
                implements RuntimeSerializationSupport {

    private final Set<SerializationConfigurationType> serializations = ConcurrentHashMap.newKeySet();
    private final Set<SerializationConfigurationLambdaCapturingType> lambdaSerializationCapturingTypes = ConcurrentHashMap.newKeySet();
    private final ProxyConfiguration proxyConfiguration;
    private final Set<ConditionalElement<List<String>>> interfaceListsSerializableProxies = ConcurrentHashMap.newKeySet();

    public SerializationConfiguration() {
        proxyConfiguration = new ProxyConfiguration();
    }

    public SerializationConfiguration(SerializationConfiguration other) {
        serializations.addAll(other.serializations);
        lambdaSerializationCapturingTypes.addAll(other.lambdaSerializationCapturingTypes);
        interfaceListsSerializableProxies.addAll(other.interfaceListsSerializableProxies);
        this.proxyConfiguration = new ProxyConfiguration(other.proxyConfiguration);
    }

    @Override
    public SerializationConfiguration copy() {
        return new SerializationConfiguration(this);
    }

    @Override
    protected void merge(SerializationConfiguration other) {
        serializations.addAll(other.serializations);
        lambdaSerializationCapturingTypes.addAll(other.lambdaSerializationCapturingTypes);
        proxyConfiguration.merge(other.proxyConfiguration);
    }

    @Override
    public void subtract(SerializationConfiguration other) {
        serializations.removeAll(other.serializations);
        lambdaSerializationCapturingTypes.removeAll(other.lambdaSerializationCapturingTypes);
        proxyConfiguration.subtract(other.proxyConfiguration);
    }

    @Override
    protected void intersect(SerializationConfiguration other) {
        serializations.retainAll(other.serializations);
        lambdaSerializationCapturingTypes.retainAll(other.lambdaSerializationCapturingTypes);
        proxyConfiguration.intersect(other.proxyConfiguration);
    }

    @Override
    protected void removeIf(Predicate predicate) {
        serializations.removeIf(predicate::testSerializationType);
        lambdaSerializationCapturingTypes.removeIf(predicate::testLambdaSerializationType);
    }

    @Override
    public void mergeConditional(ConfigurationCondition condition, SerializationConfiguration other) {
        for (SerializationConfigurationType type : other.serializations) {
            serializations.add(new SerializationConfigurationType(condition, type.getQualifiedJavaName(), type.getQualifiedCustomTargetConstructorJavaName()));
        }
    }

    public boolean contains(ConfigurationCondition condition, String serializationTargetClass, String customTargetConstructorClass) {
        return serializations.contains(createConfigurationType(condition, serializationTargetClass, customTargetConstructorClass)) ||
                        lambdaSerializationCapturingTypes.contains(createLambdaCapturingClassConfigurationType(condition, serializationTargetClass));
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        List<SerializationConfigurationType> listOfCapturedClasses = new ArrayList<>(serializations);
        Collections.sort(listOfCapturedClasses);
        printSerializationClasses(writer, "types", listOfCapturedClasses);
        writer.append(",").newline();
        List<SerializationConfigurationLambdaCapturingType> listOfCapturingClasses = new ArrayList<>(lambdaSerializationCapturingTypes);
        listOfCapturingClasses.sort(new SerializationConfigurationLambdaCapturingType.SerializationConfigurationLambdaCapturingTypesComparator());
        printSerializationClasses(writer, "lambdaCapturingTypes", listOfCapturingClasses);
        writer.append(",").newline().quote("proxies").append(":");
        printProxies(writer);
        writer.unindent().newline();
        writer.append('}');
    }

    @Override
    public ConfigurationParser createParser(boolean strictMetadata) {
        return SerializationConfigurationParser.create(strictMetadata, this, true);
    }

    private void printProxies(JsonWriter writer) throws IOException {
        List<ConditionalElement<List<String>>> lists = new ArrayList<>(interfaceListsSerializableProxies);
        ProxyConfiguration.printProxyInterfaces(writer, lists);
    }

    private static void printSerializationClasses(JsonWriter writer, String types, List<? extends JsonPrintable> serializationConfigurationTypes) throws IOException {
        writer.quote(types).append(":");
        writer.append('[');
        writer.indent();

        printSerializationTypes(serializationConfigurationTypes, writer);

        writer.unindent().newline();
        writer.append("]");
    }

    private static void printSerializationTypes(List<? extends JsonPrintable> serializationConfigurationTypes, JsonWriter writer) throws IOException {
        String prefix = "";

        for (JsonPrintable type : serializationConfigurationTypes) {
            writer.append(prefix).newline();
            type.printJson(writer);
            prefix = ",";
        }
    }

    @Override
    public void registerIncludingAssociatedClasses(ConfigurationCondition condition, Class<?> clazz) {
        register(condition, clazz);
    }

    @Override
    public void register(ConfigurationCondition condition, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerWithTargetConstructorClass(condition, clazz, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, Class<?> clazz, Class<?> customTargetConstructorClazz) {
        registerWithTargetConstructorClass(condition, clazz.getName(), customTargetConstructorClazz == null ? null : customTargetConstructorClazz.getName());
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, String className, String customTargetConstructorClassName) {
        serializations.add(createConfigurationType(condition, className, customTargetConstructorClassName));
    }

    @Override
    public void registerLambdaCapturingClass(ConfigurationCondition condition, String lambdaCapturingClassName) {
        lambdaSerializationCapturingTypes.add(createLambdaCapturingClassConfigurationType(condition, lambdaCapturingClassName.split(LambdaUtils.LAMBDA_SPLIT_PATTERN)[0]));
    }

    @Override
    public void registerProxyClass(ConfigurationCondition condition, List<String> implementedInterfaces) {
        interfaceListsSerializableProxies.add(new ConditionalElement<>(condition, implementedInterfaces));
    }

    @Override
    public boolean isEmpty() {
        return serializations.isEmpty() && lambdaSerializationCapturingTypes.isEmpty() || interfaceListsSerializableProxies.isEmpty();
    }

    private static SerializationConfigurationType createConfigurationType(ConfigurationCondition condition, String className, String customTargetConstructorClassName) {
        String convertedClassName = SignatureUtil.toInternalClassName(className);
        String convertedCustomTargetConstructorClassName = customTargetConstructorClassName == null ? null : SignatureUtil.toInternalClassName(customTargetConstructorClassName);
        return new SerializationConfigurationType(condition, convertedClassName, convertedCustomTargetConstructorClassName);
    }

    private static SerializationConfigurationLambdaCapturingType createLambdaCapturingClassConfigurationType(ConfigurationCondition condition, String className) {
        String convertedClassName = SignatureUtil.toInternalClassName(className);
        return new SerializationConfigurationLambdaCapturingType(condition, convertedClassName);
    }

    public interface Predicate {

        boolean testSerializationType(SerializationConfigurationType type);

        boolean testLambdaSerializationType(SerializationConfigurationLambdaCapturingType type);
    }
}
