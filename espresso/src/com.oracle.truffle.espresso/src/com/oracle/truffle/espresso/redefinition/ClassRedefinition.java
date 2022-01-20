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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.impl.RedefineAddedField;
import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefineListener;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class ClassRedefinition {

    private final Object redefineLock = new Object();
    private volatile boolean locked = false;
    private Thread redefineThread = null;

    private final EspressoContext context;
    private final Ids<Object> ids;
    private final RedefineListener redefineListener;
    private volatile Assumption missingFieldAssumption = Truffle.getRuntime().createAssumption();
    private ArrayList<Field> currentDelegationFields;
    private AtomicInteger nextAvailableFieldSlot = new AtomicInteger(-1);

    public Assumption getMissingFieldAssumption() {
        return missingFieldAssumption;
    }

    public void invalidateMissingFields() {
        missingFieldAssumption.invalidate();
        missingFieldAssumption = Truffle.getRuntime().createAssumption();
    }

    enum ClassChange {
        // currently supported
        NO_CHANGE,
        CONSTANT_POOL_CHANGE,
        METHOD_BODY_CHANGE,
        CLASS_NAME_CHANGED,
        ADD_METHOD,
        REMOVE_METHOD,
        NEW_CLASS,
        // currently supported under option
        SCHEMA_CHANGE,
        INVALID;
    }

    public ClassRedefinition(EspressoContext context, Ids<Object> ids, RedefineListener listener) {
        this.context = context;
        this.ids = ids;
        this.redefineListener = listener;
    }

    public void begin() {
        synchronized (redefineLock) {
            while (locked) {
                try {
                    redefineLock.wait();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
            }
            // the redefine thread is privileged
            redefineThread = Thread.currentThread();
            locked = true;
        }
    }

    public void end() {
        synchronized (redefineLock) {
            locked = false;
            redefineThread = null;
            redefineLock.notifyAll();
        }
    }

    public boolean isRedefineThread() {
        return redefineThread == Thread.currentThread();
    }

    public void addExtraReloadClasses(List<RedefineInfo> redefineInfos, List<RedefineInfo> additional) {
        redefineListener.collectExtraClassesToReload(redefineInfos, additional);
    }

    public void runPostRedefintionListeners(ObjectKlass[] changedKlasses) {
        redefineListener.postRedefinition(changedKlasses);
    }

    public void check() {
        CompilerAsserts.neverPartOfCompilation();
        // block until redefinition is done
        if (locked) {
            if (redefineThread == Thread.currentThread()) {
                // let the redefine thread pass
                return;
            }
            synchronized (redefineLock) {
                while (locked) {
                    try {
                        redefineLock.wait();
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }
        }
    }

    public synchronized Field createDelegationFrom(Field field) {
        Field delegationField = RedefineAddedField.createDelegationField(field);
        if (currentDelegationFields == null) {
            currentDelegationFields = new ArrayList<>(1);
        }
        currentDelegationFields.add(delegationField);
        return delegationField;
    }

    public synchronized void clearDelegationFields() {
        if (currentDelegationFields != null) {
            for (Field field : currentDelegationFields) {
                field.removeByRedefintion();
            }
            currentDelegationFields.clear();
        }
    }

    public List<ChangePacket> detectClassChanges(HotSwapClassInfo[] classInfos) throws RedefintionNotSupportedException {
        List<ChangePacket> result = new ArrayList<>(classInfos.length);
        for (HotSwapClassInfo hotSwapInfo : classInfos) {
            ObjectKlass klass = hotSwapInfo.getKlass();
            if (klass == null) {
                // New anonymous inner class
                result.add(new ChangePacket(hotSwapInfo, ClassChange.NEW_CLASS));
                continue;
            }
            byte[] bytes = hotSwapInfo.getBytes();
            ParserKlass parserKlass;
            ParserKlass newParserKlass = null;
            ClassChange classChange;
            DetectedChange detectedChange = new DetectedChange();
            StaticObject loader = klass.getDefiningClassLoader();
            Types types = klass.getContext().getTypes();
            parserKlass = ClassfileParser.parse(new ClassfileStream(bytes, null), loader, types.fromName(hotSwapInfo.getName()), context);
            if (hotSwapInfo.isPatched()) {
                byte[] patched = hotSwapInfo.getPatchedBytes();
                newParserKlass = parserKlass;
                // we detect changes against the patched bytecode
                parserKlass = ClassfileParser.parse(new ClassfileStream(patched, null), loader, types.fromName(hotSwapInfo.getNewName()), context);
            }
            classChange = detectClassChanges(parserKlass, klass, detectedChange, newParserKlass);
            result.add(new ChangePacket(hotSwapInfo, newParserKlass != null ? newParserKlass : parserKlass, classChange, detectedChange));
        }
        return result;
    }

    public int redefineClass(ChangePacket packet, List<ObjectKlass> invalidatedClasses, List<ObjectKlass> redefinedClasses) {
        try {
            switch (packet.classChange) {
                case METHOD_BODY_CHANGE:
                case CONSTANT_POOL_CHANGE:
                case CLASS_NAME_CHANGED:
                case ADD_METHOD:
                case REMOVE_METHOD:
                case SCHEMA_CHANGE:
                    doRedefineClass(packet, invalidatedClasses, redefinedClasses);
                    return 0;
                case NEW_CLASS:
                    ClassInfo classInfo = packet.info;

                    // if there is a currently loaded class under that name
                    // we have to replace that in the class loader registry etc.
                    // otherwise, don't eagerly define the new class
                    Symbol<Symbol.Type> type = context.getTypes().fromName(classInfo.getName());
                    ClassRegistry classRegistry = context.getRegistries().getClassRegistry(classInfo.getClassLoader());
                    Klass loadedKlass = classRegistry.findLoadedKlass(type);
                    if (loadedKlass != null) {
                        // OK, we have to define the new klass instance and
                        // inject it under the existing JDWP ID
                        classRegistry.onInnerClassRemoved(type);
                        ObjectKlass newKlass = classRegistry.defineKlass(type, classInfo.getBytes());
                        packet.info.setKlass(newKlass);
                    }
                    return 0;
                default:
                    return 0;
            }
        } catch (EspressoException ex) {
            // TODO(Gregersen) - return appropriate error code based on the exception type
            // we get from parsing the class file
            return ErrorCodes.INVALID_CLASS_FORMAT;
        }
    }

    // detect all types of class changes, but return early when a change that require arbitrary
    // changes
    private static ClassChange detectClassChanges(ParserKlass newParserKlass, ObjectKlass oldKlass, DetectedChange collectedChanges, ParserKlass finalParserKlass)
                    throws RedefintionNotSupportedException {
        ClassChange result = ClassChange.NO_CHANGE;
        ParserKlass oldParserKlass = oldKlass.getLinkedKlass().getParserKlass();
        boolean isPatched = finalParserKlass != null;

        if (!newParserKlass.getSuperKlass().equals(oldParserKlass.getSuperKlass()) || !Arrays.equals(newParserKlass.getSuperInterfaces(), oldParserKlass.getSuperInterfaces())) {
            throw new RedefintionNotSupportedException(ErrorCodes.HIERARCHY_CHANGE_NOT_IMPLEMENTED);
        }

        // detect method changes (including constructors)
        ParserMethod[] newParserMethods = newParserKlass.getMethods();
        List<Method> oldMethods = new ArrayList<>(Arrays.asList(oldKlass.getDeclaredMethods()));
        List<ParserMethod> newMethods = new ArrayList<>(Arrays.asList(newParserMethods));
        Map<Method, ParserMethod> bodyChanges = new HashMap<>();
        List<ParserMethod> newSpecialMethods = new ArrayList<>(1);

        boolean constantPoolChanged = false;
        if (!Arrays.equals(oldParserKlass.getConstantPool().getRawBytes(), newParserKlass.getConstantPool().getRawBytes())) {
            constantPoolChanged = true;
        }
        Iterator<Method> oldIt = oldMethods.iterator();
        Iterator<ParserMethod> newIt;
        while (oldIt.hasNext()) {
            Method oldMethod = oldIt.next();
            ParserMethod oldParserMethod = oldMethod.getLinkedMethod().getParserMethod();
            // verify that there is a new corresponding method
            newIt = newMethods.iterator();
            while (newIt.hasNext()) {
                ParserMethod newMethod = newIt.next();
                if (isSameMethod(oldParserMethod, newMethod)) {
                    // detect method changes
                    ClassChange change = detectMethodChanges(oldParserMethod, newMethod);
                    switch (change) {
                        case NO_CHANGE:
                            if (isPatched) {
                                checkForSpecialConstructor(collectedChanges, bodyChanges, newSpecialMethods, oldMethod, oldParserMethod, newMethod);
                            } else if (constantPoolChanged) {
                                if (isObsolete(oldParserMethod, newMethod, oldParserKlass.getConstantPool(), newParserKlass.getConstantPool())) {
                                    result = ClassChange.CONSTANT_POOL_CHANGE;
                                    collectedChanges.addMethodBodyChange(oldMethod, newMethod);
                                } else {
                                    collectedChanges.addUnchangedMethod(oldMethod);
                                }
                            } else {
                                collectedChanges.addUnchangedMethod(oldMethod);
                            }
                            break;
                        case METHOD_BODY_CHANGE:
                            result = change;
                            if (isPatched) {
                                checkForSpecialConstructor(collectedChanges, bodyChanges, newSpecialMethods, oldMethod, oldParserMethod, newMethod);
                            } else {
                                collectedChanges.addMethodBodyChange(oldMethod, newMethod);
                            }
                            break;
                        default:
                            return change;
                    }
                    newIt.remove();
                    oldIt.remove();
                    break;
                }
            }
        }
        if (isPatched) {
            ParserMethod[] finalMethods = finalParserKlass.getMethods();
            // lookup the final new method based on the index in the parser method array

            // map found changed methods
            for (Map.Entry<Method, ParserMethod> entry : bodyChanges.entrySet()) {
                Method oldMethod = entry.getKey();
                ParserMethod changed = entry.getValue();
                for (int i = 0; i < newParserMethods.length; i++) {
                    if (newParserMethods[i] == changed) {
                        collectedChanges.addMethodBodyChange(oldMethod, finalMethods[i]);
                        break;
                    }
                }
            }
            // map found new methods
            newMethods.addAll(newSpecialMethods);
            for (ParserMethod changed : newMethods) {
                for (int i = 0; i < newParserMethods.length; i++) {
                    if (newParserMethods[i] == changed) {
                        collectedChanges.addNewMethod(finalMethods[i]);
                        break;
                    }
                }
            }
        } else {
            collectedChanges.addNewMethods(newMethods);
        }

        for (Method oldMethod : oldMethods) {
            collectedChanges.addRemovedMethod(oldMethod.getMethodVersion());
        }

        if (!oldMethods.isEmpty()) {
            result = ClassChange.REMOVE_METHOD;
        } else if (!newMethods.isEmpty()) {
            result = ClassChange.ADD_METHOD;
        }

        if (isPatched) {
            result = ClassChange.CLASS_NAME_CHANGED;
        }

        // detect field changes
        Field[] oldFields = oldKlass.getDeclaredFields();
        ParserField[] newFields = newParserKlass.getFields();

        ArrayList<Field> oldFieldsList = new ArrayList<>(Arrays.asList(oldFields));
        ArrayList<ParserField> newFieldsList = new ArrayList<>(Arrays.asList(newFields));
        Map<ParserField, Field> compatibleFields = new HashMap<>();

        Iterator<Field> oldFieldsIt = oldFieldsList.iterator();
        Iterator<ParserField> newFieldsIt;

        while (oldFieldsIt.hasNext()) {
            Field oldField = oldFieldsIt.next();
            newFieldsIt = newFieldsList.iterator();
            // search for a new corresponding field
            while (newFieldsIt.hasNext()) {
                ParserField newField = newFieldsIt.next();
                // first look for a perfect match
                if (isUnchangedField(oldField, newField, compatibleFields)) {
                    // A nested anonymous inner class may contain a field reference to the outer
                    // class instance. Since we match against the patched (inner class rename rules
                    // applied) if the current class was patched (renamed) the resulting outer
                    // field pointer will have a changed type. Hence we should mark it as a new
                    // field.
                    Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(oldField.getType().toString());
                    if (isPatched && matcher.matches()) {
                        break;
                    }
                    oldFieldsIt.remove();
                    newFieldsIt.remove();
                    break;
                }
            }
        }

        if (!newFieldsList.isEmpty()) {
            if (isPatched) {
                ParserField[] finalFields = finalParserKlass.getFields();
                // lookup the final new field based on the index in the parser field array
                for (ParserField parserField : newFieldsList) {
                    for (int i = 0; i < newFields.length; i++) {
                        if (parserField == newFields[i]) {
                            collectedChanges.addNewField(finalFields[i]);
                            break;
                        }
                    }
                }
            } else {
                collectedChanges.addNewFields(newFieldsList);
            }
            result = ClassChange.SCHEMA_CHANGE;
        }

        if (!oldFieldsList.isEmpty()) {
            collectedChanges.addRemovedFields(oldFieldsList);
            result = ClassChange.SCHEMA_CHANGE;
        }

        // detect class-level changes
        if (newParserKlass.getFlags() != oldParserKlass.getFlags()) {
            result = ClassChange.SCHEMA_CHANGE;
        }

        collectedChanges.addCompatibleFields(compatibleFields);
        return result;
    }

    private static void checkForSpecialConstructor(DetectedChange collectedChanges, Map<Method, ParserMethod> bodyChanges, List<ParserMethod> newSpecialMethods,
                    Method oldMethod, ParserMethod oldParserMethod, ParserMethod newMethod) {
        // mark constructors of nested anonymous inner classes
        // if they include an anonymous inner class type parameter
        if (Symbol.Name._init_.equals(oldParserMethod.getName()) && Symbol.Signature._void != oldParserMethod.getSignature()) {
            // only mark constructors that contain the outer anonymous inner class
            Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(oldParserMethod.getSignature().toString());
            if (matcher.matches()) {
                newSpecialMethods.add(newMethod);
                collectedChanges.addRemovedMethod(oldMethod.getMethodVersion());
            } else {
                bodyChanges.put(oldMethod, newMethod);
            }
        } else {
            // for class-name patched classes we have to redefine all methods
            bodyChanges.put(oldMethod, newMethod);
        }
    }

    private static boolean isObsolete(ParserMethod oldMethod, ParserMethod newMethod, ConstantPool oldPool, ConstantPool newPool) {
        CodeAttribute oldCodeAttribute = (CodeAttribute) oldMethod.getAttribute(Symbol.Name.Code);
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Symbol.Name.Code);
        if (oldCodeAttribute == null) {
            return newCodeAttribute != null;
        } else if (newCodeAttribute == null) {
            return oldCodeAttribute != null;
        }
        BytecodeStream oldCode = new BytecodeStream(oldCodeAttribute.getOriginalCode());
        BytecodeStream newCode = new BytecodeStream(newCodeAttribute.getOriginalCode());

        return !isSame(oldCode, oldPool, newCode, newPool);
    }

    private static boolean isSame(BytecodeStream oldCode, ConstantPool oldPool, BytecodeStream newCode, ConstantPool newPool) {
        int bci;
        int nextBCI = 0;
        while (nextBCI < oldCode.endBCI()) {
            bci = nextBCI;
            int opcode = oldCode.currentBC(bci);
            nextBCI = oldCode.nextBCI(bci);
            if (opcode == Bytecodes.LDC ||
                            opcode == Bytecodes.LDC2_W ||
                            opcode == Bytecodes.LDC_W ||
                            opcode == Bytecodes.NEW ||
                            opcode == Bytecodes.INVOKEDYNAMIC ||
                            opcode == Bytecodes.GETFIELD ||
                            opcode == Bytecodes.GETSTATIC ||
                            opcode == Bytecodes.PUTFIELD ||
                            opcode == Bytecodes.PUTSTATIC ||
                            Bytecodes.isInvoke(opcode)) {
                int oldCPI = oldCode.readCPI(bci);
                PoolConstant oldConstant = oldPool.at(oldCPI);
                int newCPI = newCode.readCPI(bci);
                PoolConstant newConstant = newPool.at(newCPI);
                if (!oldConstant.toString(oldPool).equals(newConstant.toString(newPool))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ClassChange detectMethodChanges(ParserMethod oldMethod, ParserMethod newMethod) {
        // check code attribute
        CodeAttribute oldCodeAttribute = (CodeAttribute) oldMethod.getAttribute(Symbol.Name.Code);
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Symbol.Name.Code);

        if (oldCodeAttribute == null) {
            return newCodeAttribute != null ? ClassChange.METHOD_BODY_CHANGE : ClassChange.NO_CHANGE;
        } else if (newCodeAttribute == null) {
            return oldCodeAttribute != null ? ClassChange.METHOD_BODY_CHANGE : ClassChange.NO_CHANGE;
        }

        if (!Arrays.equals(oldCodeAttribute.getOriginalCode(), newCodeAttribute.getOriginalCode())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }
        // check line number table
        if (checkLineNumberTable(oldCodeAttribute.getLineNumberTableAttribute(), newCodeAttribute.getLineNumberTableAttribute())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        // check local variable table
        if (checkLocalVariableTable(oldCodeAttribute.getLocalvariableTable(), newCodeAttribute.getLocalvariableTable())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        // check local variable type table
        if (checkLocalVariableTable(oldCodeAttribute.getLocalvariableTypeTable(), newCodeAttribute.getLocalvariableTypeTable())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        return ClassChange.NO_CHANGE;
    }

    private static boolean checkLineNumberTable(LineNumberTableAttribute table1, LineNumberTableAttribute table2) {
        List<LineNumberTableAttribute.Entry> oldEntries = table1.getEntries();
        List<LineNumberTableAttribute.Entry> newEntries = table2.getEntries();

        if (oldEntries.size() != newEntries.size()) {
            return true;
        }

        for (int i = 0; i < oldEntries.size(); i++) {
            LineNumberTableAttribute.Entry oldEntry = oldEntries.get(i);
            LineNumberTableAttribute.Entry newEntry = newEntries.get(i);
            if (oldEntry.getLineNumber() != newEntry.getLineNumber() || oldEntry.getBCI() != newEntry.getBCI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkLocalVariableTable(LocalVariableTable table1, LocalVariableTable table2) {
        Local[] oldLocals = table1.getLocals();
        Local[] newLocals = table2.getLocals();

        if (oldLocals.length != newLocals.length) {
            return true;
        }

        for (int i = 0; i < oldLocals.length; i++) {
            Local oldLocal = oldLocals[i];
            Local newLocal = newLocals[i];
            if (!oldLocal.getNameAsString().equals(newLocal.getNameAsString()) || oldLocal.getSlot() != newLocal.getSlot() || oldLocal.getStartBCI() != newLocal.getStartBCI() ||
                            oldLocal.getEndBCI() != newLocal.getEndBCI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean attrChanged(ParserMethod oldMethod, ParserMethod newMethod, Symbol<Symbol.Name> name) {
        Attribute oldAttribute = oldMethod.getAttribute(name);
        Attribute newAttribute = newMethod.getAttribute(name);
        if ((oldAttribute == null || newAttribute == null)) {
            if (oldAttribute != null || newAttribute != null) {
                return true;
            } // else both null, so no change. Move on!
        } else if (!oldAttribute.sameAs(newAttribute)) {
            return true;
        }
        return false;
    }

    private static boolean isSameMethod(ParserMethod oldMethod, ParserMethod newMethod) {
        boolean same = oldMethod.getName().equals(newMethod.getName()) &&
                        oldMethod.getSignature().equals(newMethod.getSignature()) &&
                        oldMethod.getFlags() == newMethod.getFlags();
        if (same) {
            // check method attributes that would constitute a higher-level
            // class redefinition than a method body change
            if (attrChanged(oldMethod, newMethod, Symbol.Name.RuntimeVisibleTypeAnnotations)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.RuntimeInvisibleTypeAnnotations)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.RuntimeVisibleAnnotations)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.RuntimeInvisibleAnnotations)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.RuntimeInvisibleParameterAnnotations)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.RuntimeVisibleParameterAnnotations)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.Exceptions)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.Signature)) {
                return false;
            }

            if (attrChanged(oldMethod, newMethod, Symbol.Name.Exceptions)) {
                return false;
            }
        }
        return same;
    }

    private static boolean isUnchangedField(Field oldField, ParserField newField, Map<ParserField, Field> compatibleFields) {
        boolean sameName = oldField.getName() == newField.getName();
        boolean sameType = oldField.getType() == newField.getType();
        boolean sameFlags = oldField.getModifiers() == (newField.getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS);

        if (sameName && sameType) {
            if (sameFlags) {
                // same name + type + flags

                // check field attributes
                Attribute[] oldAttributes = oldField.getAttributes();
                Attribute[] newAttributes = newField.getAttributes();

                if (oldAttributes.length != newAttributes.length) {
                    return false;
                }

                for (Attribute oldAttribute : oldAttributes) {
                    boolean found = false;
                    for (Attribute newAttribute : newAttributes) {
                        if (oldAttribute.getName() == newAttribute.getName() && oldAttribute.sameAs(newAttribute)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                // identical field found
                return true;
            } else {
                // same name + type
                if (Modifier.isStatic(oldField.getModifiers()) != Modifier.isStatic(newField.getFlags())) {
                    // a change from static -> non-static or vice versa is not compatible
                } else {
                    compatibleFields.put(newField, oldField);
                }
                return false;
            }
        }
        return false;
    }

    private void doRedefineClass(ChangePacket packet, List<ObjectKlass> invalidatedClasses, List<ObjectKlass> redefinedClasses) {
        ObjectKlass oldKlass = packet.info.getKlass();
        if (packet.info.isRenamed()) {
            // renaming a class is done by
            // 1. Rename the 'name' and 'type' Symbols in the Klass
            // 2. Update the loaded class cache in the associated ClassRegistry
            // 3. Set the guest language java.lang.Class#name field to null
            // 4. update the JDWP refType ID for the klass instance
            // 5. replace/record a classloader constraint for the new type and klass combination

            Symbol<Symbol.Name> newName = packet.info.getName();
            Symbol<Symbol.Type> newType = context.getTypes().fromName(newName);

            oldKlass.patchClassName(newName, newType);
            ClassRegistry classRegistry = context.getRegistries().getClassRegistry(packet.info.getClassLoader());
            classRegistry.onClassRenamed(oldKlass);

            InterpreterToVM.setFieldObject(StaticObject.NULL, oldKlass.mirror(), context.getMeta().java_lang_Class_name);
        }
        oldKlass.redefineClass(packet, invalidatedClasses, ids);
        redefinedClasses.add(oldKlass);
        if (redefineListener.shouldRerunClassInitializer(oldKlass, packet.detectedChange.clinitChanged())) {
            context.rerunclinit(oldKlass);
        }
    }

    /**
     * @param accessingKlass the receiver's klass when the method is not static, resolutionSeed's
     *            declaring klass otherwise
     */
    @TruffleBoundary
    public Method handleRemovedMethod(Method resolutionSeed, Klass accessingKlass) {
        // wait for potential ongoing redefinition to complete
        check();
        Method replacementMethod = accessingKlass.lookupMethod(resolutionSeed.getName(), resolutionSeed.getRawSignature(), accessingKlass);
        Meta meta = resolutionSeed.getMeta();
        if (replacementMethod == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError,
                            meta.toGuestString(resolutionSeed.getDeclaringKlass().getNameAsString() + "." + resolutionSeed.getName() + resolutionSeed.getRawSignature()) +
                                            " was removed by class redefinition");
        } else if (resolutionSeed.isStatic() != replacementMethod.isStatic()) {
            String message = resolutionSeed.isStatic() ? "expected static method: " : "expected non-static method:" + replacementMethod.getName();
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, message);
        } else {
            // Update to the latest version of the replacement method
            return replacementMethod;
        }
    }

    public int getNextAvailableFieldSlot() {
        return nextAvailableFieldSlot.getAndDecrement();
    }
}
