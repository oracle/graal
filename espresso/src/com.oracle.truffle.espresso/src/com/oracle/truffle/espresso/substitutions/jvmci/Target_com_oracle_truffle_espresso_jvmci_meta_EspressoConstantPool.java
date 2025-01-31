/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.jvmci;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.jvmci.JVMCIIndyData.indyCpi;
import static com.oracle.truffle.espresso.jvmci.JVMCIIndyData.isIndyCPI;
import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.LOGGER;
import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.findObjectType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoConstantReflectionProvider.wrapEspressoObjectConstant;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIObjectType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIUnresolvedType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIField;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIMethod;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.constantpool.BootstrapMethodConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ClassMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DoubleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FloatConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ImmutablePoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.IntegerConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InterfaceMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.LongConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MemberRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.constantpool.CallSiteLink;
import com.oracle.truffle.espresso.constantpool.Resolution;
import com.oracle.truffle.espresso.constantpool.ResolvedDynamicConstant;
import com.oracle.truffle.espresso.constantpool.ResolvedInvokeDynamicConstant;
import com.oracle.truffle.espresso.constantpool.ResolvedWithInvokerClassMethodRefConstant;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.constantpool.SuccessfulCallSiteLink;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jvmci.JVMCIIndyData;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoLinkResolver;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.resolver.CallSiteType;
import com.oracle.truffle.espresso.shared.resolver.FieldAccessType;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoConstantPool {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoConstantPool() {
    }

    @Substitution(hasReceiver = true)
    public static int length(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        return constantPool.length();
    }

    private static RuntimeConstantPool getRuntimeConstantPool(StaticObject self, Meta meta) {
        StaticObject holder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder);
        RuntimeConstantPool pool = klass.getConstantPool();
        assert pool.getHolder() == klass;
        return pool;
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject lookupResolvedField(
                    StaticObject self, int cpi,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject jvmciMethod,
                    int opcode, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(jvmciMethod)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        if (!(opcode == GETSTATIC || opcode == PUTSTATIC || opcode == GETFIELD || opcode == PUTFIELD)) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(jvmciMethod);
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        PoolConstant poolConstant = constantPool.maybeResolvedAt(cpi, meta);
        if (!(poolConstant instanceof FieldRefConstant fieldRef)) {
            throw meta.throwException(meta.java_lang_ClassFormatError);
        }
        Klass symbolicHolder = lookupSymbolicHolder(cpi, constantPool, meta);
        if (symbolicHolder == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedField cannot resolve symbolic holder: " + constantPool.fieldAt(cpi).getHolderKlassName(constantPool));
            return StaticObject.NULL;
        }
        Field resolved = lookupResolvedField(fieldRef, symbolicHolder, constantPool, method, opcode, meta);
        if (resolved == null) {
            return StaticObject.NULL;
        }
        LOGGER.finer(() -> "ECP.lookupResolvedField found " + resolved);
        return toJVMCIField(resolved, cpHolder, cpHolderKlass, meta);
    }

    private static Field lookupResolvedField(FieldRefConstant fieldRef, Klass symbolicHolder, RuntimeConstantPool constantPool, Method method, int opcode, Meta meta) {
        Field symbolicResolution;
        if (fieldRef instanceof Resolvable.ResolvedConstant resolvedConstant) {
            symbolicResolution = (Field) resolvedConstant.value();
        } else {
            FieldRefConstant.Indexes symbolicFieldRef = (FieldRefConstant.Indexes) fieldRef;
            symbolicResolution = tryResolveField(symbolicFieldRef, symbolicHolder, constantPool, meta);
            if (symbolicResolution == null) {
                LOGGER.fine(() -> "ECP.lookupResolvedField failed symbolic lookup for " + symbolicHolder + ", " + symbolicFieldRef.getName(constantPool) + ", " +
                                symbolicFieldRef.getType(constantPool));
                return null;
            }
        }
        // Note that constantPool.getHolder() may be different from method.getDeclaringKlass()
        // in particular this is true in native image where the method might be a JDK method
        // (e.g.,// Ljava/lang/ClassValue;.<init>()V)
        // and the constant pool might be the one of its substitution
        // (e.g., com/oracle/svm/core/jdk/Target_java_lang_ClassValue)
        if (!EspressoLinkResolver.checkFieldAccess(meta.getContext(), symbolicResolution, FieldAccessType.fromOpCode(opcode), constantPool.getHolder(), method)) {
            LOGGER.fine(() -> {
                if (constantPool.getHolder() == method.getDeclaringKlass()) {
                    return "ECP.lookupResolvedField failed access checks for " + symbolicResolution + " from " + method + " with " + Bytecodes.nameOf(opcode);
                } else {
                    return "ECP.lookupResolvedField failed access checks for " + symbolicResolution + " from " + method + " (currentKlass=" + constantPool.getHolder() + ") with " +
                                    Bytecodes.nameOf(opcode);
                }
            });
            return null;
        }
        return symbolicResolution;
    }

    private static Field tryResolveField(FieldRefConstant.Indexes fieldRef, Klass symbolicHolder, RuntimeConstantPool constantPool, Meta meta) {
        Symbol<Name> name = fieldRef.getName(constantPool);
        Symbol<Type> type = fieldRef.getType(constantPool);
        return EspressoLinkResolver.resolveFieldSymbolOrNull(meta.getContext(), constantPool.getHolder(), name, type, symbolicHolder, true, true);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject lookupUtf8(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        PoolConstant poolConstant = constantPool.maybeResolvedAt(cpi, meta);
        if (!(poolConstant instanceof Utf8Constant utf8)) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        LOGGER.finer(() -> "ECP.lookupUtf8 found " + utf8);
        return meta.toGuestString(utf8.unsafeSymbolValue());
    }

    @Substitution(hasReceiver = true, methodName = "lookupType")
    public static @JavaType(internalName = "Ljdk/vm/ci/meta/JavaType;") StaticObject lookupTypeSubst(StaticObject self, int cpi, @SuppressWarnings("unused") int opcode,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        PoolConstant poolConstant = constantPool.maybeResolvedAt(cpi, meta);
        if (poolConstant instanceof ClassConstant classConstant) {
            if (classConstant instanceof Resolvable.ResolvedConstant resolvedConstant) {
                Klass klass = (Klass) resolvedConstant.value();
                LOGGER.finer(() -> "ECP.lookupType found " + klass);
                return toJVMCIObjectType(klass, meta);
            } else {
                return toJVMCIUnresolvedType(TypeSymbols.nameToType(((ClassConstant.ImmutableClassConstant) classConstant).getName(constantPool)), meta);
            }
        }
        if (poolConstant instanceof Utf8Constant utf8Constant) {
            return toJVMCIUnresolvedType(TypeSymbols.nameToType(utf8Constant.unsafeSymbolValue()), meta);
        }
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject lookupResolvedMethod(StaticObject self, int cpi, int opcode,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject callerMirror,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        if (!StaticObject.isNull(callerMirror)) {
            Method caller = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(callerMirror);
            if (caller.getDeclaringKlass() != cpHolderKlass) {
                LOGGER.finer(() -> "ECP.lookupResolvedMethod caller declaring class (" + caller.getDeclaringKlass() + ") doesn't match constant pool holder (" + cpHolderKlass + ")");
            }
        }
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        if (opcode == INVOKEDYNAMIC) {
            LOGGER.finer(() -> "ECP.lookupResolvedMethod resolving indy in CP of %s at cpi=0x%08x".formatted(cpHolderKlass, cpi));
            CallSiteLink callSiteLink = getCallSiteLink(self, cpi, meta);
            if (!(callSiteLink instanceof SuccessfulCallSiteLink successfulCallSiteLink)) {
                LOGGER.fine(() -> "ECP.lookupResolvedMethod no call site link or failed link in CP of %s at cpi=0x%08x".formatted(cpHolderKlass, cpi));
                return StaticObject.NULL;
            }
            Method target = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(successfulCallSiteLink.getMemberName());
            StaticObject holder = toJVMCIInstanceType(target.getDeclaringKlass(), meta);
            return toJVMCIMethod(target, holder, meta);
        }
        PoolConstant poolConstant = constantPool.maybeResolvedAt(cpi, meta);
        if (!Bytecodes.isInvoke(opcode) || !(poolConstant instanceof MethodRefConstant methodRef)) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod opcode=" + Bytecodes.nameOf(opcode) + " poolConstant=" + poolConstant);
            throw meta.throwIllegalArgumentExceptionBoundary("Not an invoke or method ref");
        }
        Klass symbolicHolder = lookupSymbolicHolder(cpi, constantPool, meta);
        if (symbolicHolder == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod couldn't find symbolic holder klass " + constantPool.methodAt(cpi).getHolderKlassName(constantPool));
            return StaticObject.NULL;
        }
        Method symbolicResolution;
        if (methodRef instanceof Resolvable.ResolvedConstant resolvedConstant) {
            symbolicResolution = (Method) resolvedConstant.value();
        } else {
            MethodRefConstant.Indexes symbolicMethodRef = (MethodRefConstant.Indexes) methodRef;
            symbolicResolution = tryResolveMethod(symbolicMethodRef, symbolicHolder, constantPool, meta);
            if (symbolicResolution == null) {
                LOGGER.fine(() -> "ECP.lookupResolvedMethod lookup method failed symbolic lookup for " + symbolicHolder + ", " + symbolicMethodRef.toString(constantPool));
                return StaticObject.NULL;
            }
        }
        ResolvedCall<Klass, Method, Field> resolvedCall = EspressoLinkResolver.resolveCallSiteOrNull(context, cpHolderKlass, symbolicResolution, CallSiteType.fromOpCode(opcode), symbolicHolder);
        if (resolvedCall == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod failed call site resolution for " + symbolicResolution + " from " + cpHolderKlass + " with " + Bytecodes.nameOf(opcode));
            return StaticObject.NULL;
        }
        Method method;
        if (methodRef instanceof ResolvedWithInvokerClassMethodRefConstant withInvoker) {
            MHInvokeGenericNode.MethodHandleInvoker invoker = withInvoker.invoker();
            method = invoker.method();
        } else {
            method = resolvedCall.getResolvedMethod();
        }
        // we don't return the invoker for unresolved InvokeGeneric cases;
        // this seems to be in line with HotSpot
        if (method.isInvokeIntrinsic()) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod lookup method found InvokeGeneric that was not resolved yet: " + method);
        }
        LOGGER.finer(() -> "ECP.lookupResolvedMethod found " + symbolicResolution);
        return toJVMCIMethod(method, cpHolder, cpHolderKlass, meta);
    }

    private static ClassConstant lookupSymbolicHolderConstant(int cpi, RuntimeConstantPool constantPool, Meta meta) {
        return (ClassConstant) constantPool.maybeResolvedAt(constantPool.memberAt(cpi).getHolderIndex(), meta);
    }

    private static Klass lookupSymbolicHolder(int cpi, RuntimeConstantPool constantPool, Meta meta) {
        ClassConstant holderClass = lookupSymbolicHolderConstant(cpi, constantPool, meta);
        return findObjectType(holderClass, constantPool, false, meta);
    }

    private static Method tryResolveMethod(MethodRefConstant.Indexes methodRef, Klass symbolicHolder, RuntimeConstantPool constantPool, Meta meta) {
        Symbol<Name> name = methodRef.getName(constantPool);
        Symbol<Signature> signature = methodRef.getSignature(constantPool);
        return EspressoLinkResolver.resolveMethodSymbolOrNull(meta.getContext(), constantPool.getHolder(), name, signature, symbolicHolder, methodRef instanceof InterfaceMethodRefConstant, true,
                        true);
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject lookupName(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        int index = cpi;
        if (isIndyCPI(index)) {
            index = indyCpi(index);
        }
        ImmutablePoolConstant poolConstant = constantPool.at(index);
        if (poolConstant instanceof MemberRefConstant.Indexes memberRef) {
            LOGGER.finer(() -> "ECP.lookupName found " + memberRef.getName(constantPool));
            return meta.toGuestString(memberRef.getName(constantPool));
        }
        if (poolConstant instanceof InvokeDynamicConstant.Indexes indyConstant) {
            LOGGER.finer(() -> "ECP.lookupName found " + indyConstant.getName(constantPool));
            return meta.toGuestString(indyConstant.getName(constantPool));
        }
        LOGGER.warning(() -> "Unsupported CP entry type for lookupName: " + poolConstant.tag() + " " + poolConstant.getClass());
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject lookupDescriptor(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        int index = cpi;
        if (isIndyCPI(index)) {
            index = indyCpi(index);
        }
        ImmutablePoolConstant poolConstant = constantPool.at(index);
        if (poolConstant instanceof MemberRefConstant.Indexes memberRef) {
            LOGGER.finer(() -> "ECP.lookupDescriptor found " + memberRef.getDescriptor(constantPool));
            return meta.toGuestString(memberRef.getDescriptor(constantPool));
        }
        if (poolConstant instanceof InvokeDynamicConstant.Indexes indyConstant) {
            LOGGER.finer(() -> "ECP.lookupDescriptor found " + indyConstant.getDescriptor(constantPool));
            return meta.toGuestString(indyConstant.getDescriptor(constantPool));
        }
        LOGGER.warning(() -> "Unsupported CP entry type for lookupDescriptor: " + poolConstant.tag() + " " + poolConstant.getClass());
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "Ljdk/vm/ci/meta/JavaType;") StaticObject lookupReferencedType(StaticObject self, int cpi, @SuppressWarnings("unused") int opcode,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        ImmutablePoolConstant poolConstant = constantPool.at(cpi);
        switch (opcode) {
            case Bytecodes.CHECKCAST:
            case Bytecodes.INSTANCEOF:
            case Bytecodes.NEW:
            case Bytecodes.ANEWARRAY:
            case Bytecodes.MULTIANEWARRAY:
            case Bytecodes.LDC:
            case Bytecodes.LDC_W:
            case Bytecodes.LDC2_W:
                if (poolConstant instanceof ClassConstant.ImmutableClassConstant classConstant) {
                    if (classConstant instanceof Resolvable.ResolvedConstant resolvedConstant) {
                        Klass klass = (Klass) resolvedConstant.value();
                        LOGGER.finer(() -> "ECP.lookupReferencedType found " + klass);
                        return toJVMCIObjectType(klass, meta);
                    } else {
                        return toJVMCIUnresolvedType(TypeSymbols.nameToType(classConstant.getName(constantPool)), meta);
                    }
                }
                break;
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTSTATIC:
            case Bytecodes.GETFIELD:
            case Bytecodes.PUTFIELD:
            case Bytecodes.INVOKEVIRTUAL:
            case Bytecodes.INVOKESPECIAL:
            case Bytecodes.INVOKESTATIC:
            case Bytecodes.INVOKEINTERFACE:
                if (poolConstant instanceof MemberRefConstant.Indexes memberRef) {
                    ClassConstant holderClass = (ClassConstant) constantPool.maybeResolvedAt(memberRef.getHolderIndex(), meta);
                    Klass holderKlass = findObjectType(holderClass, constantPool, false, meta);
                    if (holderKlass != null) {
                        LOGGER.finer(() -> "ECP.lookupReferencedType found " + holderKlass);
                        return toJVMCIObjectType(holderKlass, meta);
                    } else {
                        Symbol<Name> holderName = memberRef.getHolderKlassName(constantPool);
                        return toJVMCIUnresolvedType(TypeSymbols.nameToType(holderName), meta);
                    }
                }
                break;
        }
        LOGGER.warning(() -> "Unsupported CP entry type for lookupReferencedType: " + poolConstant.tag() + " " + poolConstant.getClass() + " for " + Bytecodes.nameOf(opcode));
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static boolean loadReferencedType0(StaticObject self, int cpi, int opcode,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        switch (opcode) {
            case Bytecodes.CHECKCAST:
            case Bytecodes.INSTANCEOF:
            case Bytecodes.NEW:
            case Bytecodes.ANEWARRAY:
            case Bytecodes.MULTIANEWARRAY: {
                Klass klass = constantPool.resolvedKlassAt(cpHolderKlass, cpi);
                LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") found " + klass);
                return true;
            }
            case Bytecodes.LDC:
            case Bytecodes.LDC_W:
            case Bytecodes.LDC2_W: {
                if (constantPool.at(cpi) instanceof ClassConstant) {
                    Klass klass = constantPool.resolvedKlassAt(cpHolderKlass, cpi);
                    LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") found " + klass);
                    return true;
                }
                return false;
            }
            case Bytecodes.INVOKEDYNAMIC: {
                // resolve this indy and call boostrap method
                assert isIndyCPI(cpi);
                JVMCIIndyData indyData = JVMCIIndyData.getExisting(cpHolderKlass, meta);
                LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") Looking up CallSiteLink for index=" + cpi + " in " + cpHolderKlass);
                JVMCIIndyData.Location location = indyData.getLocation(cpi);
                assert location != null;
                int indyCpi = indyCpi(cpi);
                PoolConstant poolConstant = constantPool.maybeResolvedAt(indyCpi, meta);
                if (!(poolConstant instanceof InvokeDynamicConstant)) {
                    throw meta.throwIllegalArgumentExceptionBoundary();
                }
                constantPool.linkInvokeDynamic(cpHolderKlass, indyCpi, location.method(), location.bci());
                return false;
            }
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTSTATIC:
            case Bytecodes.GETFIELD:
            case Bytecodes.PUTFIELD:
            case Bytecodes.INVOKEVIRTUAL:
            case Bytecodes.INVOKESPECIAL:
            case Bytecodes.INVOKESTATIC:
            case Bytecodes.INVOKEINTERFACE: {
                MemberRefConstant.Indexes memberRef = constantPool.memberAt(cpi);
                Klass klass = constantPool.resolvedKlassAt(cpHolderKlass, memberRef.getHolderIndex());
                LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") found " + klass);
                if ((opcode == INVOKEVIRTUAL || opcode == INVOKESPECIAL) && Meta.isSignaturePolymorphicHolderType(klass.getType())) {
                    if (!(memberRef instanceof Resolvable.ResolvedConstant)) {
                        Symbol<Name> methodName = memberRef.getName(constantPool);
                        if (MethodHandleIntrinsics.getId(methodName, klass) != MethodHandleIntrinsics.PolySigIntrinsics.None) {
                            // trigger resolution for method handle intrinsics
                            Method method = constantPool.resolvedMethodAt(cpHolderKlass, cpi);
                            LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") resolved MH intrinsic to " + method);
                        }
                    }
                }
                return true;
            }
            default:
                return false;
        }
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(Object.class) StaticObject lookupConstant(StaticObject self, int cpi, boolean resolve, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        PoolConstant poolConstant = constantPool.maybeResolvedAt(cpi, meta);
        if (resolve && !(poolConstant instanceof Resolvable.ResolvedConstant) && poolConstant instanceof Resolvable) {
            poolConstant = constantPool.resolvedAt(cpHolderKlass, cpi, null);
        }
        if (poolConstant instanceof IntegerConstant integerConstant) {
            return meta.jvmci.boxInt(integerConstant.value());
        }
        if (poolConstant instanceof LongConstant longConstant) {
            return meta.jvmci.boxLong(longConstant.value());
        }
        if (poolConstant instanceof FloatConstant floatConstant) {
            return meta.jvmci.boxFloat(floatConstant.value());
        }
        if (poolConstant instanceof DoubleConstant doubleConstant) {
            return meta.jvmci.boxDouble(doubleConstant.value());
        }
        if (poolConstant instanceof ClassConstant classConstant) {
            if (classConstant instanceof Resolvable.ResolvedConstant resolvedConstant) {
                Klass klass = (Klass) resolvedConstant.value();
                LOGGER.finer(() -> "ECP.lookupConstant found " + klass);
                return toJVMCIObjectType(klass, meta);
            } else if (resolve) {
                throw EspressoError.shouldNotReachHere();
            } else {
                return toJVMCIUnresolvedType(TypeSymbols.nameToType(((ClassConstant.ImmutableClassConstant) classConstant).getName(constantPool)), meta);
            }
        }
        if (poolConstant instanceof StringConstant) {
            return wrapEspressoObjectConstant(constantPool.resolvedStringAt(cpi), meta);
        }
        if (poolConstant instanceof MethodHandleConstant methodHandleConstant) {
            if (methodHandleConstant instanceof Resolvable.ResolvedConstant resolvedConstant) {
                return wrapEspressoObjectConstant((StaticObject) resolvedConstant.value(), meta);
            } else if (resolve) {
                throw EspressoError.shouldNotReachHere();
            } else {
                return StaticObject.NULL;
            }
        }
        if (poolConstant instanceof MethodTypeConstant methodTypeConstant) {
            if (methodTypeConstant instanceof Resolvable.ResolvedConstant resolvedConstant) {
                return wrapEspressoObjectConstant((StaticObject) resolvedConstant.value(), meta);
            } else if (resolve) {
                throw EspressoError.shouldNotReachHere();
            } else {
                return StaticObject.NULL;
            }
        }
        if (poolConstant instanceof DynamicConstant dynamicConstant) {
            if (dynamicConstant instanceof ResolvedDynamicConstant resolvedConstant) {
                JavaKind kind = resolvedConstant.getKind();
                if (kind.isStackInt()) {
                    char typeChar = switch (kind) {
                        case Boolean -> 'Z';
                        case Byte -> 'B';
                        case Char -> 'C';
                        case Short -> 'S';
                        case Int -> 'I';
                        default -> throw EspressoError.shouldNotReachHere(kind.toString());
                    };
                    return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic(typeChar, (long) (Integer) resolvedConstant.value());
                }
                switch (kind) {
                    case Long:
                        return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic('J', resolvedConstant.value());
                    case Float:
                        return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic('F', (long) Float.floatToRawIntBits((float) resolvedConstant.value()));
                    case Double:
                        return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic('D', Double.doubleToRawLongBits((double) resolvedConstant.value()));
                    case Object:
                        return wrapEspressoObjectConstant((StaticObject) resolvedConstant.value(), meta);
                    case Illegal:
                        return meta.jvmci.JavaConstant_ILLEGAL.getObject(meta.jvmci.JavaConstant.tryInitializeAndGetStatics());
                    default:
                        throw EspressoError.shouldNotReachHere(kind.toString());
                }
            } else if (resolve) {
                throw EspressoError.shouldNotReachHere();
            } else {
                return StaticObject.NULL;
            }
        }
        var finalPoolConstant = poolConstant;
        LOGGER.warning(() -> "Unsupported CP entry type for lookupConstant: " + finalPoolConstant.tag() + " " + finalPoolConstant.getClass());
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "Ljdk/vm/ci/meta/JavaConstant;") StaticObject lookupAppendix(StaticObject self, int index, int opcode, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (opcode != INVOKEDYNAMIC && opcode != INVOKEVIRTUAL) {
            throw meta.throwIllegalArgumentExceptionBoundary("Expected INVOKEDYNAMIC or INVOKEVIRTUAL");
        }
        if (opcode == INVOKEDYNAMIC) {
            LOGGER.finer(() -> "ECP.lookupAppendix: Looking up CallSiteLink for index=" + Integer.toHexString(index) + " in " +
                            meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(meta.jvmci.EspressoConstantPool_holder.getObject(self)));
            CallSiteLink callSiteLink = getCallSiteLink(self, index, meta);
            if (!(callSiteLink instanceof SuccessfulCallSiteLink successfulCallSiteLink)) {
                return StaticObject.NULL;
            }
            return wrapEspressoObjectConstant(successfulCallSiteLink.getUnboxedAppendix(), meta);
        } else {
            assert opcode == INVOKEVIRTUAL;
            StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
            ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
            RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
            PoolConstant poolConstant = constantPool.maybeResolvedAt(index, meta);
            if (!(poolConstant instanceof ClassMethodRefConstant methodRef)) {
                throw meta.throwIllegalArgumentExceptionBoundary("The index does not reference a MethodRef");
            }
            if (!(methodRef instanceof ResolvedWithInvokerClassMethodRefConstant withInvoker)) {
                return StaticObject.NULL;
            }
            MHInvokeGenericNode.MethodHandleInvoker invoker = withInvoker.invoker();
            assert invoker != null;
            return wrapEspressoObjectConstant(invoker.appendix(), meta);
        }
    }

    private static CallSiteLink getCallSiteLink(StaticObject self, int index, Meta meta) {
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        JVMCIIndyData indyData = JVMCIIndyData.getExisting(cpHolderKlass, meta);
        JVMCIIndyData.Location location = indyData.getLocation(index);
        assert isIndyCPI(index);
        int cpi = indyCpi(index);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        PoolConstant poolConstant = constantPool.maybeResolvedAt(cpi, meta);
        if (!(poolConstant instanceof InvokeDynamicConstant)) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        if (!(poolConstant instanceof ResolvedInvokeDynamicConstant resolvedIndy)) {
            return null;
        }
        return resolvedIndy.getCallSiteLink(location.method(), location.bci());
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoBootstrapMethodInvocation;") StaticObject lookupBootstrapMethodInvocation(StaticObject self, int cpi,
                    @SuppressWarnings("unused") int opcode,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        int index = cpi;
        if (isIndyCPI(index)) {
            index = indyCpi(index);
        }
        ImmutablePoolConstant poolConstant = constantPool.at(index);
        if (poolConstant instanceof BootstrapMethodConstant.Indexes bsmConstant) {
            BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) cpHolderKlass.getAttribute(BootstrapMethodsAttribute.NAME);
            BootstrapMethodsAttribute.Entry bsmEntry = bms.at(bsmConstant.getBootstrapMethodAttrIndex());
            StaticObject methodHandle = constantPool.getMethodHandle(bsmEntry, cpHolderKlass);
            methodHandle = (StaticObject) meta.java_lang_invoke_MethodHandle_asFixedArity.invokeDirectVirtual(methodHandle);
            assert meta.java_lang_invoke_DirectMethodHandle.isAssignableFrom(methodHandle.getKlass());
            StaticObject member = meta.java_lang_invoke_DirectMethodHandle_member.getObject(methodHandle);

            boolean isIndy = poolConstant.tag() == ConstantPool.Tag.INVOKEDYNAMIC;
            Method bootstrapMethod = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(member);
            Symbol<Name> name = bsmConstant.getName(constantPool);
            StaticObject type;
            if (isIndy) {
                Symbol<Signature> invokeSignature = SignatureSymbols.fromDescriptor(bsmConstant.getDescriptor(constantPool));
                Symbol<Type>[] parsedInvokeSignature = meta.getSignatures().parsed(invokeSignature);
                type = Resolution.signatureToMethodType(parsedInvokeSignature, cpHolderKlass, meta.getContext().getJavaVersion().java8OrEarlier(), meta);
            } else {
                Symbol<Type> typeSymbol = TypeSymbols.fromSymbol(bsmConstant.getDescriptor(constantPool));
                Klass klass = meta.resolveSymbolOrFail(typeSymbol, cpHolderKlass.getDefiningClassLoader(), cpHolderKlass.protectionDomain());
                type = klass.mirror();
            }
            StaticObject wrappedArgs = meta.jvmci.JavaConstant.allocateReferenceArray(bsmEntry.numBootstrapArguments());
            StaticObject[] unwrappedArgs = wrappedArgs.unwrap(meta.getLanguage());
            for (int i = 0; i < bsmEntry.numBootstrapArguments(); i++) {
                char entryCPI = bsmEntry.argAt(i);
                PoolConstant pc = constantPool.maybeResolvedAt(entryCPI, meta);
                if (pc instanceof DynamicConstant dynConstant) {
                    if (dynConstant instanceof ResolvedDynamicConstant resolvedDynamicConstant) {
                        unwrappedArgs[i] = resolvedDynamicConstant.guestBoxedValue(meta);
                    } else {
                        unwrappedArgs[i] = meta.jvmci.boxInt(entryCPI);
                    }
                } else {
                    StaticObject obj = switch (constantPool.tagAt(entryCPI)) {
                        case METHODHANDLE -> constantPool.resolvedMethodHandleAt(cpHolderKlass, entryCPI);
                        case METHODTYPE -> constantPool.resolvedMethodTypeAt(cpHolderKlass, entryCPI);
                        case CLASS -> constantPool.resolvedKlassAt(cpHolderKlass, entryCPI).mirror();
                        case STRING -> constantPool.resolvedStringAt(entryCPI);
                        case INTEGER -> meta.boxInteger(constantPool.intAt(entryCPI));
                        case LONG -> meta.boxLong(constantPool.longAt(entryCPI));
                        case DOUBLE -> meta.boxDouble(constantPool.doubleAt(entryCPI));
                        case FLOAT -> meta.boxFloat(constantPool.floatAt(entryCPI));
                        default -> throw EspressoError.shouldNotReachHere(pc.tag().toString());
                    };
                    unwrappedArgs[i] = wrapEspressoObjectConstant(obj, meta);
                }
            }

            StaticObject methodHolderMirror;
            if (bootstrapMethod.getDeclaringKlass() == cpHolderKlass) {
                methodHolderMirror = cpHolder;
            } else {
                methodHolderMirror = toJVMCIInstanceType(bootstrapMethod.getDeclaringKlass(), meta);
            }
            StaticObject methodMirror = toJVMCIMethod(bootstrapMethod, methodHolderMirror, meta);
            StaticObject wrappedType = wrapEspressoObjectConstant(type, meta);

            StaticObject result = meta.jvmci.EspressoBootstrapMethodInvocation.allocateInstance(context);
            meta.jvmci.EspressoBootstrapMethodInvocation_init.invokeDirectSpecial(result, isIndy, methodMirror, meta.toGuestString(name), wrappedType, wrappedArgs);
            return result;
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static byte getTagByteAt(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool runtimeConstantPool = getRuntimeConstantPool(self, meta);
        return runtimeConstantPool.tagAt(cpi).getValue();
    }
}
