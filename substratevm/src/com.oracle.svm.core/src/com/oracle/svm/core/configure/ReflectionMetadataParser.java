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

class ReflectionMetadataParser<C, T> extends ReflectionConfigurationParser<C, T> {
    private static final List<String> OPTIONAL_REFLECT_METADATA_ATTRS = Arrays.asList(CONDITIONAL_KEY,
                    "allDeclaredConstructors", "allPublicConstructors", "allDeclaredMethods", "allPublicMethods", "allDeclaredFields", "allPublicFields",
                    "methods", "fields", "unsafeAllocated");

    private final String combinedFileKey;

    ReflectionMetadataParser(String combinedFileKey, ConfigurationConditionResolver<C> conditionResolver, ReflectionConfigurationParserDelegate<C, T> delegate, boolean strictConfiguration,
                    boolean printMissingElements) {
        super(conditionResolver, delegate, strictConfiguration, printMissingElements);
        this.combinedFileKey = combinedFileKey;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        Object reflectionJson = getFromGlobalFile(json, combinedFileKey);
        if (reflectionJson != null) {
            parseClassArray(asList(reflectionJson, "first level of document must be an array of class descriptors"));
        }
    }

    @Override
    protected void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "reflection class descriptor object", List.of(TYPE_KEY), OPTIONAL_REFLECT_METADATA_ATTRS);

        Optional<ConfigurationTypeDescriptor> type = parseTypeContents(data.get(TYPE_KEY));
        if (type.isEmpty()) {
            return;
        }

        UnresolvedConfigurationCondition unresolvedCondition = parseCondition(data, true);
        TypeResult<C> conditionResult = conditionResolver.resolveCondition(unresolvedCondition);
        if (!conditionResult.isPresent()) {
            return;
        }
        C condition = conditionResult.get();

        /*
         * Even if primitives cannot be queried through Class.forName, they can be registered to
         * allow getDeclaredMethods() and similar bulk queries at run time.
         */
        TypeResult<T> result = delegate.resolveType(condition, type.get(), true);
        if (!result.isPresent()) {
            handleMissingElement("Could not resolve " + type.get() + " for reflection configuration.", result.getException());
            return;
        }

        C queryCondition = conditionResolver.alwaysTrue();
        T clazz = result.get();
        delegate.registerType(conditionResult.get(), clazz);

        delegate.registerDeclaredClasses(queryCondition, clazz);
        delegate.registerRecordComponents(queryCondition, clazz);
        delegate.registerPermittedSubclasses(queryCondition, clazz);
        delegate.registerNestMembers(queryCondition, clazz);
        delegate.registerSigners(queryCondition, clazz);
        delegate.registerPublicClasses(queryCondition, clazz);
        delegate.registerDeclaredConstructors(queryCondition, true, clazz);
        delegate.registerPublicConstructors(queryCondition, true, clazz);
        delegate.registerDeclaredMethods(queryCondition, true, clazz);
        delegate.registerPublicMethods(queryCondition, true, clazz);
        delegate.registerDeclaredFields(queryCondition, true, clazz);
        delegate.registerPublicFields(queryCondition, true, clazz);

        registerIfNotDefault(data, false, clazz, "allDeclaredConstructors", () -> delegate.registerDeclaredConstructors(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicConstructors", () -> delegate.registerPublicConstructors(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allDeclaredMethods", () -> delegate.registerDeclaredMethods(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicMethods", () -> delegate.registerPublicMethods(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allDeclaredFields", () -> delegate.registerDeclaredFields(condition, false, clazz));
        registerIfNotDefault(data, false, clazz, "allPublicFields", () -> delegate.registerPublicFields(condition, false, clazz));
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
