/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;

import jdk.graal.compiler.util.SignatureUtil;
import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonPrinter;
import jdk.graal.compiler.util.json.JsonWriter;

/**
 * Type usage information, part of a {@link TypeConfiguration}. Unlike other configuration classes
 * like {@link ConfigurationMethod}, this class is not immutable and uses locking to synchronize
 * changes during trace processing. Merely using {@link ConcurrentHashMap} is not sufficient because
 * of relationships between fields (e.g. {@link #allDeclaredMethodsAccess} and {@link #methods}) and
 * so related fields must be read and updated together.
 */
public class ConfigurationType implements JsonPrintable {
    static ConfigurationType copyAndSubtract(ConfigurationType type, ConfigurationType subtractType) {
        if (type == subtractType) {
            return null;
        }
        ConfigurationType copy = new ConfigurationType(type);
        if (subtractType == null) {
            return copy;
        }

        assert type.sameTypeAndCondition(subtractType);

        copy.removeAll(subtractType);
        return copy.isEmpty() ? null : copy;
    }

    static ConfigurationType copyAndIntersect(ConfigurationType type, ConfigurationType toIntersect) {
        ConfigurationType copy = new ConfigurationType(type);
        assert type.sameTypeAndCondition(toIntersect);
        copy.intersectWith(toIntersect);
        return copy;
    }

    static ConfigurationType copyAndMerge(ConfigurationType type, ConfigurationType toMerge) {
        ConfigurationType copy = new ConfigurationType(type);
        copy.mergeFrom(toMerge);
        return copy;
    }

    private final UnresolvedConfigurationCondition condition;
    private final ConfigurationTypeDescriptor typeDescriptor;

    private Map<String, FieldInfo> fields;
    private Map<ConfigurationMethod, ConfigurationMemberInfo> methods;

    private boolean allDeclaredClasses;
    private boolean allRecordComponents;
    private boolean allPermittedSubclasses;
    private boolean allNestMembers;
    private boolean allSigners;
    private boolean allPublicClasses;
    private ConfigurationMemberAccessibility allDeclaredFieldsAccess = ConfigurationMemberAccessibility.NONE;
    private ConfigurationMemberAccessibility allPublicFieldsAccess = ConfigurationMemberAccessibility.NONE;
    private boolean unsafeAllocated;
    private ConfigurationMemberAccessibility allDeclaredMethodsAccess = ConfigurationMemberAccessibility.NONE;
    private ConfigurationMemberAccessibility allPublicMethodsAccess = ConfigurationMemberAccessibility.NONE;
    private ConfigurationMemberAccessibility allDeclaredConstructorsAccess = ConfigurationMemberAccessibility.NONE;
    private ConfigurationMemberAccessibility allPublicConstructorsAccess = ConfigurationMemberAccessibility.NONE;
    private boolean serializable = false;
    private boolean typeJniAccessible = false;

    public ConfigurationType(UnresolvedConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean includeAllElements) {
        this.condition = Objects.requireNonNull(condition);
        this.typeDescriptor = Objects.requireNonNull(typeDescriptor);
        allDeclaredClasses = allPublicClasses = allRecordComponents = allPermittedSubclasses = allNestMembers = allSigners = includeAllElements;
        allDeclaredFieldsAccess = allPublicFieldsAccess = allDeclaredMethodsAccess = allPublicMethodsAccess = allDeclaredConstructorsAccess = allPublicConstructorsAccess = includeAllElements
                        ? ConfigurationMemberAccessibility.QUERIED
                        : ConfigurationMemberAccessibility.NONE;
    }

    ConfigurationType(ConfigurationType other, UnresolvedConfigurationCondition condition) {
        // Our object is not yet published, so it is sufficient to take only the other object's lock
        synchronized (other) {
            typeDescriptor = other.typeDescriptor;
            this.condition = condition;
            mergeFrom(other);
        }
    }

    ConfigurationType(ConfigurationType other) {
        this(other, other.condition);
    }

    void mergeFrom(ConfigurationType other) {
        assert sameTypeAndCondition(other);
        mergeFlagsFrom(other);
        mergeFieldsFrom(other);
        mergeMethodsFrom(other);
    }

