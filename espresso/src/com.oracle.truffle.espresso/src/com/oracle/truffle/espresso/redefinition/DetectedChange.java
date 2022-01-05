/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.redefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserMethod;

public final class DetectedChange {
    private final List<ParserField> addedStaticFields = new ArrayList<>();
    private final List<ParserField> addedInstanceFields = new ArrayList<>();
    private final List<Field> removedFields = new ArrayList<>();
    private final Map<Method, ParserMethod> changedMethodBodies = new HashMap<>();
    private final List<ParserMethod> addedMethods = new ArrayList<>();
    private final Set<Method.MethodVersion> removedMethods = new HashSet<>();
    private final Set<Method> unchangedMethods = new HashSet<>();
    private boolean classInitializerChanged;
    private Map<ParserField, Field> mappedCompatibleFields = new HashMap<>();
    private ObjectKlass superKlass;
    private ObjectKlass[] superInterfaces;
    private boolean isChangedSuperClass;

    public void addNewField(ParserField parserField) {
        if (parserField.isStatic()) {
            addedStaticFields.add(parserField);
        } else {
            addedInstanceFields.add(parserField);
        }
    }

    public void addNewFields(ArrayList<ParserField> newFields) {
        for (ParserField newField : newFields) {
            addNewField(newField);
        }
    }

    public void addCompatibleFields(Map<ParserField, Field> compatibleFields) {
        mappedCompatibleFields = compatibleFields;
    }

    public Map<ParserField, Field> getMappedCompatibleFields() {
        return Collections.unmodifiableMap(mappedCompatibleFields);
    }

    public List<ParserField> getAddedStaticFields() {
        return Collections.unmodifiableList(addedStaticFields);
    }

    public List<ParserField> getAddedInstanceFields() {
        return Collections.unmodifiableList(addedInstanceFields);
    }

    public void addRemovedFields(ArrayList<Field> removed) {
        removedFields.addAll(removed);
    }

    public List<Field> getRemovedFields() {
        return Collections.unmodifiableList(removedFields);
    }

    void addMethodBodyChange(Method oldMethod, ParserMethod newMethod) {
        changedMethodBodies.put(oldMethod, newMethod);
        if (oldMethod.getName() == Symbol.Name._clinit_) {
            classInitializerChanged = true;
        }
    }

    public Map<Method, ParserMethod> getChangedMethodBodies() {
        return Collections.unmodifiableMap(changedMethodBodies);
    }

    public List<ParserMethod> getAddedMethods() {
        return Collections.unmodifiableList(addedMethods);
    }

    public Set<Method.MethodVersion> getRemovedMethods() {
        return Collections.unmodifiableSet(removedMethods);
    }

    public Set<Method> getUnchangedMethods() {
        return Collections.unmodifiableSet(unchangedMethods);
    }

    public void addNewMethod(ParserMethod method) {
        addedMethods.add(method);
    }

    public void addRemovedMethod(Method.MethodVersion method) {
        removedMethods.add(method);
    }

    public void addNewMethods(List<ParserMethod> newMethods) {
        addedMethods.addAll(newMethods);
    }

    public void addUnchangedMethod(Method method) {
        unchangedMethods.add(method);
    }

    public boolean clinitChanged() {
        return classInitializerChanged;
    }

    public ObjectKlass getSuperKlass() {
        return superKlass;
    }

    public ObjectKlass[] getSuperInterfaces() {
        return superInterfaces;
    }

    public void addSuperKlass(ObjectKlass klass) {
        superKlass = klass;
    }

    public void addSuperInterfaces(ObjectKlass[] interfaces) {
        superInterfaces = interfaces;
    }

    public void markChangedSuperClass() {
        isChangedSuperClass = true;
    }

    public boolean isChangedSuperClass() {
        return isChangedSuperClass;
    }
}
