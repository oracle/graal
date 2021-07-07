/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonPrinter;
import com.oracle.svm.configure.json.JsonWriter;

public class ConfigurationType implements JsonPrintable {
    private final String qualifiedJavaName;

    private Map<String, FieldInfo> fields;
    private Map<ConfigurationMethod, ConfigurationMemberKind> methods;

    private boolean allDeclaredClasses;
    private boolean allPublicClasses;
    private boolean allDeclaredFields;
    private boolean allPublicFields;
    private boolean allDeclaredMethods;
    private boolean allPublicMethods;
    private boolean allDeclaredConstructors;
    private boolean allPublicConstructors;

    public ConfigurationType(String qualifiedJavaName) {
        assert qualifiedJavaName.indexOf('/') == -1 : "Requires qualified Java name, not internal representation";
        assert !qualifiedJavaName.startsWith("[") : "Requires Java source array syntax, for example java.lang.String[]";
        this.qualifiedJavaName = qualifiedJavaName;
    }

    public ConfigurationType(ConfigurationType other) {
        qualifiedJavaName = other.qualifiedJavaName;
        mergeWith(other);
    }

    public void mergeWith(ConfigurationType other) {
        assert qualifiedJavaName.equals(other.qualifiedJavaName);
        mergeFlagsWith(other);
        mergeFieldsWith(other);
        mergeMethodsWith(other);
    }

    private void mergeFlagsWith(ConfigurationType other) {
        setFlagsFromOtherUsingPredicate(other, (our, their) -> our || their);
    }

    private void mergeFieldsWith(ConfigurationType other) {
        if (other.fields != null) {
            if (fields == null) {
                fields = new HashMap<>();
            }
            for (Map.Entry<String, FieldInfo> fieldInfoEntry : other.fields.entrySet()) {
                fields.compute(fieldInfoEntry.getKey(), (key, value) -> {
                    if (value == null) {
                        return fieldInfoEntry.getValue();
                    } else {
                        return value.newMergedWith(fieldInfoEntry.getValue());
                    }
                });
            }
        }
        maybeRemoveFields(allDeclaredFields, allPublicFields);
    }

    private void maybeRemoveFields(boolean hasAllDeclaredFields, boolean hasAllPublicFields) {
        if (hasAllDeclaredFields) {
            removeFields(ConfigurationMemberKind.DECLARED);
        }
        if (hasAllPublicFields) {
            removeFields(ConfigurationMemberKind.PUBLIC);
        }
    }

    private void mergeMethodsWith(ConfigurationType other) {
        if (other.methods != null) {
            if (methods == null) {
                methods = new HashMap<>();
            }
            for (Map.Entry<ConfigurationMethod, ConfigurationMemberKind> methodEntry : other.methods.entrySet()) {
                methods.compute(methodEntry.getKey(), (key, value) -> {
                    if (value != null) {
                        return value.equals(ConfigurationMemberKind.PRESENT) ? methodEntry.getValue() : value;
                    } else {
                        return methodEntry.getValue();
                    }
                });
            }
        }
        maybeRemoveMethods(allDeclaredMethods, allPublicMethods, allDeclaredConstructors, allPublicConstructors);
    }

    private void maybeRemoveMethods(boolean hasAllDeclaredMethods, boolean hasAllPublicMethods, boolean hasAllDeclaredConstructors, boolean hasAllPublicConstructors) {
        if (hasAllDeclaredMethods) {
            removeMethods(ConfigurationMemberKind.DECLARED, false);
        }
        if (hasAllDeclaredConstructors) {
            removeMethods(ConfigurationMemberKind.DECLARED, true);
        }

        if (hasAllPublicMethods) {
            removeMethods(ConfigurationMemberKind.PUBLIC, false);
        }
        if (hasAllPublicConstructors) {
            removeMethods(ConfigurationMemberKind.PUBLIC, true);
        }
    }

    public void removeAll(ConfigurationType other) {
        removeFlags(other);
        removeFields(other);
        removeMethods(other);
    }

    private void removeFlags(ConfigurationType other) {
        setFlagsFromOtherUsingPredicate(other, (our, their) -> our && !their);
    }

    private void removeFields(ConfigurationType other) {
        maybeRemoveFields(allDeclaredFields || other.allDeclaredFields, allPublicFields || other.allPublicFields);
        if (fields != null && other.fields != null) {
            for (Map.Entry<String, FieldInfo> fieldInfoEntry : other.fields.entrySet()) {
                fields.computeIfPresent(fieldInfoEntry.getKey(), (key, value) -> value.newWithDifferencesFrom(fieldInfoEntry.getValue()));
            }
            if (fields.isEmpty()) {
                fields = null;
            }
        }
    }

