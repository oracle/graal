package com.oracle.truffle.espresso.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.types.SignatureDescriptor;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions). *
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
    }

    public static Klass.WithInstance meta(StaticObject obj) {
        assert obj != null;
        assert obj != StaticObject.NULL;
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

    public Klass meta(Object obj) {
        assert obj != null;
        assert obj != StaticObject.NULL;
        if (obj instanceof StaticObject) {
            assert ((StaticObject) obj).getKlass().getContext() == context;
            return meta(((StaticObject) obj).getKlass());
        }

        if (obj instanceof int[]) {
            return INT.array();
        } else if (obj instanceof byte[]) {
            return BYTE.array();
        } else if (obj instanceof boolean[]) {
            return BOOLEAN.array();
        } else if (obj instanceof long[]) {
            return LONG.array();
        } else if (obj instanceof float[]) {
            return FLOAT.array();
        } else if (obj instanceof double[]) {
            return DOUBLE.array();
        } else if (obj instanceof char[]) {
            return CHAR.array();
        } else if (obj instanceof short[]) {
            return SHORT.array();
        }

        throw EspressoError.shouldNotReachHere();
        // Arrays of primitives int[] are "adopted" by the Meta context.
        // assert obj.getClass().isArray() && obj.getClass().getComponentType().isPrimitive();
        // return new Meta.Klass(((StaticObject) obj).getKlass()).forInstance(obj);
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

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    @CompilerDirectives.TruffleBoundary
    public Klass exceptionKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert Exception.class.isAssignableFrom(exceptionClass);
        return knownKlass(exceptionClass);
    }

    @CompilerDirectives.TruffleBoundary
    public Meta.Klass knownKlass(java.lang.Class<?> hostClass) {
        assert isKnownClass(hostClass);
        // Resolve classes using BCL.
        return meta(context.getRegistries().resolve(context.getTypeDescriptors().make(MetaUtil.toInternalName(hostClass.getName())), null));
    }

    @CompilerDirectives.TruffleBoundary
    public static String toHost(StaticObject str) {
        assert str != null;
        if (str == StaticObject.NULL) {
            return null;
        }
        char[] value = (char[]) meta(str).field("value").get();
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
                        Field.set("value", value),
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

    public Object toHost(Object guestObject) {
        // guestObject can be null for void.
        // assert guestObject != null;
        if (guestObject == null) {
            return null;
        }
        if (guestObject == StaticObject.NULL) {
            return null;
        }
        if (guestObject == StaticObject.VOID) {
            return null;
        }
        // primitive array
        if (guestObject.getClass().isArray() && guestObject.getClass().getComponentType().isPrimitive()) {
            return guestObject;
        }
        if (guestObject instanceof StaticObject) {
            if (((StaticObject) guestObject).getKlass() == STRING.klass) {
                return toHost((StaticObject) guestObject);
            }
        }

        throw EspressoError.shouldNotReachHere(guestObject + " cannot be converted to host world");
    }

    public static class Klass implements ModifiersProvider {
        private final com.oracle.truffle.espresso.impl.Klass klass;

        Klass(com.oracle.truffle.espresso.impl.Klass klass) {
            this.klass = klass;
        }

        public Meta.Klass getComponentType() {
            return isArray() ? meta(klass.getComponentType()) : null;
        }

        public Meta getMeta() {
            return klass.getContext().getMeta();
        }

        public Meta.Klass getSuperclass() {
            com.oracle.truffle.espresso.impl.Klass superclass = klass.getSuperclass();
            return superclass != null ? meta(superclass) : null;
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
         * Determines if this type is either the same as, or is a superclass or superinterface of, the
         * type represented by the specified parameter. This method is identical to
         * {@link Class#isAssignableFrom(Class)} in terms of the value return for this type.
         */
        public boolean isAssignableFrom(Meta.Klass other) {
            assert !isPrimitive() && !other.isPrimitive();
            if (this.rawKlass() == other.rawKlass()) {
                return true;
            }
            if (isInterface()) {
                return other.getInterfacesStream(true).anyMatch(i -> i.rawKlass() == this.rawKlass());
            }
            if (!isPrimaryType() || !other.isPrimaryType()) {
                throw EspressoError.unimplemented();
            }
            return other.getSupertypesStream(true).anyMatch(k -> k.rawKlass() == this.rawKlass());
        }

        @CompilerDirectives.TruffleBoundary
        private boolean isPrimaryType() {
            assert !isPrimitive();
            if (isArray()) return getElementalType().isPrimaryType();
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
        private Stream<Meta.Klass> getInterfacesStream(boolean includeSuperclasses) {
            Stream<Meta.Klass> interfaces = Stream.of(klass.getInterfaces()).map(Meta::meta);
            Meta.Klass superclass = getSuperclass();
            if (includeSuperclasses && superclass != null) {
                interfaces = Stream.concat(interfaces, superclass.getInterfacesStream(includeSuperclasses));
            }
            return interfaces;
        }

        public List<Klass> getInterfaces(boolean includeSuperclasses) {
            return getInterfacesStream(includeSuperclasses).collect(collectingAndThen(toList(), Collections::unmodifiableList));
        }

        @CompilerDirectives.TruffleBoundary
        public StaticObject allocateInstance() {
            assert !klass.isArray();
            return klass.getContext().getVm().newObject(klass);
        }

        public boolean isArray() {
            return klass.isArray();
        }

        public boolean isPrimitive() {
            return klass.isPrimitive();
        }

        public Object allocateArray(int length) {
            return klass.getContext().getVm().newArray(klass, length);
        }

        public Object allocateArray(int length, IntFunction<Object> generator) {
            StaticObjectArray arr = (StaticObjectArray) klass.getContext().getVm().newArray(klass, length);
            // TODO(peterssen): Store check is missing.
            Arrays.setAll(arr.getWrapped(), generator);
            return arr;
        }

        public Optional<Method.WithInstance> getClassInitializer() {
            MethodInfo clinit = klass.findDeclaredMethod("<clinit>", void.class);
            return Optional.ofNullable(clinit).map(mi -> {
                Meta.Method m = meta(mi);
                assert m.isClassInitializer();
                return m.forInstance(klass.getStatics());
            });
        }

        public Meta.Method method(String name, Class<?> returnType, Class<?>... parameterTypes) {
            SignatureDescriptor target = klass.getContext().getSignatureDescriptors().create(returnType, parameterTypes);

            return new Meta.Method(
                            Arrays.stream(klass.getDeclaredMethods()).filter(m -> m.getName().equals(name) && m.getSignature().equals(target)).findFirst().orElse(null));
        }

        public com.oracle.truffle.espresso.impl.Klass rawKlass() {
            return klass;
        }

        public Method.WithInstance staticMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
            Meta.Method m = method(name, returnType, parameterTypes);
            assert m.isStatic();
            return m.forInstance(m.getDeclaringClass().rawKlass().getStatics());
        }

        public Meta.Field field(String name) {
            // TODO(peterssen): Improve lookup performance.
            return new Meta.Field(Arrays.stream(klass.getDeclaredFields()).filter(f -> name.equals(f.getName())).findAny().orElse(null));
        }

        public Field.WithInstance staticField(String name) {
            assert klass.isInitialized();
            return Klass.this.field(name).forInstance(klass.getStatics());
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
                assert obj != null;
                assert obj != StaticObject.NULL;
                this.instance = obj;
            }

            public Field.WithInstance field(String name) {
                return Klass.this.field(name).forInstance(instance);
            }

            public WithInstance fields(Field.SetField... setters) {
                for (Field.SetField setter : setters) {
                    setter.action.accept(field(setter.name));
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
                return toHost((StaticObject) method("toString", String.class).invokeDirect());
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
            this.method = method;
        }

        public Meta.Klass[] getParameterTypes() {
            return Arrays.stream(method.getParameterTypes()).map(Meta::meta).toArray(Meta.Klass[]::new);
        }

        public int getParameterCount() {
            return method.getParameterCount();
        }

        public Meta.Klass getDeclaringClass() {
            return meta(method.getDeclaringClass());
        }

        public MethodInfo rawMethod() {
            return method;
        }

        /**
         * Invoke guest method, parameters and return value are converted to host world. Primitives,
         * primitive arrays are shared, and are passed verbatim, conversions are provided for String
         * and StaticObject.NULL/null. There's no parameter casting based on the method's signature,
         * widening nor narrowing.
         */
        @CompilerDirectives.TruffleBoundary
        public Object invoke(Object self, Object... parameters) {
            assert parameters.length == method.getSignature().getParameterCount(!method.isStatic());
            assert !isStatic() || self == null;
            Meta meta = method.getContext().getMeta();
            if (isStatic()) {
                Object[] args = new Object[parameters.length + 1];
                for (int i = 1; i < args.length; ++i)
                    args[i] = meta.toGuest(parameters[i]);
                return meta.toHost(method.getCallTarget().call(parameters));
            } else {
                Object[] args = new Object[parameters.length + 1];
                for (int i = 1; i < args.length; ++i)
                    args[i] = meta.toGuest(parameters[i]);
                args[0] = meta.toGuest(self);
                return meta.toHost(method.getCallTarget().call(args));
            }
        }

        /**
         * Invoke a guest method without parameter/return type conversion. There's no parameter
         * casting based on the method's signature, widening nor narrowing.
         */
        @CompilerDirectives.TruffleBoundary
        public Object invokeDirect(Object self, Object... parameters) {
            assert !isStatic() || ((StaticObjectImpl) self).isStatic();
            if (isStatic()) {
                assert parameters.length == method.getSignature().getParameterCount(!method.isStatic());
                return method.getCallTarget().call(parameters);
            } else {
                assert parameters.length + 1 /* self */ == method.getSignature().getParameterCount(!method.isStatic());
                Object[] args = new Object[parameters.length + 1];
                System.arraycopy(parameters, 0, args, 1, parameters.length);
                args[0] = self;
                return method.getCallTarget().call(args);
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
                assert obj != StaticObject.NULL;
                instance = obj;
            }

            @CompilerDirectives.TruffleBoundary
            public Object invoke(Object... parameters) {
                return Method.this.invoke(instance, parameters);
            }

            @CompilerDirectives.TruffleBoundary
            public Object invokeDirect(Object... parameters) {
                return Method.this.invokeDirect(instance, parameters);
            }
        }
    }

    public static class Field implements ModifiersProvider {
        private final FieldInfo field;

        public static SetField set(String name, Object value) {
            assert value != null;
            return new SetField(name, value);
        }

        public static SetField setNull(String name) {
            return set(name, StaticObject.NULL);
        }

        @Override
        public int getModifiers() {
            return field.getModifiers();
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
            InterpreterToVM vm = field.getDeclaringClass().getContext().getVm();
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
            InterpreterToVM vm = field.getDeclaringClass().getContext().getVm();
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
                    vm.setFieldObject(value, self, field);
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
            e.printStackTrace();
        }
    }

    private static char[] getStringValue(String s) {
        try {
            return (char[]) STRING_VALUE.get(s);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getStringHash(String s) {
        try {
            return (int) STRING_HASH.get(s);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // endregion

    public static boolean isEspressoReference(Object obj) {
        return (obj != null) && (obj instanceof StaticObject || (obj.getClass().isArray() && obj.getClass().getComponentType().isPrimitive()));
    }
}
