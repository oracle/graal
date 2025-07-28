/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.TypeResult;

final class LegacyReflectionConfigurationParser<C, T> extends ReflectionConfigurationParser<C, T> {

    private static final List<String> OPTIONAL_REFLECT_CONFIG_OBJECT_ATTRS = Arrays.asList("allDeclaredConstructors", "allPublicConstructors",
                    "allDeclaredMethods", "allPublicMethods", "allDeclaredFields", "allPublicFields",
                    "allDeclaredClasses", "allRecordComponents", "allPermittedSubclasses", "allNestMembers", "allSigners",
                    "allPublicClasses", "methods", "queriedMethods", "fields", CONDITIONAL_KEY,
                    "queryAllDeclaredConstructors", "queryAllPublicConstructors", "queryAllDeclaredMethods", "queryAllPublicMethods", "unsafeAllocated", "serializable");

    LegacyReflectionConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, ReflectionConfigurationParserDelegate<C, T> delegate, EnumSet<ConfigurationParserOption> parserOptions) {
        super(conditionResolver, delegate, parserOptions);
    }

    @Override
    protected EnumSet<ConfigurationParserOption> supportedOptions() {
        EnumSet<ConfigurationParserOption> base = super.supportedOptions();
        base.add(ConfigurationParserOption.TREAT_ALL_NAME_ENTRIES_AS_TYPE);
        return base;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseClassArray(asList(json, "first level of document must be an array of class descriptors"));
    }

    @Override
    protected void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "reflection class descriptor object", List.of(NAME_KEY), OPTIONAL_REFLECT_CONFIG_OBJECT_ATTRS);

        Optional<TypeDescriptorWithOrigin> type = parseName(data, checkOption(ConfigurationParserOption.TREAT_ALL_NAME_ENTRIES_AS_TYPE));
        if (type.isEmpty()) {
            return;
        }
        ConfigurationTypeDescriptor typeDescriptor = type.get().typeDescriptor();
        /*
         * Classes registered using the old ("name") syntax requires elements (fields, methods,
         * constructors, ...) to be registered for runtime queries, whereas the new ("type") syntax
         * automatically registers all elements as queried.
         */
        boolean isType = type.get().definedAsType();

        UnresolvedConfigurationCondition unresolvedCondition = parseCondition(data, false);
        TypeResult<C> conditionResult = conditionResolver.resolveCondition(unresolvedCondition);
        if (!conditionResult.isPresent()) {
            return;
        }

        boolean jniParser = checkOption(ConfigurationParserOption.JNI_PARSER);
        /*
         * Even if primitives cannot be queried through Class.forName, they can be registered to
         * allow getDeclaredMethods() and similar bulk queries at run time.
         */
        C condition = conditionResult.get();
        TypeResult<T> result = delegate.resolveType(condition, typeDescriptor, true, jniParser);
        if (!result.isPresent()) {
            handleMissingElement("Could not resolve " + typeDescriptor + " for reflection configuration.", result.getException());
            return;
        }

        C queryCondition = isType ? conditionResolver.alwaysTrue() : condition;
        T clazz = result.get();
        delegate.registerType(conditionResult.get(), clazz);

        registerIfNotDefault(data, false, clazz, "allDeclaredConstructors", () -> delegate.registerDeclaredConstructors(condition, false, jniParser, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicConstructors", () -> delegate.registerPublicConstructors(condition, false, jniParser, clazz));
        registerIfNotDefault(data, false, clazz, "allDeclaredMethods", () -> delegate.registerDeclaredMethods(condition, false, jniParser, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicMethods", () -> delegate.registerPublicMethods(condition, false, jniParser, clazz));
        registerIfNotDefault(data, false, clazz, "allDeclaredFields", () -> delegate.registerDeclaredFields(condition, false, jniParser, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicFields", () -> delegate.registerPublicFields(condition, false, jniParser, clazz));
        registerIfNotDefault(data, !jniParser && isType, clazz, "allDeclaredClasses", () -> delegate.registerDeclaredClasses(queryCondition, clazz));
        registerIfNotDefault(data, !jniParser && isType, clazz, "allRecordComponents", () -> delegate.registerRecordComponents(queryCondition, clazz));
        registerIfNotDefault(data, !jniParser && isType, clazz, "allPermittedSubclasses", () -> delegate.registerPermittedSubclasses(queryCondition, clazz));
        registerIfNotDefault(data, !jniParser && isType, clazz, "allNestMembers", () -> delegate.registerNestMembers(queryCondition, clazz));
        registerIfNotDefault(data, !jniParser && isType, clazz, "allSigners", () -> delegate.registerSigners(queryCondition, clazz));
        registerIfNotDefault(data, !jniParser && isType, clazz, "allPublicClasses", () -> delegate.registerPublicClasses(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllDeclaredConstructors", () -> delegate.registerDeclaredConstructors(queryCondition, true, jniParser, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllPublicConstructors", () -> delegate.registerPublicConstructors(queryCondition, true, jniParser, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllDeclaredMethods", () -> delegate.registerDeclaredMethods(queryCondition, true, jniParser, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllPublicMethods", () -> delegate.registerPublicMethods(queryCondition, true, jniParser, clazz));
        if (isType) {
            /*
             * Fields cannot be registered as queried only by the user, we register them
             * unconditionally if the class is registered via "type".
             */
            delegate.registerDeclaredFields(queryCondition, true, jniParser, clazz);
            delegate.registerPublicFields(queryCondition, true, jniParser, clazz);
        }
        registerIfNotDefault(data, false, clazz, "unsafeAllocated", () -> delegate.registerUnsafeAllocated(condition, clazz));
        MapCursor<String, Object> cursor = data.getEntries();
        while (cursor.advance()) {
            String name = cursor.getKey();
            Object value = cursor.getValue();
            try {
                switch (name) {
                    case "methods":
                        parseMethods(condition, false, asList(value, "Attribute 'methods' must be an array of method descriptors"), clazz, jniParser);
                        break;
                    case "queriedMethods":
                        parseMethods(condition, true, asList(value, "Attribute 'queriedMethods' must be an array of method descriptors"), clazz, jniParser);
                        break;
                    case "fields":
                        parseFields(condition, asList(value, "Attribute 'fields' must be an array of field descriptors"), clazz, jniParser);
                        break;
                }
            } catch (LinkageError e) {
                handleMissingElement("Could not register " + delegate.getTypeName(clazz) + ": " + name + " for reflection.", e);
            }
        }
    }
}
