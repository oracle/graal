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

import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIUnresolvedType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_jdk_vm_ci_runtime_JVMCI.checkJVMCIAvailable;

import java.lang.reflect.Executable;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.constantpool.ResolvedConstant;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jvmci.JVMCIIndyData;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaMethod {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaMethod() {
    }

    @Substitution(hasReceiver = true)
    abstract static class GetLocalVariableTable extends SubstitutionNode {
        abstract @JavaType(internalName = "Ljdk/vm/ci/meta/LocalVariableTable;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.Local_init.getCallTarget())") DirectCallNode localConstructor,
                        @Cached("create(context.getMeta().jvmci.LocalVariableTable_init.getCallTarget())") DirectCallNode localVariableTableConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedArrayType_init.getCallTarget())") DirectCallNode arrayTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedPrimitiveType_forBasicType.getCallTarget())") DirectCallNode forBasicType,
                        @Cached("create(context.getMeta().jvmci.UnresolvedJavaType_create.getCallTarget())") DirectCallNode createUnresolved,
                        @Cached InitCheck initCheck) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
            if (method.getCodeAttribute() == null) {
                return StaticObject.NULL;
            }
            LocalVariableTable localvariableTable = method.getCodeAttribute().getLocalvariableTable();
            if (localvariableTable == LocalVariableTable.EMPTY_LVT) {
                return StaticObject.NULL;
            }
            Local[] locals = localvariableTable.getLocals();
            StaticObject guestLocals = meta.jvmci.Local.allocateReferenceArray(locals.length);
            StaticObject[] unwrappedGuestLocals = guestLocals.unwrap(meta.getLanguage());
            for (int i = 0; i < locals.length; i++) {
                unwrappedGuestLocals[i] = toJVMCILocal(locals[i], method.getDeclaringKlass(), localConstructor, objectTypeConstructor, arrayTypeConstructor, forBasicType, createUnresolved,
                                initCheck, context, meta);
            }
            StaticObject result = meta.jvmci.LocalVariableTable.allocateInstance(context);
            localVariableTableConstructor.call(result, guestLocals);
            return result;
        }

        private static StaticObject toJVMCILocal(Local local, ObjectKlass declaringKlass, DirectCallNode localConstructor, DirectCallNode objectTypeConstructor, DirectCallNode arrayTypeConstructor,
                        DirectCallNode forBasicType, DirectCallNode createUnresolved, InitCheck initCheck, EspressoContext context, Meta meta) {
            StaticObject result = meta.jvmci.Local.allocateInstance(context);
            Klass resolvedType = getResolvedType(local, declaringKlass, meta);
            StaticObject guestType;
            if (resolvedType != null) {
                guestType = toJVMCIType(resolvedType, objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, context, meta);
            } else {
                guestType = toJVMCIUnresolvedType(local.getTypeOrDesc(), createUnresolved, meta);
            }
            localConstructor.call(result, meta.toGuestString(local.getNameAsString()), guestType, local.getStartBCI(), local.getEndBCI(), local.getSlot());
            return result;
        }

        @TruffleBoundary
        private static Klass getResolvedType(Local local, ObjectKlass declaringKlass, Meta meta) {
            Symbol<Type> localType;
            try {
                localType = local.getTypeOrDesc().validateType(true);
            } catch (ValidationException e) {
                throw EspressoError.shouldNotReachHere("Local seems to come from a LocalTypeTable", e);
            }
            return meta.resolveSymbolOrNull(localType, declaringKlass.getDefiningClassLoader(), declaringKlass.protectionDomain());
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetLineNumberTable extends SubstitutionNode {
        abstract @JavaType(internalName = "Ljdk/vm/ci/meta/LineNumberTable;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.LineNumberTable_init.getCallTarget())") DirectCallNode lineNumberTableConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
            CodeAttribute codeAttribute = method.getCodeAttribute();
            if (codeAttribute == null) {
                return StaticObject.NULL;
            }
            LineNumberTableAttribute lineNumberTable = codeAttribute.getLineNumberTableAttribute();
            if (lineNumberTable == LineNumberTableAttribute.EMPTY) {
                return StaticObject.NULL;
            }
            List<LineNumberTableAttribute.Entry> entries = lineNumberTable.getEntries();
            int size = entries.size();
            if (size == 0) {
                return StaticObject.NULL;
            }
            int[] bcis = new int[size];
            int[] lines = new int[size];
            for (int i = 0; i < size; i++) {
                LineNumberTableAttribute.Entry entry = entries.get(i);
                bcis[i] = entry.getBCI();
                lines[i] = entry.getLineNumber();
            }
            StaticObject guestBcis = StaticObject.wrap(bcis, meta);
            StaticObject guestLines = StaticObject.wrap(lines, meta);
            StaticObject result = meta.jvmci.LineNumberTable.allocateInstance(context);
            lineNumberTableConstructor.call(result, guestLines, guestBcis);
            return result;
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetExceptionHandlers extends SubstitutionNode {
        abstract @JavaType(internalName = "[Ljdk/vm/ci/meta/ExceptionHandler;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.ExceptionHandler_init.getCallTarget())") DirectCallNode exceptionHandlerConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.UnresolvedJavaType_create.getCallTarget())") DirectCallNode createUnresolved) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
            CodeAttribute codeAttribute = method.getCodeAttribute();
            if (codeAttribute == null || codeAttribute.getExceptionHandlers().length == 0) {
                return meta.jvmci.ExceptionHandler.allocateReferenceArray(0);
            }
            ExceptionHandler[] exceptionHandlers = codeAttribute.getExceptionHandlers();
            StaticObject result = meta.jvmci.ExceptionHandler.allocateReferenceArray(exceptionHandlers.length);
            StaticObject[] unwrapped = result.unwrap(meta.getLanguage());
            for (int i = 0; i < exceptionHandlers.length; i++) {
                ExceptionHandler exceptionHandler = exceptionHandlers[i];
                StaticObject jvmciExceptionHandler = meta.jvmci.ExceptionHandler.allocateInstance(context);
                StaticObject catchType;
                int exceptionClassIndex = exceptionHandler.catchTypeCPI();
                if (exceptionClassIndex == 0) {
                    catchType = StaticObject.NULL;
                } else {
                    RuntimeConstantPool pool = method.getRuntimeConstantPool();
                    ResolvedConstant resolvedConstant = pool.peekResolvedOrNull(exceptionClassIndex, meta);
                    if (resolvedConstant != null) {
                        ObjectKlass catchKlass = (ObjectKlass) resolvedConstant.value();
                        catchType = toJVMCIInstanceType(catchKlass, objectTypeConstructor, context, meta);
                    } else {
                        ByteSequence type = TypeSymbols.nameToType(pool.className(exceptionClassIndex));
                        catchType = toJVMCIUnresolvedType(type, createUnresolved, meta);
                    }
                }
                exceptionHandlerConstructor.call(jvmciExceptionHandler,
                                exceptionHandler.getStartBCI(),
                                exceptionHandler.getEndBCI(),
                                exceptionHandler.getHandlerBCI(),
                                exceptionClassIndex,
                                catchType);
                unwrapped[i] = jvmciExceptionHandler;
            }
            return result;
        }
    }

    @Substitution(hasReceiver = true)
    public static int getFlags(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return method.getModifiers();
    }

    @Substitution(hasReceiver = true)
    public static boolean equals0(StaticObject self, @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject that,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(that)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        Method selfMethod = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        Method thatMethod = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(that);
        return selfMethod == thatMethod; // assumes Method.equals is Object.equals
    }

    @Substitution(hasReceiver = true)
    public static int hashCode0(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return System.identityHashCode(method); // assumes Method.hashCode is Object.hashCode
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Executable.class) StaticObject getMirror0(StaticObject self,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return method.makeMirror(meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getName0(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return meta.toGuestString(method.getName());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getCode0(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        if (method.getCodeAttribute() == null) {
            throw meta.throwIllegalArgumentExceptionBoundary();
        }
        if (!method.getMethodVersion().usesIndy()) {
            return StaticObject.wrap(method.getOriginalCode(), meta);
        }
        JVMCIIndyData indyData = JVMCIIndyData.getOrCreate(method.getDeclaringKlass(), meta);
        return StaticObject.wrap(indyData.getCode(method), meta);
    }

    @Substitution(hasReceiver = true)
    public static int getCodeSize0(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return getCodeSize(method);
    }

    private static int getCodeSize(Method method) {
        CodeAttribute codeAttribute = method.getCodeAttribute();
        if (codeAttribute == null) {
            return 0;
        }
        return codeAttribute.getOriginalCode().length;
    }

    @Substitution(hasReceiver = true)
    public static int getMaxLocals(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        CodeAttribute codeAttribute = method.getCodeAttribute();
        if (codeAttribute == null) {
            return 0;
        }
        return codeAttribute.getMaxLocals();
    }

    @Substitution(hasReceiver = true)
    public static int getMaxStackSize(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        CodeAttribute codeAttribute = method.getCodeAttribute();
        if (codeAttribute == null) {
            return 0;
        }
        // 1 additional slot for the appendix that gets "pushed" on the stack by the compiler
        // both for INVOKEDYNAMIC and usage of "InvokeGeneric" polymorphic signature methods
        // ("invokehandle" in HotSpot)
        return codeAttribute.getMaxStack() + 1;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getRawSignature(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return meta.toGuestString(method.getRawSignature());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(StackTraceElement.class) StaticObject asStackTraceElement(StaticObject self, int bci, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        StaticObject result = meta.java_lang_StackTraceElement.allocateInstance(context);
        int queryBci = bci;
        assert VM.EspressoStackElement.UNKNOWN_BCI < 0;
        assert VM.EspressoStackElement.NATIVE_BCI < VM.EspressoStackElement.UNKNOWN_BCI;
        if (bci < VM.EspressoStackElement.NATIVE_BCI || bci > getCodeSize(method)) {
            queryBci = VM.EspressoStackElement.UNKNOWN_BCI;
        }
        VM.fillInElementBasic(result, new VM.EspressoStackElement(method, queryBci), meta);
        return result;
    }

    @Substitution(hasReceiver = true)
    public static boolean isForceInline(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return method.isForceInline();
    }

    @Substitution(hasReceiver = true)
    public static boolean hasNeverInlineDirective(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return method.isDontInline();
    }

    @Substitution(hasReceiver = true)
    public static int getVtableIndex(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return method.getVTableIndex();
    }

    @Substitution(hasReceiver = true)
    public static int getVtableIndexForInterfaceMethod(StaticObject self, @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject resolved,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(resolved);
        Method found = klass.itableLookupOrNull(method.getDeclaringKlass(), method.getITableIndex());
        if (found != null && !found.getDeclaringKlass().isInterface()) {
            return found.getVTableIndex();
        }
        return -1;
    }

    @Substitution(hasReceiver = true)
    public static boolean isLeafMethod(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return context.getClassHierarchyOracle().isLeafMethod(method).isValid();
    }

    @Substitution(hasReceiver = true)
    public static boolean hasAnnotations(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(self);
        return method.getAttribute(Names.RuntimeVisibleAnnotations) != null;
    }
}