    private void removeMethods(ConfigurationType other) {
        maybeRemoveMethods(allDeclaredMethods || other.allDeclaredMethods, allPublicMethods || other.allPublicMethods,
                        allDeclaredConstructors || other.allDeclaredConstructors, allPublicConstructors || other.allPublicConstructors);
        if (methods != null && other.methods != null) {
            methods.entrySet().removeAll(other.methods.entrySet());
            if (methods.isEmpty()) {
                methods = null;
            }
        }
    }

    private void setFlagsFromOtherUsingPredicate(ConfigurationType other, BiPredicate<Boolean, Boolean> predicate) {
        allDeclaredClasses = predicate.test(allDeclaredClasses, other.allDeclaredClasses);
        allPublicClasses = predicate.test(allPublicClasses, other.allPublicClasses);
        allDeclaredFields = predicate.test(allDeclaredFields, other.allDeclaredFields);
        allPublicFields = predicate.test(allPublicFields, other.allPublicFields);
        allDeclaredMethods = predicate.test(allDeclaredMethods, other.allDeclaredMethods);
        allPublicMethods = predicate.test(allPublicMethods, other.allPublicMethods);
        allDeclaredConstructors = predicate.test(allDeclaredConstructors, other.allDeclaredConstructors);
        allPublicConstructors = predicate.test(allPublicConstructors, other.allPublicConstructors);
    }

    public boolean isEmpty() {
        return methods == null && fields == null && allFlagsFalse();
    }

    private boolean allFlagsFalse() {
        return !(allDeclaredClasses || allPublicClasses || allDeclaredFields || allPublicFields || allDeclaredMethods || allPublicMethods || allDeclaredConstructors || allPublicConstructors);
    }

    public String getQualifiedJavaName() {
        return qualifiedJavaName;
    }

    public void addField(String name, ConfigurationMemberKind memberKind, boolean finalButWritable) {
        if (!finalButWritable) {
            if ((memberKind.includes(ConfigurationMemberKind.DECLARED) && haveAllDeclaredFields()) || (memberKind.includes(ConfigurationMemberKind.PUBLIC) && haveAllPublicFields())) {
                fields = maybeRemove(fields, map -> {
                    FieldInfo fieldInfo = map.get(name);
                    if (fieldInfo != null && !fieldInfo.isFinalButWritable()) {
                        map.remove(name);
                    }
                });
                return;
            }
        }
        if (fields == null) {
            fields = new HashMap<>();
        }
        fields.compute(name, (k, v) -> (v != null)
                        ? FieldInfo.get(v.getKind().intersect(memberKind), v.isFinalButWritable() || finalButWritable)
                        : FieldInfo.get(memberKind, finalButWritable));
    }

    public void addMethodsWithName(String name, ConfigurationMemberKind memberKind) {
        addMethod(name, null, memberKind);
    }

    public void addMethod(String name, String internalSignature, ConfigurationMemberKind memberKind) {
        boolean matchesAllSignatures = (internalSignature == null);
        if (ConfigurationMethod.isConstructorName(name) ? (haveAllDeclaredConstructors() || (memberKind.includes(ConfigurationMemberKind.PUBLIC) && haveAllPublicConstructors()))
                        : ((memberKind.includes(ConfigurationMemberKind.DECLARED) && haveAllDeclaredMethods()) || (memberKind.includes(ConfigurationMemberKind.PUBLIC) && haveAllPublicMethods()))) {
            if (!matchesAllSignatures) {
                methods = maybeRemove(methods, map -> map.remove(new ConfigurationMethod(name, internalSignature)));
            }
            return;
        }
        if (methods == null) {
            methods = new HashMap<>();
        }
        ConfigurationMethod method = new ConfigurationMethod(name, internalSignature);
        if (matchesAllSignatures) { // remove any methods that the new entry matches
            methods.compute(method, (k, v) -> v != null ? memberKind.union(v) : memberKind);
            methods = maybeRemove(methods, map -> map.entrySet().removeIf(entry -> name.equals(entry.getKey().getName()) &&
                            memberKind.includes(entry.getValue()) && !method.equals(entry.getKey())));
        } else {
            methods.compute(method, (k, v) -> v != null ? memberKind.intersect(v) : memberKind);
        }
        assert methods.containsKey(method);
    }

    public ConfigurationMemberKind getMethodKindIfPresent(ConfigurationMethod method) {
        return methods == null ? null : methods.get(method);
    }

    public FieldInfo getFieldInfoIfPresent(String field) {
        return fields == null ? null : fields.get(field);
    }

