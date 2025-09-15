/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.jvmci;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIIndyData.indyCpi;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIIndyData.isIndyCPI;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIUtils.LOGGER;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIUtils.findObjectType;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Descriptor;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.constantpool.CallSiteLink;
import com.oracle.truffle.espresso.constantpool.ResolvedConstant;
import com.oracle.truffle.espresso.constantpool.ResolvedDynamicConstant;
import com.oracle.truffle.espresso.constantpool.ResolvedInvokeDynamicConstant;
import com.oracle.truffle.espresso.constantpool.ResolvedWithInvokerClassMethodRefConstant;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.constantpool.SuccessfulCallSiteLink;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoLinkResolver;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.SignaturePolymorphicIntrinsic;
import com.oracle.truffle.espresso.shared.resolver.CallSiteType;
import com.oracle.truffle.espresso.shared.resolver.FieldAccessType;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;

/**
 * This class contains methods that are used by both internal and external JVMCI to implement
 * JVMCI's constant pool accesses.
 */
public final class JVMCIConstantPoolUtils {
    private JVMCIConstantPoolUtils() {
    }

    public static ConstantPool.Tag safeTagAt(RuntimeConstantPool pool, int cpi, Meta meta) {
        if (cpi < 0 || pool.length() <= cpi) {
            throw meta.throwIndexOutOfBoundsExceptionBoundary("Invalid constant pool index", cpi, pool.length());
        }
        return pool.tagAt(cpi);
    }

