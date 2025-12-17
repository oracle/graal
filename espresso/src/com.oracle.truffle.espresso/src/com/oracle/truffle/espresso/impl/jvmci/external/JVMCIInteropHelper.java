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
package com.oracle.truffle.espresso.impl.jvmci.external;

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.impl.jvmci.JVMCIUtils;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.continuations.Target_org_graalvm_continuations_IdentityHashCodes;

@ExportLibrary(InteropLibrary.class)
public final class JVMCIInteropHelper implements ContextAccess, TruffleObject {
    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> ALL_MEMBERS_SET;

    static {
        String[] members = {
                        InvokeMember.GET_FLAGS,
                        InvokeMember.GET_NAME,
                        InvokeMember.GET_INSTANCE_FIELDS,
                        InvokeMember.GET_STATIC_FIELDS,
                        InvokeMember.LOOKUP_INSTANCE_TYPE,
                        InvokeMember.IS_ASSIGNABLE_FROM,
                        InvokeMember.GET_INTERFACES,
                        InvokeMember.INITIALIZE,
                        InvokeMember.LINK,
                        InvokeMember.IS_INITIALIZED,
                        InvokeMember.IS_LINKED,
                        InvokeMember.GET_CLASS_INITIALIZER,
                        InvokeMember.GET_DECLARED_METHODS,
                        InvokeMember.GET_DECLARED_CONSTRUCTORS,
                        InvokeMember.GET_ALL_METHODS,
                        InvokeMember.GET_ANNOTATION_DATA,
                        InvokeMember.HAS_SAME_CLASSLOADER,
                        InvokeMember.DECLARES_DEFAULT_METHODS,
                        InvokeMember.HAS_DEFAULT_METHODS,
                        InvokeMember.IS_LEAF_CLASS,
                        InvokeMember.GET_CONSTANT_POOL,
                        InvokeMember.GET_SOURCE_FILENAME,
                        InvokeMember.ESPRESSO_SINGLE_IMPLEMENTOR,
                        InvokeMember.TO_GUEST_STRING,
                        InvokeMember.MAKE_IDENTITY_HASH_CODE,
                        InvokeMember.NEW_OBJECT_ARRAY,
                        InvokeMember.NEW_PRIMITIVE_ARRAY,
        };
        ALL_MEMBERS = new KeysArray<>(members);
        ALL_MEMBERS_SET = Set.of(members);
    }

    private final EspressoContext context;