    public boolean haveAllDeclaredClasses() {
        return allDeclaredClasses;
    }

    public boolean haveAllPublicClasses() {
        return allPublicClasses;
    }

    public void setAllDeclaredClasses() {
        this.allDeclaredClasses = true;
    }

    public void setAllPublicClasses() {
        this.allPublicClasses = true;
    }

    public boolean haveAllDeclaredFields() {
        return allDeclaredFields;
    }

    public boolean haveAllPublicFields() {
        return allPublicFields;
    }

    public void setAllDeclaredFields() {
        this.allDeclaredFields = true;
        removeFields(ConfigurationMemberKind.DECLARED);
    }

    public void setAllPublicFields() {
        this.allPublicFields = true;
        removeFields(ConfigurationMemberKind.PUBLIC);
    }

    public boolean haveAllDeclaredMethods() {
        return allDeclaredMethods;
    }

    public boolean haveAllPublicMethods() {
        return allPublicMethods;
    }

    public void setAllDeclaredMethods() {
        this.allDeclaredMethods = true;
        removeMethods(ConfigurationMemberKind.DECLARED, false);
    }

    public void setAllPublicMethods() {
        this.allPublicMethods = true;
        removeMethods(ConfigurationMemberKind.PUBLIC, false);
    }

    public boolean haveAllDeclaredConstructors() {
        return allDeclaredConstructors;
    }

    public boolean haveAllPublicConstructors() {
        return allPublicConstructors;
    }

    public void setAllDeclaredConstructors() {
        this.allDeclaredConstructors = true;
        removeMethods(ConfigurationMemberKind.DECLARED, true);
    }

    public void setAllPublicConstructors() {
        this.allPublicConstructors = true;
        removeMethods(ConfigurationMemberKind.PUBLIC, true);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        writer.quote("name").append(':').quote(qualifiedJavaName);
        optionallyPrintJsonBoolean(writer, haveAllDeclaredFields(), "allDeclaredFields");
        optionallyPrintJsonBoolean(writer, haveAllPublicFields(), "allPublicFields");
        optionallyPrintJsonBoolean(writer, haveAllDeclaredMethods(), "allDeclaredMethods");
        optionallyPrintJsonBoolean(writer, haveAllPublicMethods(), "allPublicMethods");
        optionallyPrintJsonBoolean(writer, haveAllDeclaredConstructors(), "allDeclaredConstructors");
        optionallyPrintJsonBoolean(writer, haveAllPublicConstructors(), "allPublicConstructors");
        optionallyPrintJsonBoolean(writer, haveAllDeclaredClasses(), "allDeclaredClasses");
        optionallyPrintJsonBoolean(writer, haveAllPublicClasses(), "allPublicClasses");
        if (fields != null) {
            writer.append(',').newline().quote("fields").append(':');
            JsonPrinter.printCollection(writer, fields.entrySet(), Map.Entry.comparingByKey(), ConfigurationType::printField);
        }
        if (methods != null) {
            writer.append(',').newline().quote("methods").append(':');
            JsonPrinter.printCollection(writer,
                            methods.keySet(),
                            Comparator.comparing(ConfigurationMethod::getName).thenComparing(Comparator.nullsFirst(Comparator.comparing(ConfigurationMethod::getInternalSignature))),
                            JsonPrintable::printJson);
        }
        writer.unindent().newline();
        writer.append('}');
    }

    private static void printField(Map.Entry<String, FieldInfo> entry, JsonWriter w) throws IOException {
        w.append('{').quote("name").append(':').quote(entry.getKey());
        if (entry.getValue().isFinalButWritable()) {
            w.append(", ").quote("allowWrite").append(':').append("true");
        }
        w.append('}');
    }

    private static void optionallyPrintJsonBoolean(JsonWriter writer, boolean predicate, String attribute) throws IOException {
        if (predicate) {
            writer.append(',').newline().quote(attribute).append(":true");
        }
    }

    private void removeFields(ConfigurationMemberKind memberKind) {
        fields = maybeRemove(fields, map -> map.values().removeIf(v -> memberKind.includes(v.getKind())));
    }

    private void removeMethods(ConfigurationMemberKind memberKind, boolean constructors) {
        methods = maybeRemove(methods, map -> map.entrySet().removeIf(entry -> entry.getKey().isConstructor() == constructors && memberKind.includes(entry.getValue())));
    }

    private static <T, S> Map<T, S> maybeRemove(Map<T, S> fromMap, Consumer<Map<T, S>> action) {
        Map<T, S> map = fromMap;
        if (map != null) {
            action.accept(map);
            if (map.isEmpty()) {
                map = null;
            }
        }
        return map;
    }
}
