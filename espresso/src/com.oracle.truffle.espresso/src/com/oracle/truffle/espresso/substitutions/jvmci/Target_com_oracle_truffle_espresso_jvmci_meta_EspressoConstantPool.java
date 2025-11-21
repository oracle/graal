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

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIConstantPoolUtils.safeTagAt;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIUtils.LOGGER;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoConstantReflectionProvider.wrapEspressoObjectConstant;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIObjectType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIUnresolvedType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIField;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIMethod;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.constantpool.ResolvedConstant;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.jvmci.JVMCIConstantPoolUtils;
import com.oracle.truffle.espresso.impl.jvmci.JVMCIIndyData;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
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
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject lookupResolvedField(
                    StaticObject self, int cpi,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject jvmciMethod,
                    int opcode, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(jvmciMethod)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(jvmciMethod);
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        Field resolved = JVMCIConstantPoolUtils.lookupResolvedField(constantPool, cpi, method, opcode, context);
        if (resolved == null) {
            return StaticObject.NULL;
        }
        return toJVMCIField(resolved, cpHolder, cpHolderKlass, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject lookupUtf8(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        if (safeTagAt(constantPool, cpi, meta) != ConstantPool.Tag.UTF8) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        LOGGER.finer(() -> "ECP.lookupUtf8 found " + constantPool.toString(cpi));
        return meta.toGuestString(constantPool.utf8At(cpi));
    }

    @Substitution(hasReceiver = true, methodName = "lookupType")
    public static @JavaType(internalName = "Ljdk/vm/ci/meta/JavaType;") StaticObject lookupTypeSubst(StaticObject self, int cpi,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        if (safeTagAt(constantPool, cpi, meta) == ConstantPool.Tag.CLASS) {
            ResolvedConstant resolvedConstant = constantPool.peekResolvedOrNull(cpi, meta);
            return resolvedConstantToJVMCIObjectType(resolvedConstant, constantPool, cpi, meta);
        }
        if (safeTagAt(constantPool, cpi, meta) == ConstantPool.Tag.UTF8) {
            return toJVMCIUnresolvedType(TypeSymbols.nameToType(constantPool.utf8At(cpi)), meta);
        }
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    private static StaticObject resolvedConstantToJVMCIObjectType(ResolvedConstant resolvedConstant, RuntimeConstantPool constantPool, int cpi, Meta meta) {
        if (resolvedConstant == null || !resolvedConstant.isSuccess()) {
            return toJVMCIUnresolvedType(TypeSymbols.nameToType(constantPool.className(cpi)), meta);
        }
        Klass klass = (Klass) resolvedConstant.value();
        return toJVMCIObjectType(klass, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject lookupResolvedMethod(StaticObject self, int cpi, int opcode,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject callerMirror,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        Method caller = null;
        if (!StaticObject.isNull(callerMirror)) {
            caller = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(callerMirror);
        }
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        Method result = JVMCIConstantPoolUtils.lookupResolvedMethod(constantPool, cpi, opcode, caller, context);
        if (result == null) {
            return StaticObject.NULL;
        }
        return toJVMCIMethod(result, cpHolder, cpHolderKlass, meta);
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject lookupName(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        return meta.toGuestString(JVMCIConstantPoolUtils.lookupName(constantPool, cpi, context));
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject lookupDescriptor(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        return meta.toGuestString(JVMCIConstantPoolUtils.lookupDescriptor(constantPool, cpi, context));
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

        Object result = JVMCIConstantPoolUtils.lookupReferencedType(constantPool, cpi, opcode, context);
        if (result instanceof Klass klass) {
            return toJVMCIObjectType(klass, meta);
        } else {
            ByteSequence type = (ByteSequence) result;
            return toJVMCIUnresolvedType(type, meta);
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean loadReferencedType0(StaticObject self, int cpi, int opcode,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        return JVMCIConstantPoolUtils.loadReferencedType0(cpi, opcode, constantPool, cpHolderKlass, meta);
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(Object.class) StaticObject lookupConstant(StaticObject self, int cpi, boolean resolve, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();

        ConstantPool.Tag tag = safeTagAt(constantPool, cpi, meta);
        if (tag.isPrimitive()) {
            // There's no CP resolution for primitives, they can be read directly.
            return switch (tag) {
                case INTEGER -> meta.jvmci.boxInt(constantPool.intAt(cpi));
                case LONG -> meta.jvmci.boxLong(constantPool.longAt(cpi));
                case FLOAT -> meta.jvmci.boxFloat(constantPool.floatAt(cpi));
                case DOUBLE -> meta.jvmci.boxDouble(constantPool.doubleAt(cpi));
                default -> throw EspressoError.shouldNotReachHere();
            };
        }

        ResolvedConstant resolvedConstantOrNull = constantPool.peekResolvedOrNull(cpi, meta);
        if (resolve && resolvedConstantOrNull == null) {
            resolvedConstantOrNull = constantPool.resolvedAt(cpHolderKlass, cpi);
        }
        switch (tag) {
            case CLASS -> {
                EspressoError.guarantee(!resolve || resolvedConstantOrNull != null, "Should have been resolved");
                return resolvedConstantToJVMCIObjectType(resolvedConstantOrNull, constantPool, cpi, meta);
            }
            case STRING -> {
                return wrapEspressoObjectConstant(constantPool.resolvedStringAt(cpi), meta);
            }
            case METHODHANDLE, METHODTYPE -> {
                if (resolvedConstantOrNull != null) {
                    return wrapEspressoObjectConstant((StaticObject) resolvedConstantOrNull.value(), meta);
                } else if (resolve) {
                    throw EspressoError.shouldNotReachHere();
                } else {
                    return StaticObject.NULL;
                }
            }

            case DYNAMIC -> {
                if (resolvedConstantOrNull != null) {
                    JavaKind kind = TypeSymbols.getJavaKind(constantPool.dynamicType(cpi));
                    if (kind.isStackInt()) {
                        char typeChar = switch (kind) {
                            case Boolean -> 'Z';
                            case Byte -> 'B';
                            case Char -> 'C';
                            case Short -> 'S';
                            case Int -> 'I';
                            default -> throw EspressoError.shouldNotReachHere(kind.toString());
                        };
                        return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic(typeChar, (long) (Integer) resolvedConstantOrNull.value());
                    }
                    switch (kind) {
                        case Long:
                            return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic('J', resolvedConstantOrNull.value());
                        case Float:
                            return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic('F', (long) Float.floatToRawIntBits((float) resolvedConstantOrNull.value()));
                        case Double:
                            return (StaticObject) meta.jvmci.JavaConstant_forPrimitive.invokeDirectStatic('D', Double.doubleToRawLongBits((double) resolvedConstantOrNull.value()));
                        case Object:
                            return wrapEspressoObjectConstant((StaticObject) resolvedConstantOrNull.value(), meta);
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
        }
        LOGGER.warning(() -> "Unsupported CP entry type for lookupConstant: " + tag + " @ " + cpi + ": " + constantPool.toString(cpi));
        throw meta.throwIllegalArgumentExceptionBoundary();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "Ljdk/vm/ci/meta/JavaConstant;") StaticObject lookupAppendix(StaticObject self, int index, int opcode, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = getRuntimeConstantPool(self, meta);
        StaticObject appendix = JVMCIConstantPoolUtils.lookupAppendix(constantPool, index, opcode, context);
        if (StaticObject.isNull(appendix)) {
            return StaticObject.NULL;
        }
        return wrapEspressoObjectConstant(appendix, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoBootstrapMethodInvocation;") StaticObject lookupIndyBootstrapMethodInvocation(StaticObject self,
                    int siteIndex,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        JVMCIIndyData indyData = JVMCIIndyData.getExisting(cpHolderKlass, meta);
        int indyCpi = indyData.recoverFullCpi(siteIndex);
        return lookupBootstrapMethodInvocation(self, indyCpi, INVOKEDYNAMIC, cpHolderKlass, cpHolder, context);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoBootstrapMethodInvocation;") StaticObject lookupBootstrapMethodInvocation(StaticObject self, int cpi,
                    int opcode,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        return lookupBootstrapMethodInvocation(self, cpi, opcode, cpHolderKlass, cpHolder, context);
    }

    @Substitution(hasReceiver = true)
    public static int getNumIndyEntries(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject cpHolder = meta.jvmci.EspressoConstantPool_holder.getObject(self);
        ObjectKlass cpHolderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(cpHolder);
        JVMCIIndyData indyData = JVMCIIndyData.maybeGetExisting(cpHolderKlass, meta);
        if (indyData == null) {
            return 0;
        }
        return indyData.getLocationCount();
    }

    @Substitution(hasReceiver = true)
    public static byte getTagByteAt(StaticObject self, int cpi, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        RuntimeConstantPool runtimeConstantPool = getRuntimeConstantPool(self, meta);
        return safeTagAt(runtimeConstantPool, cpi, meta).getValue();
    }

    private static final class InternalBootstrapMethodInvocationBuilder implements JVMCIConstantPoolUtils.BootstrapMethodInvocationBuilder {
        private final Meta meta;
        private StaticObject[] unwrappedArgs;
        StaticObject wrappedArgs;
        boolean isIndy;
        Method bootstrapMethod;
        Symbol<Name> name;
        StaticObject type;

        private InternalBootstrapMethodInvocationBuilder(Meta meta) {
            this.meta = meta;
        }

        @Override
        public void setupStaticArguments(int length) {
            assert wrappedArgs == null;
            assert unwrappedArgs == null;
            wrappedArgs = meta.jvmci.JavaConstant.allocateReferenceArray(length);
            unwrappedArgs = wrappedArgs.unwrap(meta.getLanguage());
        }

        @Override
        public void staticArgument(int i, StaticObject value) {
            assert StaticObject.isNull(unwrappedArgs[i]);
            unwrappedArgs[i] = wrapEspressoObjectConstant(value, meta);
        }

        @Override
        public void staticArgumentUnresolvedDynamic(int i, int cpi) {
            assert StaticObject.isNull(unwrappedArgs[i]);
            unwrappedArgs[i] = meta.jvmci.boxInt(cpi);
        }

        @Override
        public void finalize(boolean finalIsIndy, Method finalBootstrapMethod, Symbol<Name> finalName, StaticObject finalType) {
            assert !this.isIndy;
            assert this.bootstrapMethod == null;
            assert this.name == null;
            assert this.type == null;
            this.isIndy = finalIsIndy;
            this.bootstrapMethod = finalBootstrapMethod;
            this.name = finalName;
            this.type = finalType;
        }
    }

    private static StaticObject lookupBootstrapMethodInvocation(StaticObject self, int cpi, int opcode, ObjectKlass cpHolderKlass, StaticObject cpHolder, EspressoContext context) {
        Meta meta = context.getMeta();
        RuntimeConstantPool constantPool = cpHolderKlass.getConstantPool();
        InternalBootstrapMethodInvocationBuilder builder = new InternalBootstrapMethodInvocationBuilder(meta);
        JVMCIConstantPoolUtils.lookupBootstrapMethodInvocation(constantPool, cpi, opcode, context, builder);
        if (builder.bootstrapMethod == null) {
            return StaticObject.NULL;
        }
        StaticObject methodHolderMirror;
        if (builder.bootstrapMethod.getDeclaringKlass() == cpHolderKlass) {
            methodHolderMirror = cpHolder;
        } else {
            methodHolderMirror = toJVMCIInstanceType(builder.bootstrapMethod.getDeclaringKlass(), meta);
        }
        StaticObject methodMirror = toJVMCIMethod(builder.bootstrapMethod, methodHolderMirror, meta);
        StaticObject wrappedType = wrapEspressoObjectConstant(builder.type, meta);

        StaticObject result = meta.jvmci.EspressoBootstrapMethodInvocation.allocateInstance(context);
        LOGGER.finer(() -> "ECP.lookupBootstrapMethodInvocation: returning EspressoBootstrapMethodInvocation isIndy: " + builder.isIndy + " method: " + builder.bootstrapMethod + " name: " +
                        builder.name + " type: " +
                        builder.type + " cpi:" + cpi);
        meta.jvmci.EspressoBootstrapMethodInvocation_init.invokeDirectSpecial(result, builder.isIndy, methodMirror, meta.toGuestString(builder.name), wrappedType, builder.wrappedArgs, cpi, self);
        return result;
    }
}