    private boolean sameTypeAndCondition(ConfigurationType otherType) {
        return condition.equals(otherType.condition) && typeDescriptor.equals(otherType.typeDescriptor);
    }

    private void mergeFlagsFrom(ConfigurationType other) {
        setFlagsFromOther(other, (our, their) -> our || their, ConfigurationMemberAccessibility::combine);
    }

    private void mergeFieldsFrom(ConfigurationType other) {
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
        maybeRemoveFields(allDeclaredFieldsAccess, allPublicFieldsAccess);
    }

    private void maybeRemoveFields(ConfigurationMemberAccessibility hasAllDeclaredFields, ConfigurationMemberAccessibility hasAllPublicFields) {
        if (hasAllDeclaredFields != ConfigurationMemberAccessibility.NONE) {
            removeFields(ConfigurationMemberDeclaration.DECLARED, hasAllDeclaredFields);
        }
        if (hasAllPublicFields != ConfigurationMemberAccessibility.NONE) {
            removeFields(ConfigurationMemberDeclaration.PUBLIC, hasAllPublicFields);
        }
    }

    private void mergeMethodsFrom(ConfigurationType other) {
        if (other.methods != null) {
            if (methods == null) {
                methods = new HashMap<>();
            }
            for (Map.Entry<ConfigurationMethod, ConfigurationMemberInfo> methodEntry : other.methods.entrySet()) {
                methods.compute(methodEntry.getKey(), (key, value) -> {
                    if (value != null) {
                        return value.intersect(methodEntry.getValue());
                    } else {
                        return methodEntry.getValue();
                    }
                });
            }
        }
        maybeRemoveMethods(allDeclaredMethodsAccess, allPublicMethodsAccess, allDeclaredConstructorsAccess, allPublicConstructorsAccess);
    }

    private void maybeRemoveMethods(ConfigurationMemberAccessibility hasAllDeclaredMethods, ConfigurationMemberAccessibility hasAllPublicMethods,
                    ConfigurationMemberAccessibility hasAllDeclaredConstructors,
                    ConfigurationMemberAccessibility hasAllPublicConstructors) {
        if (hasAllDeclaredMethods != ConfigurationMemberAccessibility.NONE) {
            removeMethods(ConfigurationMemberDeclaration.DECLARED, hasAllDeclaredMethods, false);
        }
        if (hasAllDeclaredConstructors != ConfigurationMemberAccessibility.NONE) {
            removeMethods(ConfigurationMemberDeclaration.DECLARED, hasAllDeclaredConstructors, true);
        }

        if (hasAllPublicMethods != ConfigurationMemberAccessibility.NONE) {
            removeMethods(ConfigurationMemberDeclaration.PUBLIC, hasAllPublicMethods, false);
        }
        if (hasAllPublicConstructors != ConfigurationMemberAccessibility.NONE) {
            removeMethods(ConfigurationMemberDeclaration.PUBLIC, hasAllPublicConstructors, true);
        }
    }

    private void intersectWith(ConfigurationType other) {
        intersectFlags(other);
        intersectFields(other);
        intersectMethods(other);
    }

    private void intersectFlags(ConfigurationType other) {
        setFlagsFromOther(other, (our, their) -> our && their, ConfigurationMemberAccessibility::intersect);
    }

    private void intersectFields(ConfigurationType other) {
        if (fields != null) {
            if (other.fields != null) {
                fields.keySet().retainAll(other.fields.keySet());
                fields.replaceAll((key, value) -> value.newIntersectedWith(other.fields.get(key)));
            } else {
                fields = null;
            }
        }
    }

    private void intersectMethods(ConfigurationType other) {
        if (methods != null) {
            if (other.methods != null) {
                methods.keySet().retainAll(other.methods.keySet());
            } else {
                methods = null;
            }
        }
    }

    private void removeAll(ConfigurationType other) {
        assert sameTypeAndCondition(other);
        removeFlags(other);
        removeFields(other);
        removeMethods(other);
    }

    private void removeFlags(ConfigurationType other) {
        setFlagsFromOther(other, (our, their) -> our && !their, ConfigurationMemberAccessibility::remove);
    }

