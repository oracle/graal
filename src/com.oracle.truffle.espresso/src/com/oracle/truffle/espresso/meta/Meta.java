/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.meta;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.intrinsics.Type;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta {

    final EspressoContext context;

    public Meta(EspressoContext context) {
        this.context = context;
        OBJECT = knownKlass(Object.class);
        STRING = knownKlass(String.class);
        CLASS = knownKlass(java.lang.Class.class);
        BOOLEAN = knownKlass(boolean.class);
        BYTE = knownKlass(byte.class);
        CHAR = knownKlass(char.class);
        SHORT = knownKlass(short.class);
        FLOAT = knownKlass(float.class);
        INT = knownKlass(int.class);
        DOUBLE = knownKlass(double.class);
        LONG = knownKlass(long.class);
        VOID = knownKlass(void.class);

        BOXED_BOOLEAN = knownKlass(Boolean.class);
        BOXED_BYTE = knownKlass(Byte.class);
        BOXED_CHAR = knownKlass(Character.class);
        BOXED_SHORT = knownKlass(Short.class);
        BOXED_FLOAT = knownKlass(Float.class);
        BOXED_INT = knownKlass(Integer.class);
        BOXED_DOUBLE = knownKlass(Double.class);
        BOXED_LONG = knownKlass(Long.class);
        BOXED_VOID = knownKlass(Void.class);

        THROWABLE = knownKlass(Throwable.class);
        STACK_OVERFLOW_ERROR = knownKlass(StackOverflowError.class);
        OUT_OF_MEMORY_ERROR = knownKlass(OutOfMemoryError.class);

        CLONEABLE = knownKlass(Cloneable.class);
        SERIALIZABLE = knownKlass(Serializable.class);
    }

    public static Klass.WithInstance meta(StaticObject obj) {
        assert StaticObject.notNull(obj);
        return meta(obj.getKlass()).forInstance(obj);
    }

    public Meta.Klass meta(java.lang.Class<?> clazz) {
        return knownKlass(clazz);
    }

    public static Meta.Method meta(MethodInfo method) {
        return new Meta.Method(method);
    }

    public static Meta.Field meta(FieldInfo field) {
        return new Meta.Field(field);
    }

    public static Meta.Klass meta(com.oracle.truffle.espresso.impl.Klass klass) {
        return new Meta.Klass(klass);
    }

    public final Klass OBJECT;
    public final Klass STRING;
    public final Klass CLASS;

    // Primitives
    public final Klass BOOLEAN;
    public final Klass BYTE;
    public final Klass CHAR;
    public final Klass SHORT;
    public final Klass FLOAT;
    public final Klass INT;
    public final Klass DOUBLE;
    public final Klass LONG;
    public final Klass VOID;

    // Boxed
    public final Klass BOXED_BOOLEAN;
    public final Klass BOXED_BYTE;
    public final Klass BOXED_CHAR;
    public final Klass BOXED_SHORT;
    public final Klass BOXED_FLOAT;
    public final Klass BOXED_INT;
    public final Klass BOXED_DOUBLE;
    public final Klass BOXED_LONG;
    public final Klass BOXED_VOID;

    public final Klass STACK_OVERFLOW_ERROR;
    public final Klass OUT_OF_MEMORY_ERROR;
    public final Klass THROWABLE;

    public final Klass CLONEABLE;
    public final Klass SERIALIZABLE;

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: (host) known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    public StaticObject initEx(java.lang.Class<?> clazz) {
        StaticObject ex = throwableKlass(clazz).allocateInstance();
        meta(ex).method("<init>", void.class).invokeDirect();
        return ex;
    }

    public static StaticObject initEx(Meta.Klass clazz, String message) {
        StaticObject ex = clazz.allocateInstance();
        meta(ex).method("<init>", void.class, String.class).invoke(message);
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, String message) {
        StaticObject ex = throwableKlass(clazz).allocateInstance();
        meta(ex).method("<init>", void.class, String.class).invoke(message);
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, @Type(Throwable.class) StaticObject cause) {
        StaticObject ex = throwableKlass(clazz).allocateInstance();
        meta(ex).method("<init>", void.class, Throwable.class).invoke(cause);
        return ex;
    }

    public EspressoException throwEx(java.lang.Class<?> clazz) {
        throw new EspressoException(initEx(clazz));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, String message) {
        throw new EspressoException(initEx(clazz, message));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, @Type(Throwable.class) StaticObject cause) {
        throw new EspressoException(initEx(clazz, cause));
    }

    @CompilerDirectives.TruffleBoundary
    public Klass throwableKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert Throwable.class.isAssignableFrom(exceptionClass);
        return knownKlass(exceptionClass);
    }

    @CompilerDirectives.TruffleBoundary
    public Meta.Klass knownKlass(java.lang.Class<?> hostClass) {
        assert isKnownClass(hostClass);
        // Resolve classes using BCL.
        return meta(context.getRegistries().resolve(context.getTypeDescriptors().make(MetaUtil.toInternalName(hostClass.getName())), StaticObject.NULL));
    }

    @CompilerDirectives.TruffleBoundary
    public Meta.Klass loadKlass(String className, StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        return meta(context.getRegistries().resolve(context.getTypeDescriptors().make(MetaUtil.toInternalName(className)), classLoader));
    }

    @CompilerDirectives.TruffleBoundary
    public static String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        char[] value = ((StaticObjectArray) meta(str).declaredField("value").get()).unwrap();
        return createString(value);
    }

    @CompilerDirectives.TruffleBoundary
    public StaticObject toGuest(String str) {
        return toGuest(this, str);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject toGuest(Meta meta, String str) {
        if (str == null) {
            return StaticObject.NULL;
        }

        final char[] value = getStringValue(str);
        final int hash = getStringHash(str);

        StaticObject result = meta.STRING.metaNew().fields(
                        Field.set("value", StaticObjectArray.wrap(value)),
                        Field.set("hash", hash)).getInstance();

        // String.hashCode must be equivalent for host and guest.
        assert str.hashCode() == (int) meta(result).method("hashCode", int.class).invokeDirect();

        return result;
    }

    private static String createString(char[] value) {
        try {
            return STRING_CONSTRUCTOR.newInstance(value, true);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public Object toGuestBoxed(Object hostObject) {
        if (hostObject == null) {
            return StaticObject.NULL;
        }
        if (hostObject instanceof String) {
            return toGuest((String) hostObject);
        }
        if (hostObject instanceof StaticObject || (hostObject.getClass().isArray() && hostObject.getClass().getComponentType().isPrimitive())) {
            return hostObject;
        }

        if (Arrays.stream(JavaKind.values()).anyMatch(c -> c.toBoxedJavaClass() == hostObject.getClass())) {
            // boxed value
            return hostObject;
        }

        throw EspressoError.shouldNotReachHere(hostObject + " cannot be converted to guest world");
    }

    public Object toGuest(Object hostObject) {
        if (hostObject == null) {
            return StaticObject.NULL;
        }
        if (hostObject instanceof String) {
            return toGuest((String) hostObject);
        }
        if (hostObject instanceof StaticObject || (hostObject.getClass().isArray() && hostObject.getClass().getComponentType().isPrimitive())) {
            return hostObject;
        }

        throw EspressoError.shouldNotReachHere(hostObject + " cannot be converted to guest world");
    }

    public Object toHostBoxed(Object object, JavaKind kind) {
        assert object != null;
        if (object instanceof StaticObject) {
            StaticObject guestObject = (StaticObject) object;
            if (StaticObject.isNull(guestObject)) {
                return null;
            }
            if (guestObject == StaticObject.VOID) {
                return null;
            }
            if (guestObject instanceof StaticObjectArray) {
                return ((StaticObjectArray) guestObject).unwrap();
            }
            if (guestObject.getKlass() == STRING.klass) {
                return toHostString(guestObject);
            }
        }
        return object;
    }

    public Object toHost(StaticObject guestObject) {
        if (StaticObject.isNull(guestObject)) {
            return null;
        }
        if (guestObject == StaticObject.VOID) {
            return null;
        }
        // primitive array
        if (guestObject.getClass().isArray() && guestObject.getClass().getComponentType().isPrimitive()) {
            return guestObject;
        }
        if (guestObject.getKlass() == STRING.klass) {
            return toHost(guestObject);
        }
        throw EspressoError.shouldNotReachHere(guestObject + " cannot be converted to host world");
    }

    public static class Klass implements ModifiersProvider {
        private final com.oracle.truffle.espresso.impl.Klass klass;

        Klass(com.oracle.truffle.espresso.impl.Klass klass) {
            this.klass = klass;
        }

        public void safeInitialize() {
            try {
                klass.initialize();
            } catch (EspressoException e) {
                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ExceptionInInitializerError.class, e.getException());
            }
        }

        public Meta.Klass getComponentType() {
            return isArray() ? meta(klass.getComponentType()) : null;
        }

        public JavaKind kind() {
            return klass.getJavaKind();
        }

        public Meta getMeta() {
            return klass.getContext().getMeta();
        }

        public Meta.Klass getSuperclass() {
            com.oracle.truffle.espresso.impl.Klass superclass = klass.getSuperclass();
            return superclass != null ? meta(superclass) : null;
        }

        public Meta.Method[] methods(boolean includeInherited) {
            return methodStream(includeInherited).toArray(Meta.Method[]::new);
        }

        private Stream<Meta.Method> methodStream(boolean includeInherited) {
            Stream<Meta.Method> methods = Arrays.stream(klass.getDeclaredMethods()).map(Meta::meta);
            if (includeInherited && getSuperclass() != null) {
                methods = Stream.concat(methods, getSuperclass().methodStream(includeInherited));
            }
            return methods;
        }

        public Meta.Klass getSupertype() {
            if (isArray()) {
                Meta.Klass componentType = getComponentType();
                if (this.rawKlass() == getMeta().OBJECT.array().rawKlass() || componentType.isPrimitive()) {
                    return getMeta().OBJECT;
                }
                return componentType.getSupertype().array();
            }
            if (isInterface()) {
                return getMeta().OBJECT;
            }
            return getSuperclass();
        }

        /**
         * Determines if this type is either the same as, or is a superclass or superinterface of,
         * the type represented by the specified parameter. This method is identical to
         * {@link Class#isAssignableFrom(Class)} in terms of the value return for this type.
         */
        @CompilerDirectives.TruffleBoundary
        public boolean isAssignableFrom(Meta.Klass other) {
            if (this.rawKlass() == other.rawKlass()) {
                return true;
            }
            if (this.isPrimitive() || other.isPrimitive()) {
                // Reference equality is enough within the same context.
                return this == other;
            }
            if (this.isArray() && other.isArray()) {
                return getComponentType().isAssignableFrom(other.getComponentType());
            }
            if (isInterface()) {
                return other.getInterfacesStream(true).anyMatch(i -> i.rawKlass() == this.rawKlass());
            }
            return other.getSupertypesStream(true).anyMatch(k -> k.rawKlass() == this.rawKlass());
        }

        @CompilerDirectives.TruffleBoundary
        private boolean isPrimaryType() {
            assert !isPrimitive();
            if (isArray())
                return getElementalType().isPrimaryType();
            return !isInterface();
        }

        @CompilerDirectives.TruffleBoundary
        public Meta.Klass getElementalType() {
            if (!isArray()) {
                return null;
            }
            return meta(klass.getElementalType());
        }

        @CompilerDirectives.TruffleBoundary
        private Stream<Meta.Klass> getSupertypesStream(boolean includeOwn) {
            Meta.Klass supertype = getSupertype();
            Stream<Meta.Klass> supertypes;
            if (supertype != null) {
                supertypes = supertype.getSupertypesStream(true);
            } else {
                supertypes = Stream.empty();
            }
            if (includeOwn) {
                return Stream.concat(Stream.of(this), supertypes);
            }
            return supertypes;
        }

        @CompilerDirectives.TruffleBoundary
        private Stream<Meta.Klass> getInterfacesStream(boolean includeInherited) {
            Stream<Meta.Klass> interfaces = Stream.of(klass.getInterfaces()).map(Meta::meta);
            Meta.Klass superclass = getSuperclass();
            if (includeInherited && superclass != null) {
                interfaces = Stream.concat(interfaces, superclass.getInterfacesStream(includeInherited));
            }
            if (includeInherited) {
                interfaces = interfaces.flatMap(i -> Stream.concat(Stream.of(i), i.getInterfacesStream(includeInherited)));
            }
            return interfaces;
        }

        public List<Klass> getInterfaces(boolean includeSuperclasses) {
            return getInterfacesStream(includeSuperclasses).collect(collectingAndThen(toList(), Collections::unmodifiableList));
        }

        @CompilerDirectives.TruffleBoundary
        public StaticObject allocateInstance() {
            assert !klass.isArray();
            return klass.getContext().getInterpreterToVM().newObject(klass);
        }

        public String getName() {
            return MetaUtil.internalNameToJava(klass.getName(), true, true);
        }

        public String getInternalName() {
            return klass.getName();
        }

        public boolean isArray() {
            return klass.isArray();
        }

        public boolean isPrimitive() {
            return klass.isPrimitive();
        }

        @CompilerDirectives.TruffleBoundary
        public Object allocateArray(int length) {
            return klass.getContext().getInterpreterToVM().newArray(klass, length);
        }

        @CompilerDirectives.TruffleBoundary
        public Object allocateArray(int length, IntFunction<StaticObject> generator) {
            // TODO(peterssen): Store check is missing.
            StaticObject[] array = new StaticObject[length];
            for (int i = 0; i < array.length; ++i) {
                array[i] = generator.apply(i);
            }
            return new StaticObjectArray(klass.getArrayClass(), array);
        }

        public Optional<Method.WithInstance> getClassInitializer() {
            MethodInfo clinit = klass.findDeclaredMethod("<clinit>", void.class);
            return Optional.ofNullable(clinit).map(mi -> {
                Meta.Method m = meta(mi);
                assert m.isClassInitializer();
                return m.forInstance(klass.getStatics());
            });
        }

        @CompilerDirectives.TruffleBoundary
        public Meta.Method method(String name, Class<?> returnType, Class<?>... parameterTypes) {
            SignatureDescriptor target = klass.getContext().getSignatureDescriptors().create(returnType, parameterTypes);
            MethodInfo found = Arrays.stream(klass.getDeclaredMethods()).filter(m -> m.getName().equals(name) && m.getSignature().equals(target)).findFirst().orElse(null);
            if (found == null) {
                if (getSuperclass() != null) {
                    return getSuperclass().method(name, returnType, parameterTypes);
                }
            }
            return found == null ? null : new Meta.Method(found);
        }

        public com.oracle.truffle.espresso.impl.Klass rawKlass() {
            return klass;
        }

        public Method.WithInstance staticMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
            Meta.Method m = method(name, returnType, parameterTypes);
            assert m.isStatic();
            return m.forInstance(m.getDeclaringClass().rawKlass().getStatics());
        }

        public Meta.Field declaredField(String name) {
            // TODO(peterssen): Improve lookup performance.
            for (FieldInfo f : klass.getDeclaredFields()) {
                if (name.equals(f.getName())) {
                    return new Meta.Field(f);
                }
            }
            return null;
        }

        public Meta.Field field(String name) {
            // TODO(peterssen): Improve lookup performance.
            Field f = declaredField(name);
            if (f == null) {
                if (getSuperclass() != null) {
                    return getSuperclass().field(name);
                }
            }
            return f;
        }

        public Field.WithInstance staticField(String name) {
            assert klass.isInitialized();
            return Klass.this.declaredField(name).forInstance(klass.getStatics());
        }

        public WithInstance forInstance(StaticObject instance) {
            return new WithInstance(instance);
        }

        public WithInstance metaNew() {
            return forInstance(allocateInstance());
        }

        @Override
        public int getModifiers() {
            return klass.getModifiers();
        }

        public class WithInstance {
            private final StaticObject instance;

            WithInstance(StaticObject obj) {
                assert StaticObject.notNull(obj);
                this.instance = obj;
            }

            public Field.WithInstance declaredField(String name) {
                return Klass.this.declaredField(name).forInstance(instance);
            }

            public Field.WithInstance field(String name) {
                return Klass.this.field(name).forInstance(instance);
            }

            public WithInstance fields(Field.SetField... setters) {
                for (Field.SetField setter : setters) {
                    setter.action.accept(declaredField(setter.name));
                }
                return this;
            }

            public Method.WithInstance method(String name, Class<?> returnType, Class<?>... parameterTypes) {
                return Klass.this.method(name, returnType, parameterTypes).forInstance(instance);
            }

            public StaticObject getInstance() {
                return instance;
            }

            public String guestToString() {
                return toHostString((StaticObject) method("toString", String.class).invokeDirect());
            }

            public Meta getMeta() {
                return instance.getKlass().getContext().getMeta();
            }
        }

        public Klass array() {
            return new Klass(klass.getArrayClass());
        }
    }

    public static class Method implements ModifiersProvider {
        private final MethodInfo method;

        Method(MethodInfo method) {
            assert method != null;
            this.method = method;
        }

        public Meta.Klass[] getParameterTypes() {
            com.oracle.truffle.espresso.impl.Klass[] params = method.getParameterTypes();
            Meta.Klass[] metaParams = new Meta.Klass[params.length];
            for (int i = 0; i < params.length; i++) {
                metaParams[i] = meta(params[i]);
            }
            return metaParams;
        }

        public int getParameterCount() {
            return method.getParameterCount();
        }

        public Meta.Klass getReturnType() {
            return meta(method.getReturnType());
        }

        public Meta.Klass getDeclaringClass() {
            return meta(method.getDeclaringClass());
        }

        public MethodInfo rawMethod() {
            return method;
        }

        public Meta.Method.WithInstance asStatic() {
            assert isStatic();
            return forInstance(method.getDeclaringClass().getStatics());
        }

        /**
         * Invoke guest method, parameters and return value are converted to host world. Primitives,
         * primitive arrays are shared, and are passed verbatim, conversions are provided for String
         * and StaticObject.NULL/null. There's no parameter casting based on the method's signature,
         * widening nor narrowing.
         */
        @CompilerDirectives.TruffleBoundary
        public Object invoke(Object self, Object... args) {
            assert args.length == method.getSignature().getParameterCount(false);
            assert !isStatic() || ((StaticObjectImpl) self).isStatic();
            Meta meta = method.getContext().getMeta();

            final Object[] filteredArgs;
            if (isStatic()) {
                filteredArgs = new Object[args.length];
                for (int i = 0; i < filteredArgs.length; ++i) {
                    filteredArgs[i] = meta.toGuestBoxed(args[i]);
                }
            } else {
                filteredArgs = new Object[args.length + 1];
                filteredArgs[0] = meta.toGuestBoxed(self);
                for (int i = 1; i < filteredArgs.length; ++i) {
                    filteredArgs[i] = meta.toGuestBoxed(args[i - 1]);
                }
            }
            return meta.toHostBoxed(method.getCallTarget().call(filteredArgs), method.getSignature().resultKind());
        }

        /**
         * Invoke a guest method without parameter/return type conversion. There's no parameter
         * casting based on the method's signature, widening nor narrowing.
         */
        @CompilerDirectives.TruffleBoundary
        public Object invokeDirect(Object self, Object... args) {
            if (isStatic()) {
                assert args.length == method.getSignature().getParameterCount(false);
                return method.getCallTarget().call(args);
            } else {
                assert args.length + 1 /* self */ == method.getSignature().getParameterCount(!method.isStatic());
                Object[] fullArgs = new Object[args.length + 1];
                System.arraycopy(args, 0, fullArgs, 1, args.length);
                fullArgs[0] = self;
                return method.getCallTarget().call(fullArgs);
            }
        }

        public String getName() {
            return method.getName();
        }

        public boolean isClassInitializer() {
            assert method.getSignature().resultKind() == JavaKind.Void;
            assert isStatic();
            assert method.getSignature().getParameterCount(false) == 0;
            return "<clinit>".equals(getName());
        }

        public boolean isConstructor() {
            return method.isConstructor();
        }

        public Meta.Method.WithInstance forInstance(StaticObject obj) {
            return new WithInstance(obj);
        }

        @Override
        public int getModifiers() {
            return method.getModifiers();
        }

        public class WithInstance {
            private final StaticObject instance;

            WithInstance(StaticObject obj) {
                assert !isStatic() || ((StaticObjectImpl) obj).isStatic();
                assert StaticObject.notNull(obj);
                instance = obj;
            }

            public Object invoke(Object... args) {
                return Method.this.invoke(instance, args);
            }

            public Object invokeDirect(Object... args) {
                return Method.this.invokeDirect(instance, args);
            }
        }
    }

    public static class Field implements ModifiersProvider {
        private final FieldInfo field;

        @Override
        public String toString() {
            return "field " + field.getName() + " : " + field.getType().getName();
        }

        public static SetField set(String name, Object value) {
            assert value != null;
            return new SetField(name, value);
        }

        public static SetField setNull(String name) {
            return set(name, StaticObject.NULL);
        }

        public FieldInfo rawField() {
            return field;
        }

        @Override
        public int getModifiers() {
            return field.getModifiers();
        }

        public int getSlot() {
            return field.getSlot();
        }

        public static class SetField extends FieldAction {
            public SetField(String name, Object value) {
                super(name, f -> f.set(value));
                assert value != null;
            }
        }

        public String getName() {
            return field.getName();
        }

        public Meta.Klass getDeclaringClass() {
            return new Meta.Klass(field.getDeclaringClass());
        }

        public static class FieldAction {
            final String name;
            final Consumer<WithInstance> action;

            public FieldAction(String name, Consumer<WithInstance> action) {
                this.name = name;
                this.action = action;
            }
        }

        public Meta.Klass getType() {
            return meta(field.getType());
        }

        Field(FieldInfo field) {
            this.field = field;
        }

        public Object get(StaticObject self) {
            InterpreterToVM vm = field.getDeclaringClass().getContext().getInterpreterToVM();
            switch (field.getKind()) {
                case Boolean:
                    return vm.getFieldBoolean(self, field);
                case Byte:
                    return vm.getFieldByte(self, field);
                case Short:
                    return vm.getFieldShort(self, field);
                case Char:
                    return vm.getFieldChar(self, field);
                case Int:
                    return vm.getFieldInt(self, field);
                case Float:
                    return vm.getFieldFloat(self, field);
                case Long:
                    return vm.getFieldLong(self, field);
                case Double:
                    return vm.getFieldDouble(self, field);
                case Object:
                    return vm.getFieldObject(self, field);
                default:
                    throw EspressoError.shouldNotReachHere();
            }
        }

        public void setNull(StaticObject self) {
            set(self, StaticObject.NULL);
        }

        public void set(StaticObject self, Object value) {
            InterpreterToVM vm = field.getDeclaringClass().getContext().getInterpreterToVM();
            switch (field.getKind()) {
                case Boolean:
                    vm.setFieldBoolean((boolean) value, self, field);
                    break;
                case Byte:
                    vm.setFieldByte((byte) value, self, field);
                    break;
                case Short:
                    vm.setFieldShort((short) value, self, field);
                    break;
                case Char:
                    vm.setFieldChar((char) value, self, field);
                    break;
                case Int:
                    vm.setFieldInt((int) value, self, field);
                    break;
                case Float:
                    vm.setFieldFloat((float) value, self, field);
                    break;
                case Long:
                    vm.setFieldLong((long) value, self, field);
                    break;
                case Double:
                    vm.setFieldDouble((double) value, self, field);
                    break;
                case Object:
                    vm.setFieldObject((StaticObject) value, self, field);
                    break;
                default:
                    throw EspressoError.shouldNotReachHere();
            }
        }

        public Meta.Field.WithInstance forInstance(StaticObject obj) {
            return new WithInstance(obj);
        }

        public class WithInstance {
            private final StaticObject instance;

            WithInstance(StaticObject obj) {
                assert obj != null;
                assert obj != StaticObject.NULL;
                instance = obj;
            }

            public Meta.Field getField() {
                return Field.this;
            }

            public Object get() {
                return Field.this.get(this.instance);
            }

            public void setNull() {
                Field.this.set(instance, StaticObject.NULL);
            }

            public void set(Object value) {
                Field.this.set(instance, value);
            }
        }
    }

    // region Low level host String access

    private static java.lang.reflect.Field STRING_VALUE;
    private static java.lang.reflect.Field STRING_HASH;
    private static Constructor<String> STRING_CONSTRUCTOR;

    static {
        try {
            STRING_VALUE = String.class.getDeclaredField("value");
            STRING_VALUE.setAccessible(true);
            STRING_HASH = String.class.getDeclaredField("hash");
            STRING_HASH.setAccessible(true);
            STRING_CONSTRUCTOR = String.class.getDeclaredConstructor(char[].class, boolean.class);
            STRING_CONSTRUCTOR.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static char[] getStringValue(String s) {
        try {
            return (char[]) STRING_VALUE.get(s);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static int getStringHash(String s) {
        try {
            return (int) STRING_HASH.get(s);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // endregion

    public static boolean isEspressoReference(Object obj) {
        return (obj != null) && (obj instanceof StaticObject || (obj.getClass().isArray() && obj.getClass().getComponentType().isPrimitive()));
    }
}
