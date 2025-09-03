/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.classfile;

import static com.oracle.svm.interpreter.classfile.ClassFile.REF_getField;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_getStatic;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_invokeInterface;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_invokeSpecial;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_invokeStatic;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_invokeVirtual;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_newInvokeSpecial;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_putField;
import static com.oracle.svm.interpreter.classfile.ClassFile.REF_putStatic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.descriptors.Descriptor;
import com.oracle.svm.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.Validation;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Utility to create hybrid constant pools that can be purely symbolic, or have resolved/unresolved
 * cached entries derived from JVMCI data-structures.
 */
public final class ConstantPoolBuilder {

    private final Map<Symbol<?>, Integer> symbolIndex = new HashMap<>();
    private final List<Symbol<?>> symbols = new ArrayList<>();

    // INVALID entries are not cached here.
    private final Map<Entry, Integer> entryIndex = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();

    private final InterpreterResolvedObjectType holder;
    private final int majorVersion;
    private final int minorVersion;
    private int position;

    public ConstantPoolBuilder(InterpreterResolvedObjectType holder, int majorVersion, int minorVersion) {
        this.holder = holder;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    private record Entry(ConstantPool.Tag tag, long rawEntry, Object cachedEntry) {
    }

    private int symbolIndex(Symbol<?> symbol) {
        return symbolIndex.computeIfAbsent(symbol, key -> {
            symbols.add(symbol);
            return symbols.size() - 1;
        });
    }

    int getPosition() {
        return this.position;
    }

    void setPosition(int newPosition) {
        this.position = newPosition;
    }

    private int appendRawEntry(ConstantPool.Tag tag, long rawEntry, Object cachedEntry) {
        assert tag == ConstantPool.Tag.DOUBLE || tag == ConstantPool.Tag.LONG || rawEntry == (int) rawEntry;
        Entry newEntry = new Entry(tag, rawEntry, cachedEntry);
        if (tag == ConstantPool.Tag.INVALID) {
            // INVALID entries are never cached.
            return appendEntryAtPosition(newEntry);
        } else {
            return entryIndex.computeIfAbsent(newEntry, key -> {
                int cpi = appendEntryAtPosition(newEntry);
                if (tag == ConstantPool.Tag.LONG || tag == ConstantPool.Tag.DOUBLE) {
                    // Two slot entry, append dummy INVALID with lower half.
                    Entry invalidEntry = new Entry(ConstantPool.Tag.INVALID, (int) rawEntry, null);
                    appendEntryAtPosition(invalidEntry);
                }
                return cpi;
            });
        }
    }

    private int appendEntryAtPosition(Entry newEntry) {
        while (entries.size() <= getPosition()) {
            entries.add(null);
        }
        assert entries.get(getPosition()) == null;
        entries.set(getPosition(), newEntry);
        int cpi = getPosition();
        setPosition(getPosition() + 1);
        return cpi;
    }

    private int appendEntry(ConstantPool.Tag tag, int entry, Object cachedEntry) {
        assert tag != ConstantPool.Tag.LONG && tag != ConstantPool.Tag.DOUBLE;
        return appendRawEntry(tag, entry, cachedEntry);
    }

    private int appendSymbolicEntry(ConstantPool.Tag tag, int entry, Object cachedEntry) {
        return appendEntry(tag, entry, cachedEntry);
    }

    private int appendSymbolicEntry(ConstantPool.Tag tag, int entry) {
        return appendSymbolicEntry(tag, entry, null);
    }

    private int appendCachedEntry(ConstantPool.Tag tag, Object cachedEntry) {
        assert cachedEntry != null;
        return appendEntry(tag, 0, cachedEntry);
    }

    public int appendInvalid() {
        return appendEntry(ConstantPool.Tag.INVALID, 0, null);
    }

    public int appendCachedString(String string) {
        return appendCachedEntry(ConstantPool.Tag.STRING, string);
    }

    public int appendCachedField(JavaField field) {
        assert field instanceof UnresolvedJavaField || field instanceof InterpreterResolvedJavaField;
        return appendCachedEntry(ConstantPool.Tag.FIELD_REF, field);
    }

    public int appendCachedMethod(boolean interfaceMethod, JavaMethod method) {
        assert method instanceof UnresolvedJavaMethod || method instanceof InterpreterResolvedJavaMethod;
        return appendCachedEntry(interfaceMethod ? ConstantPool.Tag.INTERFACE_METHOD_REF : ConstantPool.Tag.METHOD_REF, method);
    }

    public int appendCachedType(JavaType type) {
        assert type instanceof UnresolvedJavaType || type instanceof InterpreterResolvedJavaType;
        return appendCachedEntry(ConstantPool.Tag.CLASS, type);
    }

    public int appendCachedAppendix(Object appendix) {
        assert appendix instanceof MethodHandle ||
                        appendix instanceof ReferenceConstant ||
                        (appendix instanceof JavaConstant javaConstant && javaConstant.isNull());
        return appendCachedEntry(ConstantPool.Tag.INVOKEDYNAMIC, appendix);
    }

    public int appendCachedMethodType(MethodType methodType) {
        return appendCachedEntry(ConstantPool.Tag.METHODTYPE, methodType);
    }

    public int appendCachedMethodHandle(MethodHandle methodHandle) {
        return appendCachedEntry(ConstantPool.Tag.METHODHANDLE, methodHandle);
    }

    public int appendPrimitiveConstant(PrimitiveConstant primitiveConstant) {
        JavaKind javaKind = primitiveConstant.getJavaKind();
        switch (javaKind) {
            case Int, Float -> {
                ConstantPool.Tag tag = javaKind == JavaKind.Int ? ConstantPool.Tag.INTEGER : ConstantPool.Tag.FLOAT;
                int value = javaKind == JavaKind.Int ? primitiveConstant.asInt() : Float.floatToRawIntBits(primitiveConstant.asFloat());
                return appendSymbolicEntry(tag, value, primitiveConstant);
            }
            case Long, Double -> {
                ConstantPool.Tag tag = javaKind == JavaKind.Long ? ConstantPool.Tag.LONG : ConstantPool.Tag.DOUBLE;
                long rawBits = javaKind == JavaKind.Long ? primitiveConstant.asLong() : Double.doubleToRawLongBits(primitiveConstant.asDouble());
                return appendRawEntry(tag, rawBits, primitiveConstant);
            }
            default -> throw new IllegalArgumentException("Invalid primitive constant " + primitiveConstant);
        }
    }

    public int appendSymbolicLong(long value) {
        return appendRawEntry(ConstantPool.Tag.LONG, value, null);
    }

    public int appendSymbolicDouble(double value) {
        return appendRawEntry(ConstantPool.Tag.DOUBLE, Double.doubleToRawLongBits(value), null);
    }

    public int appendSymbolicInteger(int value) {
        return appendSymbolicEntry(ConstantPool.Tag.INTEGER, value);
    }

    public int appendSymbolicFloat(float value) {
        return appendSymbolicEntry(ConstantPool.Tag.FLOAT, Float.floatToRawIntBits(value));
    }

    public int appendSymbolicString(Symbol<? extends ModifiedUTF8> symbol) {
        return appendEntry(ConstantPool.Tag.STRING, appendSymbolicUTF8(symbol), null);
    }

    public int appendSymbolicUTF8(Symbol<? extends ModifiedUTF8> symbol) {
        return appendEntry(ConstantPool.Tag.UTF8, symbolIndex(symbol), null);
    }

    public int appendSymbolicClass(Symbol<Name> classNameSymbol, JavaType cachedType) {
        return appendEntry(ConstantPool.Tag.CLASS, appendSymbolicUTF8(classNameSymbol), cachedType);
    }

    public int appendSymbolicClass(Symbol<Name> classNameSymbol) {
        return appendSymbolicClass(classNameSymbol, null);
    }

    public int appendSymbolicField(Symbol<Name> fieldHolderClassNameEntry, Symbol<Name> fieldName, Symbol<Type> fieldType, JavaField cachedField) {
        assert cachedField == null || cachedField instanceof InterpreterResolvedJavaField || cachedField instanceof UnresolvedJavaField;
        int classIndex = appendSymbolicClass(fieldHolderClassNameEntry);
        int nameAndTypeIndex = appendSymbolicNameAndType(fieldName, fieldType);
        return appendEntry(ConstantPool.Tag.FIELD_REF, cat(classIndex, nameAndTypeIndex), cachedField);
    }

    public int appendSymbolicField(Symbol<Name> fieldHolderClassNameEntry, Symbol<Name> fieldName, Symbol<Type> fieldType) {
        return appendSymbolicField(fieldHolderClassNameEntry, fieldName, fieldType, null);
    }

    public int appendSymbolicMethod(boolean interfaceMethod, Symbol<Name> methodHolderClassNameEntry, Symbol<Name> methodName, Symbol<Signature> methodSignature, JavaMethod cachedMethod) {
        assert cachedMethod == null || cachedMethod instanceof InterpreterResolvedJavaMethod || cachedMethod instanceof UnresolvedJavaMethod;
        int classIndex = appendSymbolicClass(methodHolderClassNameEntry);
        int nameAndTypeIndex = appendSymbolicNameAndType(methodName, methodSignature);
        return appendEntry(
                        interfaceMethod ? ConstantPool.Tag.INTERFACE_METHOD_REF : ConstantPool.Tag.METHOD_REF,
                        cat(classIndex, nameAndTypeIndex),
                        cachedMethod);
    }

    public int appendSymbolicMethod(boolean interfaceMethod, Symbol<Name> methodHolderClassNameEntry, Symbol<Name> methodName, Symbol<Signature> methodSignature) {
        return appendSymbolicMethod(interfaceMethod, methodHolderClassNameEntry, methodName, methodSignature, null);
    }

    public int appendSymbolicNameAndType(Symbol<Name> name, Symbol<? extends Descriptor> descriptor) {
        return appendEntry(ConstantPool.Tag.NAME_AND_TYPE, cat(appendSymbolicUTF8(name), appendSymbolicUTF8(descriptor)), null);
    }

    public int appendSymbolicMethodType(Symbol<Signature> methodSignature, MethodType cachedMethodType) {
        return appendEntry(ConstantPool.Tag.METHODTYPE, appendSymbolicUTF8(methodSignature), cachedMethodType);
    }

    public int appendSymbolicMethodType(Symbol<Signature> methodSignature) {
        return appendSymbolicMethodType(methodSignature, null);
    }

    public int appendSymbolicModule(Symbol<Name> moduleName) {
        return appendEntry(ConstantPool.Tag.MODULE, appendSymbolicUTF8(moduleName), null);
    }

    public int appendSymbolicPackage(Symbol<Name> packageName) {
        return appendEntry(ConstantPool.Tag.PACKAGE, appendSymbolicUTF8(packageName), null);
    }

    public int appendSymbolicMethodHandle(byte referenceKind, boolean interfaceMethod, Symbol<Name> methodHolderClassNameEntry, Symbol<Name> methodName, Symbol<Signature> methodSignature,
                    MethodHandle cachedMethodHandle) {
        VMError.guarantee(referenceKind == REF_invokeVirtual || referenceKind == REF_invokeStatic || referenceKind == REF_invokeSpecial || referenceKind == REF_newInvokeSpecial ||
                        referenceKind == REF_invokeInterface);
        int methodIndex = appendSymbolicMethod(interfaceMethod, methodHolderClassNameEntry, methodName, methodSignature);
        return appendEntry(ConstantPool.Tag.METHODHANDLE, cat(Byte.toUnsignedInt(referenceKind), methodIndex), cachedMethodHandle);
    }

    public int appendSymbolicMethodHandle(byte referenceKind, boolean interfaceMethod, Symbol<Name> methodHolderClassNameEntry, Symbol<Name> methodName, Symbol<Signature> methodSignature) {
        return appendSymbolicMethodHandle(referenceKind, interfaceMethod, methodHolderClassNameEntry, methodName, methodSignature, null);
    }

    public int appendSymbolicMethodHandle(byte referenceKind, Symbol<Name> fieldHolderClassNameEntry, Symbol<Name> fieldName, Symbol<Type> fieldType, MethodHandle cachedMethodHandle) {
        VMError.guarantee(referenceKind == REF_getField || referenceKind == REF_getStatic || referenceKind == REF_putField || referenceKind == REF_putStatic);
        VMError.guarantee(Validation.validClassNameEntry(fieldHolderClassNameEntry));
        int fieldIndex = appendSymbolicField(fieldHolderClassNameEntry, fieldName, fieldType);
        return appendEntry(ConstantPool.Tag.METHODHANDLE, cat(Byte.toUnsignedInt(referenceKind), fieldIndex), cachedMethodHandle);
    }

    public int appendSymbolicMethodHandle(byte referenceKind, Symbol<Name> fieldHolderClassNameEntry, Symbol<Name> fieldName, Symbol<Type> fieldType) {
        return appendSymbolicMethodHandle(referenceKind, fieldHolderClassNameEntry, fieldName, fieldType, null);
    }

    private static int cat(int hi, int lo) {
        assert (short) hi == hi;
        assert (short) lo == lo;
        return (hi << 16) | (lo & 0xFFFF);
    }

    public InterpreterConstantPool build() {
        ParserConstantPool parserConstantPool = buildParserConstantPool();
        Object[] cachedEntries = entries.stream().map(entry -> entry.cachedEntry).toArray(Object[]::new);
        return InterpreterConstantPool.create(holder, parserConstantPool, cachedEntries);
    }

    public ParserConstantPool buildParserConstantPool() {
        int length = entries.size();
        byte[] tags = new byte[length];
        int[] parserEntries = new int[length];
        for (int i = 0; i < length; i++) {
            Entry entry = entries.get(i);
            ConstantPool.Tag tag = entry.tag();
            tags[i] = tag.getValue();
            parserEntries[i] = switch (entry.tag()) {
                case LONG, DOUBLE -> (int) (entry.rawEntry >>> 32);
                default -> {
                    assert entry.rawEntry == (int) entry.rawEntry;
                    yield (int) entry.rawEntry;
                }
            };
        }
        Symbol<?>[] parserSymbols = symbols.isEmpty() ? Symbol.EMPTY_ARRAY : symbols.toArray(Symbol<?>[]::new);
        assert tags.length == 0 || tags[0] == ConstantPool.CONSTANT_Invalid;
        return new ParserConstantPool(tags, parserEntries, parserSymbols, majorVersion, minorVersion);
    }
}