    public JVMCIInteropHelper(EspressoContext context) {
        this.context = context;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    @ExportMessage
    abstract static class InvokeMember {
        static final String GET_FLAGS = "getFlags";
        static final String GET_NAME = "getName";
        static final String GET_INSTANCE_FIELDS = "getInstanceFields";
        static final String GET_STATIC_FIELDS = "getStaticFields";
        static final String LOOKUP_INSTANCE_TYPE = "lookupInstanceType";
        static final String INITIALIZE = "initialize";
        static final String LINK = "link";
        static final String IS_INITIALIZED = "isInitialized";
        static final String IS_LINKED = "isLinked";
        static final String IS_ASSIGNABLE_FROM = "isAssignableFrom";
        static final String GET_INTERFACES = "getInterfaces";
        static final String GET_CLASS_INITIALIZER = "getClassInitializer";
        static final String GET_DECLARED_METHODS = "getDeclaredMethods";
        static final String GET_DECLARED_CONSTRUCTORS = "getDeclaredConstructors";
        static final String GET_ALL_METHODS = "getAllMethods";
        static final String GET_ANNOTATION_DATA = "getAnnotationData";
        static final String HAS_SAME_CLASSLOADER = "hasSameClassLoader";
        static final String DECLARES_DEFAULT_METHODS = "declaresDefaultMethods";
        static final String HAS_DEFAULT_METHODS = "hasDefaultMethods";
        static final String IS_LEAF_CLASS = "isLeafClass";
        static final String GET_CONSTANT_POOL = "getConstantPool";
        static final String GET_SOURCE_FILENAME = "getSourceFileName";
        static final String ESPRESSO_SINGLE_IMPLEMENTOR = "espressoSingleImplementor";
        static final String TO_GUEST_STRING = "toGuestString";
        static final String MAKE_IDENTITY_HASH_CODE = "makeIdentityHashCode";
        static final String NEW_OBJECT_ARRAY = "newObjectArray";
        static final String NEW_PRIMITIVE_ARRAY = "newPrimitiveArray";

        @Specialization(guards = "GET_FLAGS.equals(member)")
        static int getFlags(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.getModifiers();
        }

        @Specialization(guards = "GET_NAME.equals(member)")
        static String getName(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.getType().toString();
        }

        @Specialization(guards = "GET_INSTANCE_FIELDS.equals(member)")
        static Object getInstanceFields(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return new KeysArray<>(klass.getAllDeclaredInstanceFields());
        }

        @Specialization(guards = "GET_STATIC_FIELDS.equals(member)")
        static Object getStaticFields(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return new KeysArray<>(klass.getStaticFieldTable());
        }

        @Specialization(guards = "GET_INTERFACES.equals(member)")
        static Object getInterfaces(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            ObjectKlass[] interfaces = klass.getSuperInterfaces();
            if (interfaces.length == 0) {
                return StaticObject.NULL;
            }
            return new KeysArray<>(interfaces);
        }

        @Specialization(guards = "LOOKUP_INSTANCE_TYPE.equals(member)")
        static Object lookupInstanceType(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary stringInterop,
                        @CachedLibrary(limit = "1") @Exclusive InteropLibrary booleanInterop,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError,
                        @Cached @Shared InlinedBranchProfile valueError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 3) {
                arityError.enter(node);
                throw ArityException.create(3, 3, arguments.length);
            }
            String type;
            try {
                type = stringInterop.asString(arguments[0]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected a string as first argument");
            }
            if (type.charAt(0) != 'L' || type.charAt(type.length() - 1) != ';') {
                valueError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type descriptor as first argument (e.g., Lfoo/Bar;)");
            }
            if (!(arguments[1] instanceof ObjectKlass accessingKlass)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type as second argument");
            }
            boolean resolve;
            try {
                resolve = booleanInterop.asBoolean(arguments[2]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected a boolean as third argument");
            }
            EspressoContext context = EspressoContext.get(node);
            ByteSequence typeBytes = ByteSequence.create(type);
            if (!Validation.validTypeDescriptor(typeBytes, false)) {
                valueError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected a valid instance type descriptor as first argument (e.g., Lfoo/Bar;)");
            }
            Symbol<Type> typeSymbol = context.getTypes().lookupValidType(typeBytes);
            if (typeSymbol == null) {
                return StaticObject.NULL;
            }
            assert !TypeSymbols.isArray(typeSymbol);
            Meta meta = context.getMeta();
            StaticObject loader = accessingKlass.getDefiningClassLoader();
            if (resolve) {
                return meta.loadKlassOrFail(typeSymbol, loader, accessingKlass.protectionDomain());
            } else {
                Klass klass = meta.getRegistries().findLoadedClass(typeSymbol, loader);
                if (klass == null) {
                    return StaticObject.NULL;
                }
                return klass;
            }
        }

        @Specialization(guards = "INITIALIZE.equals(member)")
        static Object initialize(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            klass.initialize();
            return StaticObject.NULL;
        }

        @Specialization(guards = "LINK.equals(member)")
        static Object link(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            klass.ensureLinked();
            return StaticObject.NULL;
        }

        @Specialization(guards = "IS_INITIALIZED.equals(member)")
        static boolean isInitialized(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.isInitialized();
        }

        @Specialization(guards = "IS_LINKED.equals(member)")
        static boolean isLinked(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.isLinked();
        }

        @Specialization(guards = "IS_ASSIGNABLE_FROM.equals(member)")
        static boolean isAssignableFrom(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof ObjectKlass selfKlass)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type as first argument");
            }
            if (!(arguments[1] instanceof ObjectKlass otherKlass)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type as second argument");
            }
            return selfKlass.isAssignableFrom(otherKlass);
        }

        @Specialization(guards = "GET_CLASS_INITIALIZER.equals(member)")
        static Object getClassInitializer(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            Method classInitializer = klass.getClassInitializer();
            if (classInitializer == null) {
                return StaticObject.NULL;
            }
            return classInitializer;
        }

        @Specialization(guards = "GET_DECLARED_METHODS.equals(member)")
        static Object getDeclaredMethods(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            Method[] methods = klass.getDeclaredMethods();
            if (methods.length == 0) {
                return StaticObject.NULL;
            }
            return new KeysArray<>(methods);
        }

        @Specialization(guards = "GET_DECLARED_CONSTRUCTORS.equals(member)")
        static Object getDeclaredConstructors(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            Method[] methods = klass.getDeclaredConstructors();
            if (methods.length == 0) {
                return StaticObject.NULL;
            }
            return new KeysArray<>(methods);
        }

        @Specialization(guards = "GET_ALL_METHODS.equals(member)")
        static Object getAllMethods(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            Method[] methods = JVMCIUtils.getAllMethods(klass, Method[]::new, (array, i, m) -> array[i] = m);
            if (methods.length == 0) {
                return StaticObject.NULL;
            }
            return new KeysArray<>(methods);
        }

        // This would be less awkward if we could return Klass as non-statics JVMCI object like
        // field and method
        @Specialization(guards = "GET_ANNOTATION_DATA.equals(member)")
        static Object getAnnotationData(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            byte[] data = null;
            if (arguments[0] instanceof ObjectKlass klass) {
                Attribute annotations = klass.getAttribute(ParserNames.RuntimeVisibleAnnotations);
                if (annotations != null) {
                    data = annotations.getData();
                }
            } else if (arguments[0] instanceof Method method) {
                Attribute annotations = method.getAttribute(ParserNames.RuntimeVisibleAnnotations);
                if (annotations != null) {
                    data = annotations.getData();
                }
            } else if (arguments[0] instanceof Field field) {
                Attribute annotations = field.getAttribute(ParserNames.RuntimeVisibleAnnotations);
                if (annotations != null) {
                    data = annotations.getData();
                }
            } else {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type, a method, or a field as first argument");
            }
            if (data == null) {
                return StaticObject.NULL;
            }
            // TODO should byte[] also support the buffer messages?
            return EspressoContext.get(node).getMeta().java_nio_ByteBuffer_wrap.invokeDirectStatic(StaticObject.wrap(data, EspressoContext.get(node).getMeta()));
        }

        @Specialization(guards = "HAS_SAME_CLASSLOADER.equals(member)")
        static boolean hasSameClassLoader(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof ObjectKlass klass1)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type as first argument");
            }
            if (!(arguments[1] instanceof ObjectKlass klass2)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type as second argument");
            }
            return klass1.getDefiningClassLoader() == klass2.getDefiningClassLoader();
        }

        @Specialization(guards = "DECLARES_DEFAULT_METHODS.equals(member)")
        static boolean declaresDefaultMethods(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.hasDeclaredDefaultMethods();
        }

        @Specialization(guards = "HAS_DEFAULT_METHODS.equals(member)")
        static boolean hasDefaultMethods(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.hasDefaultMethods();
        }

        @Specialization(guards = "IS_LEAF_CLASS.equals(member)")
        static boolean isLeafClass(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            EspressoContext context = EspressoContext.get(node);
            return context.getClassHierarchyOracle().isLeafKlass(klass).isValid();
        }

        @Specialization(guards = "GET_CONSTANT_POOL.equals(member)")
        static Object getConstantPool(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return new InteropConstantPoolWrapper(klass.getConstantPool());
        }

        @Specialization(guards = "GET_SOURCE_FILENAME.equals(member)")
        static Object getSourceFileName(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            return klass.getSourceFile();
        }

        @Specialization(guards = "ESPRESSO_SINGLE_IMPLEMENTOR.equals(member)")
        static Object espressoSingleImplementor(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            ObjectKlass klass = getSingleKlassArgument(arguments, node, typeError, arityError);
            EspressoContext context = EspressoContext.get(node);
            ObjectKlass result = context.getClassHierarchyOracle().readSingleImplementor(klass).get();
            if (result == null) {
                return StaticObject.NULL;
            }
            return result;
        }

        @Specialization(guards = "TO_GUEST_STRING.equals(member)")
        static Object toGuestString(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary stringInterop,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            try {
                String string = stringInterop.asString(arguments[0]);
                EspressoContext context = EspressoContext.get(node);
                return context.getMeta().toGuestString(string);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an string");
            }
        }

        @Specialization(guards = "MAKE_IDENTITY_HASH_CODE.equals(member)")
        static Object makeIdentityHashCode(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary intInterop,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof StaticObject o)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an espresso object as first argument");
            }
            int requestedValue;
            try {
                requestedValue = intInterop.asInt(arguments[1]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an int as second argument");
            }
            Meta meta = EspressoContext.get(node).getMeta();
            return Target_org_graalvm_continuations_IdentityHashCodes.setHashCode(o, requestedValue, meta, meta.getLanguage());
        }

        @Specialization(guards = "NEW_OBJECT_ARRAY.equals(member)")
        static Object newObjectArray(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary intInterop,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 3) {
                arityError.enter(node);
                throw ArityException.create(3, 3, arguments.length);
            }
            if (!(arguments[0] instanceof ObjectKlass elementKlass)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an espresso object class as first argument");
            }
            int dimensions;
            try {
                dimensions = intInterop.asInt(arguments[1]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an int as second argument");
            }
            int length;
            try {
                length = intInterop.asInt(arguments[2]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an int as third argument");
            }
            assert dimensions > 0;
            return elementKlass.getArrayKlass(dimensions).getComponentType().allocateReferenceArray(length);
        }

        @Specialization(guards = "NEW_PRIMITIVE_ARRAY.equals(member)")
        static Object newPrimitiveArray(JVMCIInteropHelper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary intInterop,
                        @Cached @Shared InlinedBranchProfile typeError,
                        @Cached @Shared InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert receiver != null;
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 3) {
                arityError.enter(node);
                throw ArityException.create(3, 3, arguments.length);
            }
            int typeChar;
            try {
                typeChar = intInterop.asInt(arguments[0]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected a primitive JavaKind type char as an int as first argument (not an int)");
            }
            JavaKind javaKind = JavaKind.fromPrimitiveOrVoidTypeCharOrNull((char) typeChar);
            if (javaKind == null || !javaKind.isPrimitive() || javaKind == JavaKind.Void) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected a primitive JavaKind type char as an int as first argument (not a valid type char or kind)");
            }
            int dimensions;
            try {
                dimensions = intInterop.asInt(arguments[1]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an int as second argument");
            }
            int length;
            try {
                length = intInterop.asInt(arguments[2]);
            } catch (UnsupportedMessageException e) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an int as third argument");
            }
            Meta meta = EspressoContext.get(node).getMeta();
            PrimitiveKlass elementType = switch (javaKind) {
                case Boolean -> meta._boolean;
                case Byte -> meta._byte;
                case Char -> meta._char;
                case Short -> meta._short;
                case Int -> meta._int;
                case Long -> meta._long;
                case Double -> meta._double;
                case Float -> meta._float;
                default -> {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(javaKind.toString());
                }
            };
            assert dimensions > 0;
            if (dimensions > 1) {
                return elementType.getArrayKlass(dimensions).getComponentType().allocateReferenceArray(length);
            } else {
                return elementType.allocatePrimitiveArray(length);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doUnknown(JVMCIInteropHelper receiver, String member, Object[] arguments) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }

        private static ObjectKlass getSingleKlassArgument(Object[] arguments, Node node, InlinedBranchProfile typeError, InlinedBranchProfile arityError)
                        throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof ObjectKlass klass)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments, "Expected an instance type");
            }
            return klass;
        }
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public boolean isMemberInvocable(String member) {
        return ALL_MEMBERS_SET.contains(member);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return ALL_MEMBERS;
    }
}
