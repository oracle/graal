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
package com.oracle.svm.interpreter;

import java.lang.invoke.MethodType;

import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.interpreter.metadata.CremaResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;

import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class RuntimeInterpreterConstantPool extends InterpreterConstantPool {

    public RuntimeInterpreterConstantPool(InterpreterResolvedObjectType holder, ParserKlass parserKlass) {
        super(holder, parserKlass.getConstantPool());
        // pre-resolve the holder class so that hidden classes can resolve themselves
        cachedEntries[parserKlass.getThisKlassIndex()] = holder;
    }

    @Override
    public RuntimeException classFormatError(String message) {
        throw SemanticJavaException.raise(new ClassFormatError(message));
    }

    @Override
    protected Object resolve(int cpi, InterpreterResolvedObjectType accessingClass) {
        Tag tag = tagAt(cpi);
        return switch (tag) {
            case STRING -> resolveStringConstant(cpi, accessingClass);
            case FIELD_REF -> resolveFieldRefConstant(cpi, accessingClass);
            case INTERFACE_METHOD_REF -> resolveInterfaceMethodRefConstant(cpi, accessingClass);
            case METHOD_REF -> resolveClassMethodRefConstant(cpi, accessingClass);
            case CLASS -> resolveClassConstant(cpi, accessingClass);
            case METHODTYPE -> resolveMethodType(cpi, accessingClass);
            case METHODHANDLE -> resolveMethodHandle(cpi, accessingClass);
            case INVOKEDYNAMIC -> resolveInvokeDynamic(cpi, accessingClass);
            default -> throw VMError.unimplemented("Unimplemented CP resolution for " + tag);
        };
    }

    private Object resolveInvokeDynamic(int cpi, InterpreterResolvedObjectType accessingClass) {
        BootstrapMethodsAttribute bms = ((CremaResolvedObjectType) accessingClass).getBootstrapMethodsAttribute();
        int bootstrapMethodAttrIndex = this.invokeDynamicBootstrapMethodAttrIndex(cpi);
        BootstrapMethodsAttribute.Entry bsEntry = bms.at(bootstrapMethodAttrIndex);

        Symbol<Signature> invokeSignature = this.invokeDynamicSignature(cpi);
        Symbol<Type>[] parsedInvokeSignature = SymbolsSupport.getSignatures().parsed(invokeSignature);

        return new ResolvedInvokeDynamicConstant(bsEntry, parsedInvokeSignature, this.invokeDynamicName(cpi));
    }

    private Object resolveMethodHandle(int cpi, InterpreterResolvedObjectType accessingClass) {
        Object mtype;
        InterpreterResolvedJavaType mklass;
        Symbol<Name> refName;

        int memberIndex = this.methodHandleMemberIndex(cpi);

        Tag refTag = this.tagAt(memberIndex);
        if (refTag == Tag.METHOD_REF || refTag == Tag.INTERFACE_METHOD_REF) {
            InterpreterResolvedJavaMethod target = this.resolvedMethodAt(accessingClass, memberIndex);
            Symbol<Type>[] parsed = target.getParsedSymbolicSignature(CremaRuntimeAccess.getInstance().getSymbolPool());

            mtype = signatureToMethodType(parsed, accessingClass);
            /*
             * we should use the klass from the method ref here rather than the declaring klass of
             * the target this is because the resolved target might come from a default method and
             * have an interface as declaring klass however if the refKind is invokeVirtual, it
             * would be illegal to use the interface type
             */
            int holderIndex = this.memberClassIndex(memberIndex);
            mklass = this.resolvedTypeAt(accessingClass, holderIndex);
            refName = target.getSymbolicName();
        } else {
            assert refTag == Tag.FIELD_REF;
            InterpreterResolvedJavaField field = this.resolvedFieldAt(accessingClass, memberIndex);
            mtype = field.getResolvedType().getJavaClass();
            mklass = field.getDeclaringClass();
            refName = field.getSymbolicName();
        }
        String mname = refName.toString();
        int refKind = this.methodHandleRefKind(cpi);
        return Target_java_lang_invoke_MethodHandleNatives.linkMethodHandleConstant(
                        accessingClass.getJavaClass(), refKind,
                        mklass.getJavaClass(), mname, mtype);
    }

    private Object resolveMethodType(int cpi, InterpreterResolvedObjectType accessingClass) {
        Symbol<Signature> sig = this.methodTypeSignature(cpi);
        Symbol<Type>[] parsed = SymbolsSupport.getSignatures().parsed(sig);
        return signatureToMethodType(parsed, accessingClass);
    }

    private String resolveStringConstant(int stringIndex, @SuppressWarnings("unused") InterpreterResolvedObjectType accessingKlass) {
        int utf8Index = this.stringUtf8Index(stringIndex);
        String string = this.utf8At(utf8Index).toString().intern(); // intern?
        return string;
    }

    private InterpreterResolvedJavaType resolveClassConstant(int classIndex, InterpreterResolvedJavaType accessingKlass) {
        assert accessingKlass != null;
        assert tagAt(classIndex) == Tag.CLASS;

        Object entry = this.cachedEntries[classIndex];
        Symbol<Type> type = null;

        if (entry == null) {
            // CP comes from dynamically loaded .class file.
            Symbol<Name> className = this.className(classIndex);
            type = SymbolsSupport.getTypes().fromClassNameEntry(className);
        } else if (entry instanceof UnresolvedJavaType unresolvedJavaType) {
            Throwable cause = unresolvedJavaType.getCause();
            if (cause != null) {
                throw uncheckedThrow(cause);
            }
            // CP comes from build-time JVMCI type, derive type from UnresolvedJavaType.
            type = SymbolsSupport.getTypes().getOrCreateValidType(unresolvedJavaType.getName());
        } else {
            throw VMError.shouldNotReachHere("Invalid cached CP entry, expected unresolved type, but got " + entry);
        }

        assert type != null;

        try {
            InterpreterResolvedObjectType result = CremaRuntimeAccess.getInstance().lookupOrLoadType(type, accessingKlass);
            return result;
        } catch (LinkageError e) {
            // Comment from Hotspot:
            // Just throw the exception and don't prevent these classes from being loaded for
            // virtual machine errors like StackOverflow and OutOfMemoryError, etc.
            // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
            this.cachedEntries[classIndex] = UnresolvedJavaType.create(type.toString(), e);
            throw e;
        }
    }

    private InterpreterResolvedJavaField resolveFieldRefConstant(int fieldIndex, InterpreterResolvedObjectType accessingClass) {
        assert accessingClass != null;
        assert tagAt(fieldIndex) == Tag.FIELD_REF;

        Object entry = this.cachedEntries[fieldIndex];

        Symbol<Name> fieldName = null;
        Symbol<Type> fieldType = null;
        InterpreterResolvedJavaType holder = null;

        if (entry == null) {
            // CP comes from dynamically loaded .class file.
            fieldName = this.fieldName(fieldIndex);
            fieldType = this.fieldType(fieldIndex);
            int memberClassIndex = this.memberClassIndex(fieldIndex);
            holder = (InterpreterResolvedJavaType) resolvedAt(memberClassIndex, accessingClass);
        } else if (entry instanceof UnresolvedJavaField unresolvedJavaField) {
            Throwable cause = unresolvedJavaField.getCause();
            if (cause != null) {
                throw uncheckedThrow(cause);
            }
            // CP comes from build-time JVMCI type, derive it from UnresolvedJavaField.
            fieldName = SymbolsSupport.getNames().getOrCreate(unresolvedJavaField.getName());
            fieldType = SymbolsSupport.getTypes().getOrCreateValidType(unresolvedJavaField.getType().getName());
            Symbol<Type> holderType = SymbolsSupport.getTypes().getOrCreateValidType(unresolvedJavaField.getDeclaringClass().getName());
            assert !TypeSymbols.isPrimitive(holderType) && !TypeSymbols.isArray(holderType);
            // Perf. note: The holder is re-resolved every-time (never cached).
            holder = CremaRuntimeAccess.getInstance().lookupOrLoadType(holderType, accessingClass);
        } else {
            throw VMError.shouldNotReachHere("Invalid cached CP entry, expected unresolved field, but got " + entry);
        }

        // TODO(peterssen): Enable access checks and loading constraints.
        InterpreterResolvedJavaField result = CremaLinkResolver.resolveFieldSymbolOrThrow(CremaRuntimeAccess.getInstance(), accessingClass, fieldName, fieldType, holder, false, false);
        return result;
    }

    private InterpreterResolvedJavaMethod resolveClassMethodRefConstant(int methodIndex, InterpreterResolvedObjectType accessingClass) {
        assert accessingClass != null;
        assert tagAt(methodIndex) == Tag.METHOD_REF;

        Object entry = this.cachedEntries[methodIndex];

        Symbol<Name> methodName = null;
        Symbol<Signature> methodSignature = null;
        InterpreterResolvedJavaType holder = null;

        if (entry == null) {
            // CP comes from dynamically loaded .class file.
            methodName = this.methodName(methodIndex);
            methodSignature = this.methodSignature(methodIndex);
            int memberClassIndex = this.memberClassIndex(methodIndex);
            holder = (InterpreterResolvedJavaType) resolvedAt(memberClassIndex, accessingClass);
        } else if (entry instanceof UnresolvedJavaMethod unresolvedJavaMethod) {
            Throwable cause = unresolvedJavaMethod.getCause();
            if (cause != null) {
                throw uncheckedThrow(cause);
            }
            // CP comes from build-time JVMCI type, derive it from UnresolvedJavaMethod.
            methodName = SymbolsSupport.getNames().getOrCreate(unresolvedJavaMethod.getName());
            methodSignature = SymbolsSupport.getSignatures().getOrCreateValidSignature(unresolvedJavaMethod.getSignature().toMethodDescriptor());
            Symbol<Type> holderType = SymbolsSupport.getTypes().getOrCreateValidType(unresolvedJavaMethod.getDeclaringClass().getName());
            // Perf. note: The holder is re-resolved every-time (never cached).
            holder = CremaRuntimeAccess.getInstance().lookupOrLoadType(holderType, accessingClass);
        } else {
            throw VMError.shouldNotReachHere("Invalid cached CP entry, expected unresolved method, but got " + entry);
        }

        // TODO(peterssen): Enable access checks and loading constraints.
        InterpreterResolvedJavaMethod classMethod = CremaLinkResolver.resolveMethodSymbol(CremaRuntimeAccess.getInstance(), accessingClass, methodName, methodSignature, holder, false, false, false);

        // TODO(peterssen): Support MethodHandle invoke intrinsics.

        return classMethod;
    }

    private InterpreterResolvedJavaMethod resolveInterfaceMethodRefConstant(int interfaceMethodIndex, InterpreterResolvedObjectType accessingClass) {
        assert tagAt(interfaceMethodIndex) == Tag.INTERFACE_METHOD_REF;

        Object entry = this.cachedEntries[interfaceMethodIndex];

        Symbol<Name> methodName = null;
        Symbol<Signature> methodSignature = null;
        InterpreterResolvedJavaType holder = null;

        if (entry == null) {
            // CP comes from dynamically loaded .class file.
            methodName = this.methodName(interfaceMethodIndex);
            methodSignature = this.methodSignature(interfaceMethodIndex);
            int memberClassIndex = this.memberClassIndex(interfaceMethodIndex);
            holder = (InterpreterResolvedJavaType) resolvedAt(memberClassIndex, accessingClass);
        } else if (entry instanceof UnresolvedJavaMethod unresolvedJavaMethod) {
            Throwable cause = unresolvedJavaMethod.getCause();
            if (cause != null) {
                throw uncheckedThrow(cause);
            }
            // CP comes from build-time JVMCI type, derive it from UnresolvedJavaMethod.
            methodName = SymbolsSupport.getNames().getOrCreate(unresolvedJavaMethod.getName());
            methodSignature = SymbolsSupport.getSignatures().getOrCreateValidSignature(unresolvedJavaMethod.getSignature().toMethodDescriptor());
            Symbol<Type> holderType = SymbolsSupport.getTypes().getOrCreateValidType(unresolvedJavaMethod.getDeclaringClass().getName());
            // Perf. note: The holder is re-resolved every-time (never cached).
            holder = CremaRuntimeAccess.getInstance().lookupOrLoadType(holderType, accessingClass);
        } else {
            throw VMError.shouldNotReachHere("Invalid cached CP entry, expected unresolved method, but got " + entry);
        }

        // TODO(peterssen): Enable access checks and loading constraints.
        InterpreterResolvedJavaMethod interfaceMethod = CremaLinkResolver.resolveMethodSymbol(CremaRuntimeAccess.getInstance(), accessingClass, methodName, methodSignature, holder, true, false,
                        false);

        // TODO(peterssen): Support MethodHandle invoke intrinsics.

        return interfaceMethod;
    }

    public Object[] getStaticArguments(BootstrapMethodsAttribute.Entry entry, InterpreterResolvedObjectType accessingClass) {
        Object[] args = new Object[entry.numBootstrapArguments()];
        for (int i = 0; i < entry.numBootstrapArguments(); i++) {
            args[i] = switch (tagAt(entry.argAt(i))) {
                case METHODHANDLE -> this.resolvedMethodHandleAt(entry.argAt(i), accessingClass);
                case METHODTYPE -> this.resolvedMethodTypeAt(entry.argAt(i), accessingClass);
                case CLASS -> this.resolveClassConstant(entry.argAt(i), accessingClass).getJavaClass();
                case STRING -> this.resolveStringAt(entry.argAt(i));
                case INTEGER -> this.intAt(entry.argAt(i));
                case LONG -> this.longAt(entry.argAt(i));
                case DOUBLE -> this.doubleAt(entry.argAt(i));
                case FLOAT -> this.floatAt(entry.argAt(i));
                default -> throw VMError.unimplemented("Unimplemented CP resolution for " + tagAt(entry.argAt(i)));
            };
        }
        return args;
    }

    public static MethodType signatureToMethodType(Symbol<Type>[] signature, InterpreterResolvedObjectType accessingClass) {
        Symbol<Type> rt = SignatureSymbols.returnType(signature);
        int pcount = SignatureSymbols.parameterCount(signature);
        Class<?>[] ptypes = new Class<?>[pcount];
        Class<?> rtype;
        for (int i = 0; i < pcount; i++) {
            Symbol<Type> paramType = SignatureSymbols.parameterType(signature, i);
            ptypes[i] = resolveSymbolAndAccessCheck(accessingClass, paramType);
        }
        rtype = resolveSymbolAndAccessCheck(accessingClass, rt);

        return Target_java_lang_invoke_MethodHandleNatives.findMethodHandleType(rtype, ptypes);
    }

    private static Class<?> resolveSymbolAndAccessCheck(InterpreterResolvedObjectType accessingClass, Symbol<Type> type) {
        Class<?> clazz = CremaSupport.singleton().resolveOrThrow(type, accessingClass);
        // GR-62339 check access
        return clazz;
    }
}