    @TruffleBoundary
    public static boolean loadReferencedType0(int cpi, int opcode, RuntimeConstantPool constantPool, ObjectKlass cpHolderKlass, Meta meta) {
        switch (opcode) {
            case CHECKCAST:
            case INSTANCEOF:
            case NEW:
            case ANEWARRAY:
            case MULTIANEWARRAY: {
                Klass klass = constantPool.resolvedKlassAt(cpHolderKlass, cpi);
                LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") found " + klass);
                return true;
            }
            case LDC:
            case LDC_W:
            case LDC2_W: {
                if (safeTagAt(constantPool, cpi, meta) == ConstantPool.Tag.CLASS) {
                    Klass klass = constantPool.resolvedKlassAt(cpHolderKlass, cpi);
                    LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") found " + klass);
                    return true;
                }
                return false;
            }
            case INVOKEDYNAMIC: {
                // resolve this indy and call boostrap method
                assert isIndyCPI(cpi);
                JVMCIIndyData indyData = JVMCIIndyData.getExisting(cpHolderKlass, meta);
                LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") Looking up CallSiteLink for index=" + cpi + " in " + cpHolderKlass);
                JVMCIIndyData.Location location = indyData.getLocation(cpi);
                assert location != null;
                int indyCpi = indyCpi(cpi);
                if (!(safeTagAt(constantPool, indyCpi, meta) == ConstantPool.Tag.INVOKEDYNAMIC)) {
                    throw meta.throwIllegalArgumentExceptionBoundary();
                }
                constantPool.linkInvokeDynamic(cpHolderKlass, indyCpi, location.method(), location.bci());
                return false;
            }
            case GETSTATIC:
            case PUTSTATIC:
            case GETFIELD:
            case PUTFIELD:
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                Klass klass = constantPool.resolvedKlassAt(cpHolderKlass, constantPool.memberClassIndex(cpi));
                LOGGER.finer(() -> "ECP.loadReferencedType0(" + Bytecodes.nameOf(opcode) + ") found " + klass);
                if ((opcode == INVOKEVIRTUAL || opcode == INVOKESPECIAL) && ParserKlass.isSignaturePolymorphicHolderType(klass.getType())) {
                    ResolvedConstant resolvedConstant = constantPool.peekResolvedOrNull(cpi, meta);
                    if (resolvedConstant == null) {
                        Symbol<Name> methodName = constantPool.memberName(cpi);
                        if (SignaturePolymorphicIntrinsic.getId(methodName, klass) != null) {
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

    @TruffleBoundary
    public static Method lookupResolvedMethod(RuntimeConstantPool constantPool, int cpi, int opcode, Method caller, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass cpHolderKlass = constantPool.getHolder();
        if (caller != null && caller.getDeclaringKlass() != cpHolderKlass) {
            LOGGER.finer(() -> "ECP.lookupResolvedMethod caller declaring class (" + caller.getDeclaringKlass() + ") doesn't match constant pool holder (" + cpHolderKlass + ")");
        }
        if (opcode == INVOKEDYNAMIC) {
            LOGGER.finer(() -> "ECP.lookupResolvedMethod resolving indy in CP of %s at cpi=0x%08x".formatted(cpHolderKlass, cpi));
            CallSiteLink callSiteLink = getCallSiteLink(cpHolderKlass, cpi, meta);
            if (!(callSiteLink instanceof SuccessfulCallSiteLink successfulCallSiteLink)) {
                LOGGER.fine(() -> "ECP.lookupResolvedMethod no call site link or failed link in CP of %s at cpi=0x%08x".formatted(cpHolderKlass, cpi));
                return null;
            }
            return (Method) meta.HIDDEN_VMTARGET.getHiddenObject(successfulCallSiteLink.getMemberName());
        }
        if (!Bytecodes.isInvoke(opcode) || !(safeTagAt(constantPool, cpi, meta).isMethod())) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod opcode=" + Bytecodes.nameOf(opcode) + " poolConstant=" + constantPool.toString(cpi));
            throw meta.throwIllegalArgumentExceptionBoundary("Not an invoke or method ref");
        }
        Klass symbolicHolder = lookupSymbolicHolder(cpi, constantPool, meta);
        if (symbolicHolder == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod couldn't find symbolic holder klass " + constantPool.memberClassName(cpi));
            return null;
        }
        Method symbolicResolution;
        ResolvedConstant resolvedConstantOrNull = constantPool.peekResolvedOrNull(cpi, meta);
        if (resolvedConstantOrNull != null) {
            symbolicResolution = (Method) resolvedConstantOrNull.value();
        } else {
            symbolicResolution = tryResolveMethod(cpi, symbolicHolder, constantPool, meta);
            if (symbolicResolution == null) {
                LOGGER.fine(() -> "ECP.lookupResolvedMethod lookup method failed symbolic lookup for " + symbolicHolder + ", " + constantPool.toString(cpi));
                return null;
            }
        }
        ResolvedCall<Klass, Method, Field> resolvedCall = EspressoLinkResolver.resolveCallSiteOrNull(context, cpHolderKlass, symbolicResolution, CallSiteType.fromOpCode(opcode), symbolicHolder);
        if (resolvedCall == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod failed call site resolution for " + symbolicResolution + " from " + cpHolderKlass + " with " + Bytecodes.nameOf(opcode));
            return null;
        }
        Method method;
        if (resolvedConstantOrNull instanceof ResolvedWithInvokerClassMethodRefConstant withInvoker) {
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
        return method;
    }

    private static Klass lookupSymbolicHolder(int cpi, RuntimeConstantPool constantPool, Meta meta) {
        int holderClassIndex = constantPool.memberClassIndex(cpi);
        return findObjectType(holderClassIndex, constantPool, false, true, meta);
    }

    private static Method tryResolveMethod(int methodIndex, Klass symbolicHolder, RuntimeConstantPool constantPool, Meta meta) {
        Symbol<Name> name = constantPool.methodName(methodIndex);
        Symbol<Signature> signature = constantPool.methodSignature(methodIndex);
        ConstantPool.Tag tag = safeTagAt(constantPool, methodIndex, meta);
        return EspressoLinkResolver.resolveMethodSymbolOrNull(meta.getContext(), constantPool.getHolder(), name, signature, symbolicHolder, tag == ConstantPool.Tag.INTERFACE_METHOD_REF, true, true);
    }

    private static CallSiteLink getCallSiteLink(ObjectKlass cpHolderKlass, int index, Meta meta) {
        JVMCIIndyData indyData = JVMCIIndyData.getExisting(cpHolderKlass, meta);
        JVMCIIndyData.Location location = indyData.getLocation(index);
        assert isIndyCPI(index);
        int cpi = indyCpi(index);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        ResolvedConstant resolvedConstantOrNull = constantPool.peekResolvedOrNull(cpi, meta);
        if (safeTagAt(constantPool, cpi, meta) != ConstantPool.Tag.INVOKEDYNAMIC) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        if (!(resolvedConstantOrNull instanceof ResolvedInvokeDynamicConstant resolvedIndy)) {
            return null;
        }
        return resolvedIndy.getCallSiteLink(location.method(), location.bci());
    }

    @TruffleBoundary
    public static Field lookupResolvedField(RuntimeConstantPool constantPool, int cpi, Method method, int opcode, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        if (!(opcode == GETSTATIC || opcode == PUTSTATIC || opcode == GETFIELD || opcode == PUTFIELD)) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        if (safeTagAt(constantPool, cpi, meta) != ConstantPool.Tag.FIELD_REF) {
            throw meta.throwException(meta.java_lang_ClassFormatError);
        }
        Klass symbolicHolder = lookupSymbolicHolder(cpi, constantPool, meta);
        if (symbolicHolder == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedField cannot resolve symbolic holder: " + constantPool.memberClassName(cpi));
            return null;
        }
        Field resolved = lookupResolvedField(cpi, symbolicHolder, constantPool, method, opcode, meta);
        if (resolved != null) {
            LOGGER.finer(() -> "ECP.lookupResolvedField found " + resolved);
        }
        return resolved;
    }

    private static Field lookupResolvedField(int fieldIndex, Klass symbolicHolder, RuntimeConstantPool constantPool, Method method, int opcode, Meta meta) {
        Field symbolicResolution;
        ResolvedConstant resolvedConstant = constantPool.peekResolvedOrNull(fieldIndex, meta);
        if (resolvedConstant != null) {
            symbolicResolution = (Field) resolvedConstant.value();
        } else {
            symbolicResolution = tryResolveField(fieldIndex, symbolicHolder, constantPool, meta);
            if (symbolicResolution == null) {
                LOGGER.fine(() -> "ECP.lookupResolvedField failed symbolic lookup for " + symbolicHolder + ", " + constantPool.fieldName(fieldIndex) + ", " +
                                constantPool.fieldType(fieldIndex));
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

    private static Field tryResolveField(int fieldIndex, Klass symbolicHolder, RuntimeConstantPool constantPool, Meta meta) {
        Symbol<Name> name = constantPool.fieldName(fieldIndex);
        Symbol<Type> type = constantPool.fieldType(fieldIndex);
        return EspressoLinkResolver.resolveFieldSymbolOrNull(meta.getContext(), constantPool.getHolder(), name, type, symbolicHolder, true, true);
    }

    @TruffleBoundary
    public static Symbol<? extends Descriptor> lookupDescriptor(RuntimeConstantPool constantPool, int cpi, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        int index = isIndyCPI(cpi) ? indyCpi(cpi) : cpi;
        ConstantPool.Tag tag = safeTagAt(constantPool, index, meta);
        if (tag.isMember()) {
            LOGGER.finer(() -> "ECP.lookupDescriptor found " + constantPool.memberDescriptor(index));
            return constantPool.memberDescriptor(index);
        }
        if (tag == ConstantPool.Tag.INVOKEDYNAMIC) {
            Symbol<Signature> indySignature = constantPool.invokeDynamicSignature(index);
            LOGGER.finer(() -> "ECP.lookupDescriptor found " + indySignature);
            return indySignature;
        }
        LOGGER.warning(() -> "Unsupported CP entry type for lookupDescriptor: " + tag);
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @TruffleBoundary
    public static Symbol<Name> lookupName(RuntimeConstantPool constantPool, int cpi, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        int index = isIndyCPI(cpi) ? indyCpi(cpi) : cpi;
        ConstantPool.Tag tag = safeTagAt(constantPool, index, meta);
        if (tag.isMember()) {
            LOGGER.finer(() -> "ECP.lookupName found " + constantPool.memberName(index));
            return constantPool.memberName(index);
        }
        if (tag == ConstantPool.Tag.INVOKEDYNAMIC) {
            LOGGER.finer(() -> "ECP.lookupName found " + constantPool.invokeDynamicName(index));
            return constantPool.invokeDynamicName(index);
        }
        LOGGER.warning(() -> "Unsupported CP entry type for lookupName: " + tag + " " + constantPool.toString(index));
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @TruffleBoundary
    public static StaticObject lookupAppendix(RuntimeConstantPool constantPool, int index, int opcode, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        if (opcode != INVOKEDYNAMIC && opcode != INVOKEVIRTUAL) {
            throw meta.throwIllegalArgumentExceptionBoundary("Expected INVOKEDYNAMIC or INVOKEVIRTUAL");
        }
        if (opcode == INVOKEDYNAMIC) {
            LOGGER.finer(() -> "ECP.lookupAppendix: Looking up CallSiteLink for index=" + Integer.toHexString(index) + " in " + constantPool.getHolder());
            CallSiteLink callSiteLink = getCallSiteLink(constantPool.getHolder(), index, meta);
            if (!(callSiteLink instanceof SuccessfulCallSiteLink successfulCallSiteLink)) {
                return StaticObject.NULL;
            }
            StaticObject appendix = successfulCallSiteLink.getUnboxedAppendix();
            assert StaticObject.notNull(appendix);
            return appendix;
        } else {
            assert opcode == INVOKEVIRTUAL;
            ResolvedConstant resolvedConstant = constantPool.peekResolvedOrNull(index, meta);
            if (safeTagAt(constantPool, index, meta) != ConstantPool.Tag.METHOD_REF) {
                throw meta.throwIllegalArgumentExceptionBoundary("The index does not reference a MethodRef");
            }
            if (!(resolvedConstant instanceof ResolvedWithInvokerClassMethodRefConstant withInvoker)) {
                return StaticObject.NULL;
            }
            MHInvokeGenericNode.MethodHandleInvoker invoker = withInvoker.invoker();
            assert invoker != null;
            assert StaticObject.notNull(invoker.appendix());
            return invoker.appendix();
        }
    }

    @TruffleBoundary
    public static Object lookupReferencedType(RuntimeConstantPool constantPool, int cpi, @SuppressWarnings("unused") int opcode, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        int classCpi;
        switch (opcode) {
            case CHECKCAST:
            case INSTANCEOF:
            case NEW:
            case ANEWARRAY:
            case MULTIANEWARRAY:
            case LDC:
            case LDC_W:
            case LDC2_W:
                if (safeTagAt(constantPool, cpi, meta) != ConstantPool.Tag.CLASS) {
                    throw meta.throwIllegalArgumentExceptionBoundary("Opcode and constant pool entry types mismatch");
                }
                classCpi = cpi;
                break;
            case GETSTATIC:
            case PUTSTATIC:
            case GETFIELD:
            case PUTFIELD:
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE:
                if (!safeTagAt(constantPool, cpi, meta).isMember()) {
                    throw meta.throwIllegalArgumentExceptionBoundary("Opcode and constant pool entry types mismatch");
                }
                classCpi = constantPool.memberClassIndex(cpi);
                break;
            default:
                LOGGER.warning(() -> "Unsupported CP entry type for lookupReferencedType: " + safeTagAt(constantPool, cpi, meta) + " " + constantPool.toString(cpi) + " for " +
                                Bytecodes.nameOf(opcode));
                throw meta.throwIllegalArgumentExceptionBoundary("Unsupported CP entry type");
        }
        Klass klass;
        try {
            klass = findObjectType(classCpi, constantPool, false, true, meta);
        } catch (EspressoException e) {
            throw EspressoError.shouldNotReachHere("findObjectType with resolve=false should never throw", e);
        }
        if (klass == null) {
            Symbol<Name> className = constantPool.className(classCpi);
            return TypeSymbols.nameToType(className);
        }
        LOGGER.finer(() -> "ECP.lookupReferencedType found " + klass);
        return klass;
    }

    @TruffleBoundary
    public static void lookupBootstrapMethodInvocation(RuntimeConstantPool constantPool, int cpi, int opcode, EspressoContext context, BootstrapMethodInvocationBuilder builder) {
        Meta meta = context.getMeta();
        int index;
        if (opcode == -1) {
            assert !isIndyCPI(cpi);
            index = cpi;
        } else if (opcode == INVOKEDYNAMIC) {
            assert isIndyCPI(cpi);
            index = indyCpi(cpi);
        } else {
            throw meta.throwIllegalArgumentExceptionBoundary("Unexpected opcode: " + opcode);
        }
        ObjectKlass cpHolderKlass = constantPool.getHolder();
        ConstantPool.Tag tag = safeTagAt(constantPool, index, meta);
        if (tag != ConstantPool.Tag.DYNAMIC && tag != ConstantPool.Tag.INVOKEDYNAMIC) {
            return;
        }
        BootstrapMethodsAttribute bms = cpHolderKlass.getAttribute(BootstrapMethodsAttribute.NAME, BootstrapMethodsAttribute.class);
        int bsmAttrIndex = constantPool.bsmBootstrapMethodAttrIndex(index);
        BootstrapMethodsAttribute.Entry bsmEntry = bms.at(bsmAttrIndex);
        StaticObject methodHandle = constantPool.getMethodHandle(bsmEntry, cpHolderKlass);
        methodHandle = (StaticObject) meta.java_lang_invoke_MethodHandle_asFixedArity.invokeDirectVirtual(methodHandle);
        assert meta.java_lang_invoke_DirectMethodHandle.isAssignableFrom(methodHandle.getKlass());
        StaticObject member = meta.java_lang_invoke_DirectMethodHandle_member.getObject(methodHandle);

        boolean isIndy = tag == ConstantPool.Tag.INVOKEDYNAMIC;
        Method bootstrapMethod = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(member);
        Symbol<Name> name = constantPool.bsmName(index);
        StaticObject type;
        if (isIndy) {
            Symbol<Signature> invokeSignature = SignatureSymbols.fromDescriptor(constantPool.invokeDynamicSignature(index));
            Symbol<Type>[] parsedInvokeSignature = meta.getSignatures().parsed(invokeSignature);
            type = RuntimeConstantPool.signatureToMethodType(parsedInvokeSignature, cpHolderKlass, meta.getContext().getJavaVersion().java8OrEarlier(), meta);
        } else {
            Symbol<Type> typeSymbol = TypeSymbols.fromSymbol(constantPool.dynamicType(index));
            Klass klass = meta.resolveSymbolOrFail(typeSymbol, cpHolderKlass.getDefiningClassLoader(), cpHolderKlass.protectionDomain());
            type = klass.mirror();
        }
        builder.setupStaticArguments(bsmEntry.numBootstrapArguments());
        for (int i = 0; i < bsmEntry.numBootstrapArguments(); i++) {
            char entryCPI = bsmEntry.argAt(i);
            ConstantPool.Tag entryTag = safeTagAt(constantPool, entryCPI, meta);
            if (entryTag == ConstantPool.Tag.DYNAMIC) {
                ResolvedConstant resolvedConstant = constantPool.peekResolvedOrNull(entryCPI, meta);
                if (resolvedConstant instanceof ResolvedDynamicConstant resolvedDynamicConstant) {
                    builder.staticArgument(i, resolvedDynamicConstant.guestBoxedValue(meta));
                } else {
                    builder.staticArgumentUnresolvedDynamic(i, entryCPI);
                }
            } else {
                StaticObject obj = switch (entryTag) {
                    case METHODHANDLE -> constantPool.resolvedMethodHandleAt(cpHolderKlass, entryCPI);
                    case METHODTYPE -> constantPool.resolvedMethodTypeAt(cpHolderKlass, entryCPI);
                    case CLASS -> constantPool.resolvedKlassAt(cpHolderKlass, entryCPI).mirror();
                    case STRING -> constantPool.resolvedStringAt(entryCPI);
                    case INTEGER -> meta.boxInteger(constantPool.intAt(entryCPI));
                    case LONG -> meta.boxLong(constantPool.longAt(entryCPI));
                    case DOUBLE -> meta.boxDouble(constantPool.doubleAt(entryCPI));
                    case FLOAT -> meta.boxFloat(constantPool.floatAt(entryCPI));
                    default -> throw EspressoError.shouldNotReachHere(entryTag.toString());
                };
                builder.staticArgument(i, obj);
            }
        }
        builder.finalize(isIndy, bootstrapMethod, name, type);
    }

    public interface BootstrapMethodInvocationBuilder {
        void setupStaticArguments(int length);

        void staticArgument(int i, StaticObject value);

        void staticArgumentUnresolvedDynamic(int i, int cpi);

        void finalize(boolean isIndy, Method bootstrapMethod, Symbol<Name> name, StaticObject type);
    }
}
