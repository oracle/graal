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

class ReflectionMetadataParser<C, T> extends ReflectionConfigurationParser<C, T> {
    private static final List<String> OPTIONAL_REFLECT_METADATA_ATTRS = Arrays.asList(CONDITIONAL_KEY,
                    "allDeclaredConstructors", "allPublicConstructors", "allDeclaredMethods", "allPublicMethods", "allDeclaredFields", "allPublicFields",
                    "methods", "fields", "unsafeAllocated", "serializable", "jniAccessible");

    ReflectionMetadataParser(ConfigurationConditionResolver<C> conditionResolver, ReflectionConfigurationParserDelegate<C, T> delegate,
                    EnumSet<ConfigurationParserOption> parserOptions) {
        super(conditionResolver, delegate, parserOptions);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        String sectionName = checkOption(ConfigurationParserOption.JNI_PARSER) ? JNI_KEY : REFLECTION_KEY;
        Object reflectionJson = getFromGlobalFile(json, sectionName);
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

        boolean jniParser = checkOption(ConfigurationParserOption.JNI_PARSER);
        boolean typeJniAccessible = jniParser || data.get("jniAccessible") == Boolean.TRUE;
        /*
         * Even if primitives cannot be queried through Class.forName, they can be registered to
         * allow getDeclaredMethods() and similar bulk queries at run time.
         */
        TypeResult<List<T>> result = delegate.resolveTypes(condition, type.get(), true, typeJniAccessible);
        if (!result.isPresent()) {
            handleMissingElement("Could not resolve " + type.get() + " for reflection configuration.", result.getException());
            return;
        }

        C queryCondition = conditionResolver.alwaysTrue();
        List<T> classes = result.get();
        for (T clazz : classes) {
            delegate.registerType(conditionResult.get(), clazz);

            delegate.registerDeclaredClasses(queryCondition, clazz);
            delegate.registerPublicClasses(queryCondition, clazz);
            if (!jniParser) {
                delegate.registerRecordComponents(queryCondition, clazz);
                delegate.registerPermittedSubclasses(queryCondition, clazz);
                delegate.registerNestMembers(queryCondition, clazz);
                delegate.registerSigners(queryCondition, clazz);
            }
            delegate.registerDeclaredConstructors(queryCondition, true, jniParser, clazz);
            delegate.registerPublicConstructors(queryCondition, true, jniParser, clazz);
            delegate.registerDeclaredMethods(queryCondition, true, jniParser, clazz);
            delegate.registerPublicMethods(queryCondition, true, jniParser, clazz);
            delegate.registerDeclaredFields(queryCondition, true, jniParser, clazz);
            delegate.registerPublicFields(queryCondition, true, jniParser, clazz);

            if (!jniParser) {
                registerIfNotDefault(data, false, clazz, "serializable", () -> delegate.registerAsSerializable(condition, clazz));
                registerIfNotDefault(data, false, clazz, "jniAccessible", () -> delegate.registerAsJniAccessed(condition, clazz));
            }

            registerIfNotDefault(data, false, clazz, "allDeclaredConstructors", () -> delegate.registerDeclaredConstructors(condition, false, typeJniAccessible, clazz));
            registerIfNotDefault(data, false, clazz, "allPublicConstructors", () -> delegate.registerPublicConstructors(condition, false, typeJniAccessible, clazz));
            registerIfNotDefault(data, false, clazz, "allDeclaredMethods", () -> delegate.registerDeclaredMethods(condition, false, typeJniAccessible, clazz));
            registerIfNotDefault(data, false, clazz, "allPublicMethods", () -> delegate.registerPublicMethods(condition, false, typeJniAccessible, clazz));
            registerIfNotDefault(data, false, clazz, "allDeclaredFields", () -> delegate.registerDeclaredFields(condition, false, typeJniAccessible, clazz));
            registerIfNotDefault(data, false, clazz, "allPublicFields", () -> delegate.registerPublicFields(condition, false, typeJniAccessible, clazz));
            registerIfNotDefault(data, false, clazz, "unsafeAllocated", () -> delegate.registerUnsafeAllocated(condition, clazz));

            MapCursor<String, Object> cursor = data.getEntries();
            while (cursor.advance()) {
                String name = cursor.getKey();
                Object value = cursor.getValue();
                try {
                    switch (name) {
                        case "methods":
                            parseMethods(condition, false, asList(value, "Attribute 'methods' must be an array of method descriptors"), clazz, typeJniAccessible);
                            break;
                        case "fields":
                            parseFields(condition, asList(value, "Attribute 'fields' must be an array of field descriptors"), clazz, typeJniAccessible);
                            break;
                    }
                } catch (LinkageError e) {
                    handleMissingElement("Could not register " + delegate.getTypeName(clazz) + ": " + name + " for reflection.", e);
                }
            }
        }
    }
}