    private void removeFields(ConfigurationType other) {
        maybeRemoveFields(allDeclaredFieldsAccess.combine(other.allDeclaredFieldsAccess), allPublicFieldsAccess.combine(other.allPublicFieldsAccess));
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
        maybeRemoveMethods(allDeclaredMethodsAccess.combine(other.allDeclaredMethodsAccess), allPublicMethodsAccess.combine(other.allPublicMethodsAccess),
                        allDeclaredConstructorsAccess.combine(other.allDeclaredConstructorsAccess), allPublicConstructorsAccess.combine(other.allPublicConstructorsAccess));
        if (methods != null && other.methods != null) {
            for (Map.Entry<ConfigurationMethod, ConfigurationMemberInfo> entry : other.methods.entrySet()) {
                ConfigurationMemberInfo otherMethodInfo = entry.getValue();
                methods.computeIfPresent(entry.getKey(), (method, methodInfo) -> {
                    if (otherMethodInfo.includes(methodInfo)) {
                        return null; // remove
                    }
                    return methodInfo;
                });
            }
            if (methods.isEmpty()) {
                methods = null;
            }
        }
    }

    private void setFlagsFromOther(ConfigurationType other, BiPredicate<Boolean, Boolean> flagPredicate,
                    BiFunction<ConfigurationMemberAccessibility, ConfigurationMemberAccessibility, ConfigurationMemberAccessibility> accessCombiner) {
        allDeclaredClasses = flagPredicate.test(allDeclaredClasses, other.allDeclaredClasses);
        allRecordComponents = flagPredicate.test(allRecordComponents, other.allRecordComponents);
        allPermittedSubclasses = flagPredicate.test(allPermittedSubclasses, other.allPermittedSubclasses);
        allNestMembers = flagPredicate.test(allNestMembers, other.allNestMembers);
        allSigners = flagPredicate.test(allSigners, other.allSigners);
        allPublicClasses = flagPredicate.test(allPublicClasses, other.allPublicClasses);
        allDeclaredFieldsAccess = accessCombiner.apply(allDeclaredFieldsAccess, other.allDeclaredFieldsAccess);
        allPublicFieldsAccess = accessCombiner.apply(allPublicFieldsAccess, other.allPublicFieldsAccess);
        unsafeAllocated = flagPredicate.test(unsafeAllocated, other.unsafeAllocated);
        allDeclaredMethodsAccess = accessCombiner.apply(allDeclaredMethodsAccess, other.allDeclaredMethodsAccess);
        allPublicMethodsAccess = accessCombiner.apply(allPublicMethodsAccess, other.allPublicMethodsAccess);
        allDeclaredConstructorsAccess = accessCombiner.apply(allDeclaredConstructorsAccess, other.allDeclaredConstructorsAccess);
        allPublicConstructorsAccess = accessCombiner.apply(allPublicConstructorsAccess, other.allPublicConstructorsAccess);
        serializable = flagPredicate.test(serializable, other.serializable);
        typeJniAccessible = flagPredicate.test(typeJniAccessible, other.typeJniAccessible);
    }

    private boolean isEmpty() {
        return methods == null && fields == null && allFlagsFalse();
    }

    private boolean allFlagsFalse() {
        return !(allDeclaredClasses || allRecordComponents || allPermittedSubclasses || allNestMembers || allSigners || allPublicClasses || serializable || typeJniAccessible ||
                        allDeclaredFieldsAccess != ConfigurationMemberAccessibility.NONE || allPublicFieldsAccess != ConfigurationMemberAccessibility.NONE ||
                        allDeclaredMethodsAccess != ConfigurationMemberAccessibility.NONE || allPublicMethodsAccess != ConfigurationMemberAccessibility.NONE ||
                        allDeclaredConstructorsAccess != ConfigurationMemberAccessibility.NONE || allPublicConstructorsAccess != ConfigurationMemberAccessibility.NONE);
    }

    public ConfigurationTypeDescriptor getTypeDescriptor() {
        return typeDescriptor;
    }

