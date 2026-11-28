/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIUtils.findType;

import java.lang.invoke.MethodHandleInfo;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
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

    public static int lookupMethodHandleReferenceKind(RuntimeConstantPool pool, int cpi, Meta meta) {
        checkMethodHandle(pool, cpi, meta);
        return pool.methodHandleRefKind(cpi);
    }

    public static int lookupMethodHandleReferenceIndex(RuntimeConstantPool pool, int cpi, Meta meta) {
        checkMethodHandle(pool, cpi, meta);
        return pool.methodHandleMemberIndex(cpi);
    }

    private static void checkMethodHandle(RuntimeConstantPool pool, int cpi, Meta meta) {
        if (cpi < 1 || cpi >= pool.length()) {
            throw meta.throwIndexOutOfBoundsExceptionBoundary("Invalid constant pool index", cpi, pool.length());
        }
        if (pool.tagAt(cpi) != ConstantPool.Tag.METHODHANDLE) {
            throw meta.throwIllegalArgumentExceptionBoundary("Expected a method handle constant pool index");
        }
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
            return (Method) meta.java_lang_invoke_MemberName_0vmTarget.getHiddenObject(successfulCallSiteLink.getMemberName());
        }
        int invokeOpcode;
        int methodCpi;
        boolean returnInvoker;
        if (opcode == -1 || opcode == LDC || opcode == LDC_W) {
            invokeOpcode = switch (lookupMethodHandleReferenceKind(constantPool, cpi, meta)) {
                case MethodHandleInfo.REF_invokeVirtual -> INVOKEVIRTUAL;
                case MethodHandleInfo.REF_invokeStatic -> INVOKESTATIC;
                case MethodHandleInfo.REF_invokeSpecial, MethodHandleInfo.REF_newInvokeSpecial -> INVOKESPECIAL;
                case MethodHandleInfo.REF_invokeInterface -> INVOKEINTERFACE;
                default -> throw meta.throwIllegalArgumentExceptionBoundary("Expected a method handle referencing a method");
            };
            methodCpi = constantPool.methodHandleMemberIndex(cpi);
            returnInvoker = false;
        } else if (Bytecodes.isInvoke(opcode)) {
            invokeOpcode = opcode;
            methodCpi = cpi;
            returnInvoker = true;
        } else {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid opcode " + opcode);
        }
        if (!safeTagAt(constantPool, methodCpi, meta).isMethod()) {
            throw meta.throwIllegalArgumentExceptionBoundary("Not a method ref");
        }
        Klass symbolicHolder = lookupSymbolicHolder(methodCpi, constantPool, meta);
        if (symbolicHolder == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod couldn't find symbolic holder klass " + constantPool.memberClassName(methodCpi));
            return null;
        }
        Method symbolicResolution;
        ResolvedConstant resolvedConstantOrNull = constantPool.peekResolvedOrNull(methodCpi, meta);
        if (resolvedConstantOrNull != null && resolvedConstantOrNull.isSuccess()) {
            symbolicResolution = (Method) resolvedConstantOrNull.value();
        } else {
            symbolicResolution = tryResolveMethod(methodCpi, symbolicHolder, constantPool, meta);
            if (symbolicResolution == null) {
                LOGGER.fine(() -> "ECP.lookupResolvedMethod lookup method failed symbolic lookup for " + symbolicHolder + ", " + constantPool.toString(methodCpi));
                return null;
            }
        }
        ResolvedCall<Klass, Method, Field> resolvedCall = EspressoLinkResolver.resolveCallSiteOrNull(context, cpHolderKlass, symbolicResolution, CallSiteType.fromOpCode(invokeOpcode), symbolicHolder);
        if (resolvedCall == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod failed call site resolution for " + symbolicResolution + " from " + cpHolderKlass + " with " + Bytecodes.nameOf(invokeOpcode));
            return null;
        }
        Method method;
        if (returnInvoker && resolvedConstantOrNull instanceof ResolvedWithInvokerClassMethodRefConstant withInvoker) {
            MHInvokeGenericNode.MethodHandleInvoker invoker = withInvoker.invoker();
            method = invoker.method();
        } else {
            method = resolvedCall.getResolvedMethod();
        }
        // we don't return the invoker for unresolved InvokeGeneric cases;
        // this seems to be in line with HotSpot
        if (method.isInvokeIntrinsic()) {
            LOGGER.fine(() -> "ECP.lookupResolvedMethod lookup method found InvokeGeneric that was not resolved yet: " + method);
            return null;
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
        int fieldIndex;
        int fieldOpcode;
        if (opcode == -1 || opcode == LDC || opcode == LDC_W) {
            fieldOpcode = switch (lookupMethodHandleReferenceKind(constantPool, cpi, meta)) {
                case MethodHandleInfo.REF_getField -> GETFIELD;
                case MethodHandleInfo.REF_getStatic -> GETSTATIC;
                case MethodHandleInfo.REF_putField -> PUTFIELD;
                case MethodHandleInfo.REF_putStatic -> PUTSTATIC;
                default -> throw meta.throwIllegalArgumentExceptionBoundary("Expected a method handle referencing a field");
            };
            fieldIndex = constantPool.methodHandleMemberIndex(cpi);
        } else if (opcode == GETSTATIC || opcode == PUTSTATIC || opcode == GETFIELD || opcode == PUTFIELD) {
            fieldIndex = cpi;
            fieldOpcode = opcode;
        } else {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        if (safeTagAt(constantPool, fieldIndex, meta) != ConstantPool.Tag.FIELD_REF) {
            throw meta.throwException(meta.java_lang_ClassFormatError);
        }
        Klass symbolicHolder = lookupSymbolicHolder(fieldIndex, constantPool, meta);
        if (symbolicHolder == null) {
            LOGGER.fine(() -> "ECP.lookupResolvedField cannot resolve symbolic holder: " + constantPool.memberClassName(fieldIndex));
            return null;
        }
        Field resolved = lookupResolvedField(fieldIndex, symbolicHolder, constantPool, method, fieldOpcode, meta);
        if (resolved != null) {
            LOGGER.finer(() -> "ECP.lookupResolvedField found " + resolved);
        }
        return resolved;
    }

    private static Field lookupResolvedField(int fieldIndex, Klass symbolicHolder, RuntimeConstantPool constantPool, Method method, int opcode, Meta meta) {
        Field symbolicResolution;
        ResolvedConstant resolvedConstant = constantPool.peekResolvedOrNull(fieldIndex, meta);
        if (resolvedConstant != null && resolvedConstant.isSuccess()) {
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
    public static Symbol<?> lookupDescriptor(RuntimeConstantPool constantPool, int cpi, EspressoContext context) {
        Meta meta = context.getMeta();
        int index = isIndyCPI(cpi) ? indyCpi(cpi) : cpi;
        ConstantPool.Tag tag = safeTagAt(constantPool, index, meta);
        if (tag == ConstantPool.Tag.METHODHANDLE) {
            index = constantPool.methodHandleMemberIndex(index);
            tag = safeTagAt(constantPool, index, meta);
        }
        if (tag.isMember()) {
            return constantPool.memberDescriptor(index);
        }
        return switch (tag) {
            case UTF8 -> constantPool.utf8At(index);
            case METHODTYPE -> constantPool.methodTypeSignature(index);
            case INVOKEDYNAMIC -> constantPool.invokeDynamicSignature(index);
            case DYNAMIC -> constantPool.dynamicType(index);
            default -> throw meta.throwIllegalArgumentExceptionBoundary("Entry has no descriptor");
        };
    }

    @TruffleBoundary
    public static Symbol<Name> lookupName(RuntimeConstantPool constantPool, int cpi, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        int index = isIndyCPI(cpi) ? indyCpi(cpi) : cpi;
        ConstantPool.Tag tag = safeTagAt(constantPool, index, meta);
        if (tag == ConstantPool.Tag.METHODHANDLE) {
            index = constantPool.methodHandleMemberIndex(index);
            tag = safeTagAt(constantPool, index, meta);
        }
        if (tag.isMember()) {
            return constantPool.memberName(index);
        }
        if (tag == ConstantPool.Tag.INVOKEDYNAMIC || tag == ConstantPool.Tag.DYNAMIC) {
            return constantPool.bsmName(index);
        }
        throw meta.throwIllegalArgumentExceptionBoundary("Entry has no name");
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
    public static Object lookupReferencedType(RuntimeConstantPool constantPool, int rawIndex, int rawOpcode, EspressoContext context) {
        assert context.getLanguage().isJVMCIEnabled();
        Meta meta = context.getMeta();
        int cpi = rawIndex;
        int opcode = rawOpcode;
        int classCpi;
        ConstantPool.Tag entryTag = safeTagAt(constantPool, cpi, meta);
        if (entryTag == ConstantPool.Tag.DYNAMIC) {
            Symbol<Type> type = constantPool.dynamicType(cpi);
            Klass klass = findType(type, constantPool.getHolder(), false, false, meta);
            return klass == null ? type : klass;
        }
        if (entryTag == ConstantPool.Tag.METHODHANDLE && (opcode == LDC || opcode == LDC_W)) {
            opcode = -1;
        }
        switch (opcode) {
            case -1:
                ConstantPool.Tag tag = safeTagAt(constantPool, cpi, meta);
                if (tag == ConstantPool.Tag.METHODHANDLE) {
                    cpi = constantPool.methodHandleMemberIndex(cpi);
                    tag = safeTagAt(constantPool, cpi, meta);
                }
                if (tag == ConstantPool.Tag.CLASS) {
                    classCpi = cpi;
                } else if (tag.isMember()) {
                    classCpi = constantPool.memberClassIndex(cpi);
                } else {
                    throw meta.throwIllegalArgumentExceptionBoundary("Entry has no referenced type");
                }
                break;
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
        return klass;
    }

    @TruffleBoundary
    public static void lookupBootstrapMethodInvocation(RuntimeConstantPool constantPool, int cpi, int opcode, boolean resolve, EspressoContext context,
                    BootstrapMethodInvocationBuilder builder) {
        Meta meta = context.getMeta();
        int index;
        if (opcode == -1 || opcode == LDC || opcode == LDC_W || opcode == LDC2_W) {
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
        boolean isIndy = tag == ConstantPool.Tag.INVOKEDYNAMIC;
        BootstrapMethodsAttribute bms = cpHolderKlass.getAttribute(BootstrapMethodsAttribute.NAME, BootstrapMethodsAttribute.class);
        int bsmAttrIndex = constantPool.bsmBootstrapMethodAttrIndex(index);
        BootstrapMethodsAttribute.Entry bsmEntry = bms.at(bsmAttrIndex);
        int handleIndex = bsmEntry.getBootstrapMethodRef();
        int[] argumentIndices = new int[bsmEntry.numBootstrapArguments()];
        for (int i = 0; i < argumentIndices.length; i++) {
            argumentIndices[i] = bsmEntry.argAt(i);
        }
        StaticObject type = StaticObject.NULL;
        if (resolve) {
            constantPool.getMethodHandle(bsmEntry, cpHolderKlass);
            if (isIndy) {
                Symbol<Type>[] signature = meta.getSignatures().parsed(constantPool.invokeDynamicSignature(index));
                type = RuntimeConstantPool.signatureToMethodType(signature, cpHolderKlass, meta.getContext().getJavaVersion().java8OrEarlier(), meta);
            } else {
                Klass klass = meta.resolveSymbolOrFail(constantPool.dynamicType(index), cpHolderKlass.getDefiningClassLoader(), cpHolderKlass.protectionDomain());
                type = klass.mirror();
            }
        }
        int referenceKind = lookupMethodHandleReferenceKind(constantPool, handleIndex, meta);
        Method method = null;
        if (referenceKind >= MethodHandleInfo.REF_invokeVirtual) {
            method = lookupResolvedMethod(constantPool, handleIndex, -1, null, context);
        }
        if (resolve) {
            builder.setupStaticArguments(argumentIndices.length);
            for (int i = 0; i < argumentIndices.length; i++) {
                int argumentIndex = argumentIndices[i];
                ConstantPool.Tag argumentTag = safeTagAt(constantPool, argumentIndex, meta);
                if (argumentTag == ConstantPool.Tag.DYNAMIC) {
                    ResolvedConstant entry = constantPool.peekResolvedOrNull(argumentIndex, meta);
                    if (entry instanceof ResolvedDynamicConstant dynamic && dynamic.isSuccess()) {
                        builder.staticArgument(i, dynamic.guestBoxedValue(meta));
                    } else {
                        builder.staticArgumentUnresolvedDynamic(i, argumentIndex);
                    }
                } else {
                    StaticObject value = switch (argumentTag) {
                        case METHODHANDLE -> constantPool.resolvedMethodHandleAt(cpHolderKlass, argumentIndex);
                        case METHODTYPE -> constantPool.resolvedMethodTypeAt(cpHolderKlass, argumentIndex);
                        case CLASS -> constantPool.resolvedKlassAt(cpHolderKlass, argumentIndex).mirror();
                        case STRING -> constantPool.resolvedStringAt(argumentIndex);
                        case INTEGER -> meta.boxInteger(constantPool.intAt(argumentIndex));
                        case LONG -> meta.boxLong(constantPool.longAt(argumentIndex));
                        case DOUBLE -> meta.boxDouble(constantPool.doubleAt(argumentIndex));
                        case FLOAT -> meta.boxFloat(constantPool.floatAt(argumentIndex));
                        default -> throw EspressoError.shouldNotReachHere(argumentTag.toString());
                    };
                    builder.staticArgument(i, value);
                }
            }
        }
        builder.finalize(isIndy, method, constantPool.bsmName(index), type, handleIndex, argumentIndices);
    }

    public interface BootstrapMethodInvocationBuilder {
        void setupStaticArguments(int length);

        void staticArgument(int i, StaticObject value);

        void staticArgumentUnresolvedDynamic(int i, int cpi);

        void finalize(boolean isIndy, Method bootstrapMethod, Symbol<Name> name, StaticObject type, int methodHandleIndex, int[] staticArgumentIndices);
    }
}
