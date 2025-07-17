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

import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.LOGGER;
import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.findObjectType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIObjectType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIPrimitiveType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIUnresolvedType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIMethod;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_jdk_vm_ci_runtime_JVMCI.checkJVMCIAvailable;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_EspressoJVMCIRuntime {

    private Target_com_oracle_truffle_espresso_jvmci_EspressoJVMCIRuntime() {
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Ljdk/vm/ci/runtime/JVMCICompiler;") StaticObject createEspressoGraalJVMCICompiler(StaticObject self, @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        if (meta.jvmci.GraalJVMCICompiler == null) {
            throw meta.throwNoClassDefFoundErrorBoundary("jdk.graal.compiler.api.runtime.GraalJVMCICompiler is missing, is this runtime providing an empty jdk.graal.compiler module?");
        }
        if (meta.jvmci.DummyEspressoGraalJVMCICompiler == null) {
            throw meta.throwNoClassDefFoundErrorBoundary("com.oracle.truffle.espresso.graal.DummyEspressoGraalJVMCICompiler is missing");
        }
        openJVMCITo(meta.jvmci.GraalJVMCICompiler.module(), meta);
        openJVMCITo(meta.jvmci.DummyEspressoGraalJVMCICompiler.module(), meta);
        LOGGER.fine("Creating DummyEspressoGraalJVMCICompiler");
        return (StaticObject) meta.jvmci.DummyEspressoGraalJVMCICompiler_create.invokeDirectStatic(self);
    }

    private static void openJVMCITo(ModuleTable.ModuleEntry compilerModuleEntry, Meta meta) {
        LOGGER.finer(() -> "Opening JVMCI to " + compilerModuleEntry.getNameAsString());
        StaticObject compilerModule = compilerModuleEntry.module();
        meta.jvmci.Services_openJVMCITo.invokeDirectStatic(compilerModule);
    }

    @Substitution(hasReceiver = true)
    abstract static class LookupType extends SubstitutionNode {
        abstract @JavaType(internalName = "Ljdk/vm/ci/meta/JavaType;") StaticObject execute(StaticObject self, @JavaType(String.class) StaticObject name,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject accessingClass, boolean resolve);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, StaticObject guestTypeString, StaticObject accessingClass, boolean resolve,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedArrayType_init.getCallTarget())") DirectCallNode arrayTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedPrimitiveType_forBasicType.getCallTarget())") DirectCallNode forBasicType,
                        @Cached("create(context.getMeta().jvmci.UnresolvedJavaType_create.getCallTarget())") DirectCallNode createUnresolved,
                        @Cached InitCheck initCheck) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(guestTypeString) || StaticObject.isNull(accessingClass)) {
                throw meta.throwNullPointerExceptionBoundary();
            }
            String type = meta.toHostString(guestTypeString);
            LOGGER.finer(() -> "lookupType " + type + " resolved:" + resolve);
            ObjectKlass accessingKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(accessingClass);
            return lookupType(type, accessingKlass, resolve, objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, createUnresolved, context, meta);
        }
    }

    static StaticObject lookupType(String type, ObjectKlass accessingKlass, boolean resolve, DirectCallNode objectTypeConstructor, DirectCallNode arrayTypeConstructor, DirectCallNode forBasicType,
                    InitCheck initCheck, DirectCallNode createUnresolved, EspressoContext context, Meta meta) {
        ByteSequence typeDescriptor = ByteSequence.create(type);
        if (type.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeCharOrNull(type.charAt(0));
            if (kind == null) {
                return toJVMCIUnresolvedType(typeDescriptor, createUnresolved, meta);
            }
            return toJVMCIPrimitiveType(kind, forBasicType, initCheck, meta);
        }
        return lookupNonPrimitiveType(typeDescriptor, accessingKlass, resolve, objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, createUnresolved, context, meta);
    }

    static StaticObject lookupNonPrimitiveType(ByteSequence typeDescriptor, ObjectKlass accessingKlass, boolean resolve, DirectCallNode objectTypeConstructor, DirectCallNode arrayTypeConstructor,
                    DirectCallNode forBasicType, InitCheck initCheck, DirectCallNode createUnresolved, EspressoContext context, Meta meta) {
        Symbol<Type> symbol = meta.getTypes().lookupValidType(typeDescriptor);
        if (symbol == null) {
            if (resolve) {
                symbol = meta.getTypes().getOrCreateValidType(typeDescriptor);
            }
            if (symbol == null) {
                return toJVMCIUnresolvedType(typeDescriptor, createUnresolved, meta);
            }
        }
        Klass result = findObjectType(symbol, accessingKlass, resolve, meta);
        if (result == null) {
            assert !resolve;
            return toJVMCIUnresolvedType(symbol, createUnresolved, meta);
        } else {
            return toJVMCIObjectType(result, objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class ResolveMethod extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject receiver,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject method,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject callerType);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, StaticObject receiver, StaticObject jvmciMethod, StaticObject accessingClass,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(receiver) || StaticObject.isNull(accessingClass) || StaticObject.isNull(jvmciMethod)) {
                throw meta.throwNullPointerExceptionBoundary();
            }
            ObjectKlass receiverKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(receiver);
            Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(jvmciMethod);
            ObjectKlass accessingKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(accessingClass);
            LOGGER.finer(() -> "resolveMethod " + method + " on " + receiverKlass + " as seen from " + accessingKlass);
            if (method.isSignaturePolymorphicDeclared() || !receiverKlass.isLinked() || receiverKlass.isInterface() || method.isStatic()) {
                return StaticObject.NULL;
            }

            ObjectKlass declaringKlass = method.getDeclaringKlass();
            if (!checkAccess(accessingKlass, declaringKlass, method)) {
                return StaticObject.NULL;
            }

            Method resolved;
            if (method.isPrivate()) {
                resolved = method;
            } else if (declaringKlass.isInterface()) {
                if (!declaringKlass.isAssignableFrom(receiverKlass)) {
                    return StaticObject.NULL;
                }
                assert method.getITableIndex() >= 0 : method;
                resolved = receiverKlass.itableLookupOrNull(declaringKlass, method.getITableIndex());
                if (resolved != null && !resolved.isPublic()) {
                    return StaticObject.NULL;
                }
            } else {
                assert method.getVTableIndex() >= 0 : method;
                resolved = receiverKlass.vtableLookup(method.getVTableIndex());
            }
            if (resolved == null) {
                return StaticObject.NULL;
            }
            StaticObject jvmciHolder;
            if (resolved.getDeclaringKlass() == receiverKlass) {
                jvmciHolder = receiver;
            } else if (resolved.getDeclaringKlass() == accessingKlass) {
                jvmciHolder = accessingClass;
            } else {
                jvmciHolder = toJVMCIInstanceType(resolved.getDeclaringKlass(), objectTypeConstructor, context, meta);
            }
            return toJVMCIMethod(resolved, jvmciHolder, methodConstructor, context, meta);
        }

        @TruffleBoundary
        private static boolean checkAccess(ObjectKlass accessingKlass, ObjectKlass declaringKlass, Method method) {
            return RuntimeConstantPool.memberCheckAccess(accessingKlass, declaringKlass, method);
        }
    }
}