    public synchronized void addField(String name, ConfigurationMemberDeclaration declaration, boolean finalButWritable) {
        ConfigurationMemberAccessibility accessibility = ConfigurationMemberAccessibility.ACCESSED;
        if (!finalButWritable) {
            if ((declaration.includes(ConfigurationMemberDeclaration.DECLARED) && allDeclaredFieldsAccess.includes(accessibility)) ||
                            (declaration.includes(ConfigurationMemberDeclaration.PUBLIC) && allPublicFieldsAccess.includes(accessibility))) {
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
                        ? FieldInfo.get(v.getKind().intersect(ConfigurationMemberInfo.get(declaration, accessibility)), v.isFinalButWritable() || finalButWritable)
                        : FieldInfo.get(declaration, accessibility, finalButWritable));
    }

    public void addMethodsWithName(String name, ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility) {
        addMethod(name, null, declaration, accessibility);
    }

    public void addMethod(String name, String internalSignature, ConfigurationMemberDeclaration declaration) {
        addMethod(name, internalSignature, declaration, ConfigurationMemberAccessibility.ACCESSED);
    }

    public synchronized void addMethod(String name, String internalSignature, ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility) {
        ConfigurationMemberInfo kind = ConfigurationMemberInfo.get(declaration, accessibility);
        boolean matchesAllSignatures = (internalSignature == null);
        if (!matchesAllSignatures) {
            /*
             * A method with an invalid signature will not match any existing method. The signature
             * is also checked during run-time queries (in this case, JNI's `Get(Static)MethodID`)
             * and the missing registration error check does not happen if the signature is invalid,
             * so there is no need to register a negative query for the method either.
             */
            if (!SignatureUtil.isSignatureValid(internalSignature, true)) {
                return;
            }
        }
        if (ConfigurationMethod.isConstructorName(name) ? hasAllConstructors(declaration, accessibility) : hasAllMethods(declaration, accessibility)) {
            if (!matchesAllSignatures) {
                if (accessibility == ConfigurationMemberAccessibility.ACCESSED) {
                    methods = maybeRemove(methods, map -> map.remove(new ConfigurationMethod(name, internalSignature)));
                } else if (accessibility == ConfigurationMemberAccessibility.QUERIED) {
                    methods = maybeRemove(methods, map -> {
                        ConfigurationMethod method = new ConfigurationMethod(name, internalSignature);
                        /* Querying all methods should not remove individually accessed methods. */
                        if (map.containsKey(method) && map.get(method).getAccessibility() == ConfigurationMemberAccessibility.QUERIED) {
                            map.remove(method);
                        }
                    });
                }
            }
            return;
        }
        if (methods == null) {
            methods = new HashMap<>();
        }
        ConfigurationMethod method = new ConfigurationMethod(name, internalSignature);
        if (matchesAllSignatures) { // remove any methods that the new entry matches
            methods.compute(method, (k, v) -> v != null ? kind.union(v) : kind);
            methods = maybeRemove(methods, map -> map.entrySet().removeIf(entry -> name.equals(entry.getKey().getName()) &&
                            kind.includes(entry.getValue()) && !method.equals(entry.getKey())));
        } else {
            methods.compute(method, (k, v) -> v != null ? kind.intersect(v) : kind);
        }
        assert methods.containsKey(method);
    }

    private boolean hasAllConstructors(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility) {
        return (declaration.includes(ConfigurationMemberDeclaration.DECLARED) && allDeclaredConstructorsAccess.includes(accessibility)) ||
                        (declaration.includes(ConfigurationMemberDeclaration.PUBLIC) && allPublicConstructorsAccess.includes(accessibility));
    }

    private boolean hasAllMethods(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility) {
        return (declaration.includes(ConfigurationMemberDeclaration.DECLARED) && allDeclaredMethodsAccess.includes(accessibility)) ||
                        (declaration.includes(ConfigurationMemberDeclaration.PUBLIC) && allPublicMethodsAccess.includes(accessibility));
    }

    public synchronized void setAllDeclaredClasses() {
        allDeclaredClasses = true;
    }

    public synchronized void setAllRecordComponents() {
        allRecordComponents = true;
    }

    public synchronized void setAllPermittedSubclasses() {
        allPermittedSubclasses = true;
    }

    public synchronized void setAllNestMembers() {
        allNestMembers = true;
    }

    public synchronized void setAllSigners() {
        allSigners = true;
    }

    public synchronized void setAllPublicClasses() {
        allPublicClasses = true;
    }

    public void setUnsafeAllocated() {
        this.unsafeAllocated = true;
    }

    public synchronized void setAllDeclaredFields(ConfigurationMemberAccessibility accessibility) {
        if (!allDeclaredFieldsAccess.includes(accessibility)) {
            allDeclaredFieldsAccess = accessibility;
            removeFields(ConfigurationMemberDeclaration.DECLARED, allDeclaredFieldsAccess);
        }
    }

    public synchronized void setAllPublicFields(ConfigurationMemberAccessibility accessibility) {
        if (!allPublicFieldsAccess.includes(accessibility)) {
            allPublicFieldsAccess = accessibility;
            removeFields(ConfigurationMemberDeclaration.PUBLIC, allDeclaredFieldsAccess);
        }
    }

    public synchronized void setAllDeclaredMethods(ConfigurationMemberAccessibility accessibility) {
        if (!allDeclaredMethodsAccess.includes(accessibility)) {
            allDeclaredMethodsAccess = accessibility;
            removeMethods(ConfigurationMemberDeclaration.DECLARED, accessibility, false);
        }
    }

    public synchronized void setAllPublicMethods(ConfigurationMemberAccessibility accessibility) {
        if (!allPublicMethodsAccess.includes(accessibility)) {
            allPublicMethodsAccess = accessibility;
            removeMethods(ConfigurationMemberDeclaration.PUBLIC, accessibility, false);
        }
    }

    public synchronized void setAllDeclaredConstructors(ConfigurationMemberAccessibility accessibility) {
        if (!allDeclaredConstructorsAccess.includes(accessibility)) {
            allDeclaredConstructorsAccess = accessibility;
            removeMethods(ConfigurationMemberDeclaration.DECLARED, accessibility, true);
        }
    }

    public synchronized void setAllPublicConstructors(ConfigurationMemberAccessibility accessibility) {
        if (!allPublicConstructorsAccess.includes(accessibility)) {
            allPublicConstructorsAccess = accessibility;
            removeMethods(ConfigurationMemberDeclaration.PUBLIC, accessibility, true);
        }
    }

    public synchronized boolean isSerializable() {
        return serializable;
    }

    public synchronized void setSerializable() {
        serializable = true;
    }

    public synchronized boolean isJniAccessible() {
        return typeJniAccessible;
    }

    public synchronized void setJniAccessible() {
        typeJniAccessible = true;
    }

    @Override
    public synchronized void printJson(JsonWriter writer) throws IOException {
        writer.appendObjectStart();
        ConfigurationConditionPrintable.printConditionAttribute(condition, writer, true);
        writer.quote("type").appendFieldSeparator();
        typeDescriptor.printJson(writer);

        printJsonBooleanIfSet(writer, allDeclaredFieldsAccess == ConfigurationMemberAccessibility.ACCESSED, "allDeclaredFields");
        printJsonBooleanIfSet(writer, allPublicFieldsAccess == ConfigurationMemberAccessibility.ACCESSED, "allPublicFields");
        printJsonBooleanIfSet(writer, allDeclaredMethodsAccess == ConfigurationMemberAccessibility.ACCESSED, "allDeclaredMethods");
        printJsonBooleanIfSet(writer, allPublicMethodsAccess == ConfigurationMemberAccessibility.ACCESSED, "allPublicMethods");
        printJsonBooleanIfSet(writer, allDeclaredConstructorsAccess == ConfigurationMemberAccessibility.ACCESSED, "allDeclaredConstructors");
        printJsonBooleanIfSet(writer, allPublicConstructorsAccess == ConfigurationMemberAccessibility.ACCESSED, "allPublicConstructors");
        printJsonBooleanIfSet(writer, unsafeAllocated, "unsafeAllocated");
        printJsonBooleanIfSet(writer, serializable, "serializable");
        printJsonBooleanIfSet(writer, typeJniAccessible, "jniAccessible");

        if (fields != null) {
            writer.appendSeparator().quote("fields").appendFieldSeparator();
            JsonPrinter.printCollection(writer, fields.entrySet(), Map.Entry.comparingByKey(), ConfigurationType::printField);
        }
        if (methods != null) {
            Set<ConfigurationMethod> accessedMethods = getMethodsByAccessibility(ConfigurationMemberAccessibility.ACCESSED);
            if (!accessedMethods.isEmpty()) {
                writer.appendSeparator().quote("methods").appendFieldSeparator();
                Comparator<ConfigurationMethod> methodComparator = Comparator.comparing(ConfigurationMethod::getName)
                                .thenComparing(Comparator.nullsFirst(Comparator.comparing(ConfigurationMethod::getInternalSignature)));
                JsonPrinter.printCollection(writer, accessedMethods, methodComparator, JsonPrintable::printJson);
            }
        }

        writer.appendObjectEnd();
    }

    private Set<ConfigurationMethod> getMethodsByAccessibility(ConfigurationMemberAccessibility accessibility) {
        return methods.entrySet().stream().filter(e -> e.getValue().getAccessibility() == accessibility).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static void printField(Map.Entry<String, FieldInfo> entry, JsonWriter w) throws IOException {
        w.appendObjectStart().quote("name").appendFieldSeparator().quote(entry.getKey());
        if (entry.getValue().isFinalButWritable()) {
            w.appendSeparator().quote("allowWrite").appendFieldSeparator().append("true");
        }
        w.appendObjectEnd();
    }

    private static void printJsonBooleanIfSet(JsonWriter writer, boolean predicate, String attribute) throws IOException {
        if (predicate) {
            printJsonBoolean(writer, predicate, attribute);
        }
    }

    private static void printJsonBoolean(JsonWriter writer, boolean value, String attribute) throws IOException {
        writer.appendSeparator().quote(attribute).appendFieldSeparator().append(Boolean.toString(value));
    }

    private void removeFields(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility) {
        ConfigurationMemberInfo kind = ConfigurationMemberInfo.get(declaration, accessibility);
        fields = maybeRemove(fields, map -> map.entrySet().removeIf(entry -> kind.includes(entry.getValue().getKind())));
    }

    private void removeMethods(ConfigurationMemberDeclaration declaration, ConfigurationMemberAccessibility accessibility, boolean constructors) {
        ConfigurationMemberInfo kind = ConfigurationMemberInfo.get(declaration, accessibility);
        methods = maybeRemove(methods, map -> map.entrySet().removeIf(entry -> entry.getKey().isConstructor() == constructors && kind.includes(entry.getValue())));
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

    UnresolvedConfigurationCondition getCondition() {
        return condition;
    }

    /**
     * Backdoor for tests. No synchronization is guaranteed here, tests are assumed to be
     * single-threaded or must synchronize accesses themselves.
     */
    public static final class TestBackdoor {
        public static ConfigurationMemberInfo getMethodInfoIfPresent(ConfigurationType type, ConfigurationMethod method) {
            return (type.methods == null) ? null : type.methods.get(method);
        }

        public static FieldInfo getFieldInfoIfPresent(ConfigurationType type, String fieldName) {
            return (type.fields == null) ? null : type.fields.get(fieldName);
        }

        public static ConfigurationMemberAccessibility getAllDeclaredFields(ConfigurationType self) {
            return self.allDeclaredFieldsAccess;
        }

        public static ConfigurationMemberAccessibility getAllPublicFields(ConfigurationType type) {
            return type.allPublicFieldsAccess;
        }

        public static boolean haveAllDeclaredClasses(ConfigurationType type) {
            return type.allDeclaredClasses;
        }

        public static boolean haveAllRecordComponents(ConfigurationType type) {
            return type.allRecordComponents;
        }

        public static boolean haveAllPermittedSubclasses(ConfigurationType type) {
            return type.allPermittedSubclasses;
        }

        public static boolean haveAllNestMembers(ConfigurationType type) {
            return type.allNestMembers;
        }

        public static boolean haveAllSigners(ConfigurationType type) {
            return type.allSigners;
        }

        public static boolean haveAllPublicClasses(ConfigurationType type) {
            return type.allPublicClasses;
        }

        public static ConfigurationMemberAccessibility getAllDeclaredConstructors(ConfigurationType type) {
            return type.allDeclaredConstructorsAccess;
        }

        public static ConfigurationMemberAccessibility getAllPublicConstructors(ConfigurationType type) {
            return type.allPublicConstructorsAccess;
        }

        private TestBackdoor() {
        }
    }
}
