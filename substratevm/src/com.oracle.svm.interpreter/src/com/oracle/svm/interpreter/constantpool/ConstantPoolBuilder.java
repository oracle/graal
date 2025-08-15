/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.svm.interpreter.constantpool;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.descriptors.Descriptor;
import com.oracle.svm.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
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

public final class ConstantPoolBuilder {

    private final Map<Symbol<?>, Integer> symbolIndex = new HashMap<>();
    private final List<Symbol<?>> symbols = new ArrayList<>();

    // INVALID entries are not cached here.
    private final Map<Entry, Integer> entryIndex = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();

    private final InterpreterResolvedObjectType holder;
    private final int majorVersion;
    private final int minorVersion;

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

    private int appendRawEntry(ConstantPool.Tag tag, long rawEntry, Object cachedEntry) {
        assert !(tag != ConstantPool.Tag.DOUBLE && tag != ConstantPool.Tag.LONG) || (rawEntry == (int) rawEntry);

        Entry newEntry = new Entry(tag, rawEntry, cachedEntry);
        if (tag == ConstantPool.Tag.INVALID) {
            // INVALID entries are never cached.
            int cpi = entries.size();
            entries.add(newEntry);
            return cpi;
        } else {
            return entryIndex.computeIfAbsent(newEntry, key -> {
                int cpi = entries.size();
                entries.add(newEntry); // First entry contains full value.
                if (tag == ConstantPool.Tag.LONG || tag == ConstantPool.Tag.DOUBLE) {
                    // Two slot entry, append dummy INVALID with lower half.
                    entries.add(new Entry(ConstantPool.Tag.INVALID, (int) rawEntry, null));
                }
                return cpi;
            });
        }
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

    public int appendSymbolicType(Symbol<Type> typeSymbol, JavaType cachedType) {
        return appendEntry(ConstantPool.Tag.CLASS, appendSymbolicUTF8(typeSymbol), cachedType);
    }

    public int appendSymbolicType(Symbol<Type> typeSymbol) {
        return appendSymbolicType(typeSymbol, null);
    }

    public int appendSymbolicField(Symbol<Type> fieldHolder, Symbol<Name> fieldName, Symbol<Type> fieldType, JavaField cachedField) {
        assert cachedField == null || cachedField instanceof InterpreterResolvedJavaField || cachedField instanceof UnresolvedJavaField;
        int classIndex = appendSymbolicType(fieldHolder);
        int nameAndTypeIndex = appendNameAndType(fieldName, fieldType);
        return appendEntry(ConstantPool.Tag.FIELD_REF, cat(classIndex, nameAndTypeIndex), cachedField);
    }

    public int appendSymbolicField(Symbol<Type> fieldHolder, Symbol<Name> fieldName, Symbol<Type> fieldType) {
        return appendSymbolicField(fieldHolder, fieldName, fieldType, null);
    }

    public int appendSymbolicMethod(boolean interfaceMethod, Symbol<Type> methodHolder, Symbol<Name> methodName, Symbol<Signature> methodSignature, JavaMethod cachedMethod) {
        assert cachedMethod == null || cachedMethod instanceof InterpreterResolvedJavaMethod || cachedMethod instanceof UnresolvedJavaMethod;
        int classIndex = appendSymbolicType(methodHolder);
        int nameAndTypeIndex = appendNameAndType(methodName, methodSignature);
        return appendEntry(
                        interfaceMethod ? ConstantPool.Tag.INTERFACE_METHOD_REF : ConstantPool.Tag.METHOD_REF,
                        cat(classIndex, nameAndTypeIndex),
                        cachedMethod);
    }

    public int appendSymbolicMethod(boolean interfaceMethod, Symbol<Type> methodHolder, Symbol<Name> methodName, Symbol<Signature> methodSignature) {
        return appendSymbolicMethod(interfaceMethod, methodHolder, methodName, methodSignature, null);
    }

    private int appendNameAndType(Symbol<Name> name, Symbol<? extends Descriptor> descriptor) {
        return appendEntry(ConstantPool.Tag.NAME_AND_TYPE, cat(appendSymbolicUTF8(name), appendSymbolicUTF8(descriptor)), null);
    }

    public int appendSymbolicMethodType(Symbol<Signature> methodSignature, MethodType cachedMethodType) {
        return appendEntry(ConstantPool.Tag.METHODTYPE, appendSymbolicUTF8(methodSignature), cachedMethodType);
    }

    public int appendSymbolicMethodType(Symbol<Signature> methodSignature) {
        return appendSymbolicMethodType(methodSignature, null);
    }

    private static int cat(int hi, int lo) {
        assert (short) hi == hi;
        assert (short) lo == lo;
        return (hi << 16) | (lo & 0xFFFF);
    }
}
