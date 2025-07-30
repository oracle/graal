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

import static com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_jdk_vm_ci_runtime_JVMCI.checkJVMCIAvailable;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
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
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType() {
    }

    @Substitution(hasReceiver = true)
    abstract static class GetStaticFields0 extends SubstitutionNode {
        abstract @JavaType(internalName = "[Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaField_init.getCallTarget())") DirectCallNode fieldConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            return toJVMCIFields(klass.getStaticFieldTable(), self, fieldConstructor, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetInstanceFields0 extends SubstitutionNode {
        abstract @JavaType(internalName = "[Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaField_init.getCallTarget())") DirectCallNode fieldConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            return toJVMCIFields(klass.getAllDeclaredInstanceFields(), self, fieldConstructor, context, meta);
        }
    }

    private static StaticObject toJVMCIFields(Field[] fields, StaticObject holder, DirectCallNode fieldConstructor, EspressoContext context, Meta meta) {
        int count = 0;
        for (Field f : fields) {
            if (!f.isRemoved()) {
                count++;
            }
        }
        StaticObject result = meta.jvmci.EspressoResolvedJavaField.allocateReferenceArray(count);
        StaticObject[] underlying = result.unwrap(context.getLanguage());
        int i = 0;
        for (Field f : fields) {
            if (!f.isRemoved()) {
                StaticObject jvmciMirror = toJVMCIField(f, holder, fieldConstructor, context, meta);
                underlying[i++] = jvmciMirror;
            }
        }
        return result;
    }

    static StaticObject toJVMCIField(Field f, StaticObject holder, DirectCallNode fieldConstructor, EspressoContext context, Meta meta) {
        assert meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder) == f.getDeclaringKlass() : f + " not declared in " + meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder);
        StaticObject jvmciMirror = meta.jvmci.EspressoResolvedJavaField.allocateInstance(context);
        meta.jvmci.HIDDEN_FIELD_MIRROR.setHiddenObject(jvmciMirror, f);
        fieldConstructor.call(jvmciMirror, holder);
        return jvmciMirror;
    }

    static StaticObject toJVMCIField(Field f, StaticObject holder, Meta meta) {
        assert meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder) == f.getDeclaringKlass() : f + " not declared in " + meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder);
        StaticObject jvmciMirror = meta.jvmci.EspressoResolvedJavaField.allocateInstance(meta.getContext());
        meta.jvmci.HIDDEN_FIELD_MIRROR.setHiddenObject(jvmciMirror, f);
        meta.jvmci.EspressoResolvedJavaField_init.invokeDirectSpecial(jvmciMirror, holder);
        return jvmciMirror;
    }

    static StaticObject toJVMCIField(Field f, StaticObject maybeHolder, ObjectKlass maybeHolderKlass, Meta meta) {
        StaticObject holder;
        if (f.getDeclaringKlass() == maybeHolderKlass) {
            holder = maybeHolder;
        } else {
            holder = toJVMCIInstanceType(f.getDeclaringKlass(), meta);
        }
        return toJVMCIField(f, holder, meta);
    }

    private static StaticObject toJVMCIMethods(Method[] methods, boolean constructors, StaticObject holder, DirectCallNode methodConstructor, EspressoContext context, Meta meta) {
        int count = 0;
        for (Method m : methods) {
            if (Names._clinit_.equals(m.getName())) {
                continue;
            }
            if (Names._init_.equals(m.getName()) == constructors) {
                count++;
            }

        }
        StaticObject result = meta.jvmci.EspressoResolvedJavaMethod.allocateReferenceArray(count);
        StaticObject[] underlying = result.unwrap(context.getLanguage());
        int i = 0;
        for (Method m : methods) {
            if (Names._clinit_.equals(m.getName())) {
                continue;
            }
            if (Names._init_.equals(m.getName()) == constructors) {
                StaticObject jvmciMirror = toJVMCIMethod(m, holder, methodConstructor, context, meta);
                underlying[i++] = jvmciMirror;
            }
        }
        return result;
    }

    static StaticObject toJVMCIMethod(Method m, StaticObject holder, DirectCallNode methodConstructor, EspressoContext context, Meta meta) {
        // We need to get the identity method:
        // * This makes sure .equals works
        // * It also ensures the methods have the right i/v-table indices
        Method identityMethod = m.identity();
        boolean poisoned = m.hasPoisonPill();
        assert meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder) == identityMethod.getDeclaringKlass();
        StaticObject jvmciMirror = meta.jvmci.EspressoResolvedJavaMethod.allocateInstance(context);
        meta.jvmci.HIDDEN_METHOD_MIRROR.setHiddenObject(jvmciMirror, identityMethod);
        methodConstructor.call(jvmciMirror, holder, poisoned);
        return jvmciMirror;
    }

    static StaticObject toJVMCIMethod(Method m, StaticObject holder, Meta meta) {
        // We need to get the identity method (see above)
        Method identityMethod = m.identity();
        boolean poisoned = m.hasPoisonPill();
        assert meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder) == identityMethod.getDeclaringKlass();
        StaticObject jvmciMirror = meta.jvmci.EspressoResolvedJavaMethod.allocateInstance(meta.getContext());
        meta.jvmci.HIDDEN_METHOD_MIRROR.setHiddenObject(jvmciMirror, identityMethod);
        meta.jvmci.EspressoResolvedJavaMethod_init.invokeDirectSpecial(jvmciMirror, holder, poisoned);
        return jvmciMirror;
    }

    static StaticObject toJVMCIMethod(Method m, StaticObject maybeHolder, ObjectKlass maybeHolderKlass, Meta meta) {
        StaticObject holder;
        if (m.getDeclaringKlass() == maybeHolderKlass) {
            holder = maybeHolder;
        } else {
            holder = toJVMCIInstanceType(m.getDeclaringKlass(), meta);
        }
        return toJVMCIMethod(m, holder, meta);
    }

    @Substitution(hasReceiver = true)
    public static int getFlags(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return klass.getModifiers();
    }

    @Substitution(hasReceiver = true)
    public static boolean equals0(StaticObject self, @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject that,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(that)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        ObjectKlass thatKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(that);
        return selfKlass.equals(thatKlass);
    }

    @Substitution(hasReceiver = true)
    public static int hashCode(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return selfKlass.hashCode();
    }

    @Substitution(hasReceiver = true)
    abstract static class GetSuperclass0 extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            ObjectKlass superKlass = klass.getSuperKlass();
            if (superKlass == null) {
                meta.throwIllegalArgumentExceptionBoundary();
            }

            return toJVMCIInstanceType(superKlass, objectTypeConstructor, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetInterfaces0 extends SubstitutionNode {
        abstract @JavaType(internalName = "[Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            ObjectKlass[] interfaces = klass.getSuperInterfaces();
            if (interfaces.length == 0) {
                return StaticObject.NULL;
            }
            StaticObject result = meta.jvmci.EspressoResolvedInstanceType.allocateReferenceArray(interfaces.length);
            StaticObject[] unwrappedResults = result.unwrap(meta.getLanguage());
            for (int i = 0; i < interfaces.length; i++) {
                unwrappedResults[i] = toJVMCIInstanceType(interfaces[i], objectTypeConstructor, context, meta);
            }
            return result;
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class Initialize extends SubstitutionNode {
        abstract void execute(StaticObject self);

        @Specialization
        static void doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached InitCheck initCheck) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            initCheck.execute(klass);
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean declaresDefaultMethods(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return selfKlass.hasDeclaredDefaultMethods();
    }

    @Substitution(hasReceiver = true)
    public static boolean hasDefaultMethods(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return selfKlass.hasDefaultMethods();
    }

    @Substitution(hasReceiver = true)
    public static boolean hasSameClassLoader(StaticObject self, @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject other,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        ObjectKlass otherKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(other);
        return selfKlass.getDefiningClassLoader() == otherKlass.getDefiningClassLoader();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getName0(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        Symbol<Type> type = selfKlass.getType();
        if (selfKlass.isAnonymous()) {
            return meta.toGuestString(appendId(type, selfKlass));
        } else if (selfKlass.isHidden()) {
            int idx = type.lastIndexOf((byte) '+');
            assert idx > 0;
            return meta.toGuestString(convertHidden(type, idx));
        } else {
            return meta.toGuestString(type);
        }
    }

    @TruffleBoundary
    private static String convertHidden(Symbol<Type> type, int idx) {
        return type.subSequence(0, idx) + "." + type.subSequence(idx + 1);
    }

    @TruffleBoundary
    private static String appendId(Symbol<Type> type, ObjectKlass selfKlass) {
        return type.subSequence(0, type.length() - 1) + "/" + selfKlass.getId();
    }

    @Substitution(hasReceiver = true)
    public static boolean isLinked(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return selfKlass.isLinked();
    }

    @Substitution(hasReceiver = true)
    public static void link(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        selfKlass.ensureLinked();
    }

    @Substitution(hasReceiver = true)
    public static boolean isInitialized(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return selfKlass.isInitialized();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getSourceFileName(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return meta.toGuestString(selfKlass.getSourceFile());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getMirror0(StaticObject self,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return klass.mirror();
    }

    @Substitution(hasReceiver = true)
    abstract static class GetDeclaredMethods0 extends SubstitutionNode {
        abstract @JavaType(internalName = "[Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            return toJVMCIMethods(klass.getDeclaredMethods(), false, self, methodConstructor, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetDeclaredConstructors0 extends SubstitutionNode {
        abstract @JavaType(internalName = "[Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            return toJVMCIMethods(klass.getDeclaredMethods(), true, self, methodConstructor, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetClassInitializer extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            Method classInitializer = getClassInitializer(klass);
            if (classInitializer == null) {
                return StaticObject.NULL;
            }
            return toJVMCIMethod(classInitializer, self, methodConstructor, context, meta);
        }

        @TruffleBoundary
        private static Method getClassInitializer(ObjectKlass klass) {
            return klass.getClassInitializer();
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class EspressoSingleImplementor extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
            /*
             * Note: What espresso calls "single implementor" is called "leaf concrete subtype" by
             * JVMCI
             * 
             * In turn, what JVMCI calls "single implementor" doesn't currently exist in espresso.
             * It would be the unique non-interface implementor of an interface, and doesn't have to
             * be concrete
             */
            ObjectKlass singleImplementor = getSingleImplementor(context, klass);
            if (singleImplementor == null) {
                return StaticObject.NULL;
            }
            return toJVMCIInstanceType(singleImplementor, objectTypeConstructor, context, meta);
        }

        @TruffleBoundary
        private static ObjectKlass getSingleImplementor(EspressoContext context, ObjectKlass klass) {
            return context.getClassHierarchyOracle().readSingleImplementor(klass).get();
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean isLeafClass(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return context.getClassHierarchyOracle().isLeafKlass(selfKlass).isValid();
    }

    @Substitution(hasReceiver = true)
    public static int getVtableLength(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        ObjectKlass selfKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);
        return selfKlass.getVTable().length;
    }

    @Substitution(hasReceiver = true)
    abstract static class GetAllMethods0 extends SubstitutionNode {
        abstract @JavaType(internalName = "[Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();

            ObjectKlass klass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(self);

            Method.MethodVersion[] declaredMethodVersions = klass.getDeclaredMethodVersions();
            Method.MethodVersion[] mirandaMethods = klass.getMirandaMethods();
            int resultSize = declaredMethodVersions.length;
            if (mirandaMethods != null) {
                for (Method.MethodVersion mirandaMethod : mirandaMethods) {
                    if (mirandaMethod.getMethod().hasPoisonPill()) {
                        resultSize++;
                    }
                }
            }
            StaticObject result = meta.jvmci.EspressoResolvedJavaMethod.allocateReferenceArray(resultSize);
            StaticObject[] underlying = result.unwrap(context.getLanguage());
            int i = 0;
            for (Method.MethodVersion methodVersion : declaredMethodVersions) {
                underlying[i++] = toJVMCIMethod(methodVersion.getMethod(), self, methodConstructor, context, meta);
            }
            if (resultSize != declaredMethodVersions.length) {
                for (Method.MethodVersion mirandaMethod : mirandaMethods) {
                    if (mirandaMethod.getMethod().hasPoisonPill()) {
                        StaticObject holder;
                        if (mirandaMethod.getDeclaringKlass() == klass) {
                            holder = self;
                        } else {
                            holder = toJVMCIInstanceType(mirandaMethod.getDeclaringKlass(), meta);
                        }
                        underlying[i++] = toJVMCIMethod(mirandaMethod.getMethod(), holder, methodConstructor, context, meta);
                    }
                }
            }
            return result;
        }
    }
}
