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
package com.oracle.svm.core.configure;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import com.oracle.svm.core.TypeResult;

final class LegacyReflectionConfigurationParser<C, T> extends ReflectionConfigurationParser<C, T> {

    private static final List<String> OPTIONAL_REFLECT_CONFIG_OBJECT_ATTRS = Arrays.asList("allDeclaredConstructors", "allPublicConstructors",
                    "allDeclaredMethods", "allPublicMethods", "allDeclaredFields", "allPublicFields",
                    "allDeclaredClasses", "allRecordComponents", "allPermittedSubclasses", "allNestMembers", "allSigners",
                    "allPublicClasses", "methods", "queriedMethods", "fields", CONDITIONAL_KEY,
                    "queryAllDeclaredConstructors", "queryAllPublicConstructors", "queryAllDeclaredMethods", "queryAllPublicMethods", "unsafeAllocated");

    private final boolean treatAllNameEntriesAsType;

    LegacyReflectionConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, ReflectionConfigurationParserDelegate<C, T> delegate, boolean strictConfiguration,
                    boolean printMissingElements, boolean treatAllNameEntriesAsType) {
        super(conditionResolver, delegate, strictConfiguration, printMissingElements);
        this.treatAllNameEntriesAsType = treatAllNameEntriesAsType;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseClassArray(asList(json, "first level of document must be an array of class descriptors"));
    }

    @Override
    protected void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "reflection class descriptor object", List.of(NAME_KEY), OPTIONAL_REFLECT_CONFIG_OBJECT_ATTRS);

        Optional<TypeDescriptorWithOrigin> type = parseName(data, treatAllNameEntriesAsType);
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

        UnresolvedConfigurationCondition unresolvedCondition = parseCondition(data, isType);
        TypeResult<C> conditionResult = conditionResolver.resolveCondition(unresolvedCondition);
        if (!conditionResult.isPresent()) {
            return;
        }

        /*
         * Even if primitives cannot be queried through Class.forName, they can be registered to
         * allow getDeclaredMethods() and similar bulk queries at run time.
         */
        C condition = conditionResult.get();
        TypeResult<T> result = delegate.resolveType(condition, typeDescriptor, true);
        if (!result.isPresent()) {
            handleMissingElement("Could not resolve " + typeDescriptor + " for reflection configuration.", result.getException());
            return;
        }

        C queryCondition = isType ? conditionResolver.alwaysTrue() : condition;
        T clazz = result.get();
        delegate.registerType(conditionResult.get(), clazz);

        registerIfNotDefault(data, false, clazz, "allDeclaredConstructors", () -> delegate.registerDeclaredConstructors(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicConstructors", () -> delegate.registerPublicConstructors(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allDeclaredMethods", () -> delegate.registerDeclaredMethods(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicMethods", () -> delegate.registerPublicMethods(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allDeclaredFields", () -> delegate.registerDeclaredFields(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicFields", () -> delegate.registerPublicFields(condition, false, clazz));
        registerIfNotDefault(data, isType, clazz, "allDeclaredClasses", () -> delegate.registerDeclaredClasses(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "allRecordComponents", () -> delegate.registerRecordComponents(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "allPermittedSubclasses", () -> delegate.registerPermittedSubclasses(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "allNestMembers", () -> delegate.registerNestMembers(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "allSigners", () -> delegate.registerSigners(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "allPublicClasses", () -> delegate.registerPublicClasses(queryCondition, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllDeclaredConstructors", () -> delegate.registerDeclaredConstructors(queryCondition, true, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllPublicConstructors", () -> delegate.registerPublicConstructors(queryCondition, true, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllDeclaredMethods", () -> delegate.registerDeclaredMethods(queryCondition, true, clazz));
        registerIfNotDefault(data, isType, clazz, "queryAllPublicMethods", () -> delegate.registerPublicMethods(queryCondition, true, clazz));
        if (isType) {
            /*
             * Fields cannot be registered as queried only by the user, we register them
             * unconditionally if the class is registered via "type".
             */
            delegate.registerDeclaredFields(queryCondition, true, clazz);
            delegate.registerPublicFields(queryCondition, true, clazz);
        }
        registerIfNotDefault(data, false, clazz, "unsafeAllocated", () -> delegate.registerUnsafeAllocated(condition, clazz));
        MapCursor<String, Object> cursor = data.getEntries();
        while (cursor.advance()) {
            String name = cursor.getKey();
            Object value = cursor.getValue();
            try {
                switch (name) {
                    case "methods":
                        parseMethods(condition, false, asList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
                        break;
                    case "queriedMethods":
                        parseMethods(condition, true, asList(value, "Attribute 'queriedMethods' must be an array of method descriptors"), clazz);
                        break;
                    case "fields":
                        parseFields(condition, asList(value, "Attribute 'fields' must be an array of field descriptors"), clazz);
                        break;
                }
            } catch (LinkageError e) {
                handleMissingElement("Could not register " + delegate.getTypeName(clazz) + ": " + name + " for reflection.", e);
            }
        }
    }
}
