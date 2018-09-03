package com.oracle.truffle.espresso.meta;

import java.util.function.Consumer;

import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Introspection API for to access the guest world from the host, provides seamless object
 * conversions, known class resolution. Separates guest object data from Java semantics (e.g.
 * resolving methods).
 *
 * It provides seamless conversions from host to guest classes for a well known subset (e.g. common
 * types and exceptions).
 */
public final class Meta {
    final EspressoContext context;

    public Meta(EspressoContext context) {
        this.context = context;
    }

    public static Klass.WithInstance meta(StaticObject obj) {
        assert obj != null;
        assert obj != StaticObject.NULL;
        return meta(obj.getKlass()).forInstance(obj);
    }

    public Klass meta(java.lang.Class clazz) {
        return knownKlass(clazz);
    }

    public static Klass meta(com.oracle.truffle.espresso.impl.Klass klass) {
        return new Klass(klass);
    }

    public Klass.WithInstance meta(Object obj) {
        assert obj != null;
        assert obj != StaticObject.NULL;
        if (obj instanceof StaticObject) {
            assert ((StaticObject) obj).getKlass().getContext() == context;
            return Meta.meta((StaticObject) obj);
        }

        throw EspressoError.unimplemented();
        // Arrays of primitives int[] are "adopted" by the Meta context.
        // assert obj.getClass().isArray() && obj.getClass().getComponentType().isPrimitive();
        // return new Meta.Klass(((StaticObject) obj).getKlass()).forInstance(obj);
    }

    public final Klass OBJECT = knownKlass(Object.class);
    public final Klass STRING = knownKlass(String.class);
    public final Klass CLASS = knownKlass(java.lang.Class.class);

    // Primitives
    public final Klass BOOLEAN = knownKlass(boolean.class);
    public final Klass BYTE = knownKlass(byte.class);
    public final Klass CHAR = knownKlass(char.class);
    public final Klass SHORT = knownKlass(short.class);
    public final Klass FLOAT = knownKlass(float.class);
    public final Klass INT = knownKlass(int.class);
    public final Klass DOUBLE = knownKlass(double.class);
    public final Klass LONG = knownKlass(long.class);

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    public Klass exceptionKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert exceptionClass.isAssignableFrom(Exception.class);
        return knownKlass(exceptionClass);
    }

    public Klass knownKlass(java.lang.Class hostClass) {
        assert isKnownClass(hostClass);
        // Resolve classes using BCL.
        return meta(context.getRegistries().resolve(context.getTypeDescriptors().make(MetaUtil.toInternalName(hostClass.getName())), null));
    }

    public static String toHost(StaticObject str) {
        assert str != null;
        if (str == StaticObject.NULL) {
            return null;
        }
        char[] value = (char[]) meta(str).field("value").get();
        return createString(value);
    }

    public StaticObject toGuest(String str) {
        if (str == null) {
            return StaticObject.NULL;
        }

        final char[] value = getStringValue(str);
        final int hash = getStringHash(str);

        return meta(STRING.allocateInstance()).fields(
                        Field.set("value", value),
                        Field.set("hash", hash)).getInstance();
    }

    private static char[] getStringValue(String s) {
        return null;
    }

    private static int getStringHash(String s) {
        return 0;
    }

    private static String createString(char[] value) {
        return new String(value);
    }

    private Object toGuest(Object hostObject) {
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

    private Object toHost(Object guestObject) {
        assert guestObject != null;
        if (guestObject == StaticObject.NULL) {
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

    public static class Method {
        private final MethodInfo method;

        Method(MethodInfo method) {
            this.method = method;
        }

        // Call with parameter conversion
        public Object invoke(Object self, Object... parameters) {

        }

        // no conversions are performed
        public Object invokeDirect(Object self, Object... parameters) {
            assert parameters.length == method.getSignature().getParameterCount(!method.isStatic());
            assert !method.isStatic() || self == null;
            if (method.isStatic()) {
                return method.getCallTarget().call(parameters);
            } else {
                Object[] args = new Object[parameters.length + 1];
                System.arraycopy(parameters, 0, args, 1, parameters.length);
                args[0] = self;
                return method.getCallTarget().call(args);
            }
        }

        WithInstance forInstance(StaticObject obj) {
            return new WithInstance(obj);
        }

        public class WithInstance {
            private final StaticObject instance;

            WithInstance(StaticObject obj) {
                assert obj != null;
                assert obj != StaticObject.NULL;
                instance = obj;
            }

            public Object invoke(Object... parameters) {
                return Method.this.invoke(instance, parameters);
            }

            public Object invokeDirect(Object self, Object... parameters) {
                return Method.this.invokeDirect(instance, parameters);
            }
        }
    }

    public static class Klass {
        private final com.oracle.truffle.espresso.impl.Klass klass;

        Klass(com.oracle.truffle.espresso.impl.Klass klass) {
            this.klass = klass;
        }

        public StaticObject allocateInstance() {
            assert !klass.isArray();
            return klass.getContext().getVm().newObject(klass);
        }

        public StaticObject allocateArray(int length) {
            return klass.getContext().getVm().newArray(klass, length);
        }

        public Method method(String name, Class<?> returnType, Class<?>... parameterTypes) {
            return null;
        }

        public Field field(String name) {
            return null;
        }

        public WithInstance forInstance(StaticObject instance) {
            return new WithInstance(instance);
        }

        public WithInstance metaNew() {
            return forInstance(allocateInstance());
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
                return toHost((StaticObject) method("toString", null).invoke());
            }
        }

        public Klass array() {
            return new Klass(klass.getArrayClass());
        }
    }

    public static class Field {
        private final FieldInfo field;

        public static SetField set(String name, Object value) {
            assert value != null;
            return new SetField(name, value);
        }

        public static SetField setNull(String name) {
            return set(name, StaticObject.NULL);
        }

        public static class SetField extends FieldAction {
            public SetField(String name, Object value) {
                super(name, f -> f.set(value));
                assert value != null;
            }
        }

        public static class FieldAction {
            final String name;
            final Consumer<WithInstance> action;

            public FieldAction(String name, Consumer<WithInstance> action) {
                this.name = name;
                this.action = action;
            }
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
            }
            throw EspressoError.shouldNotReachHere();
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
            }
            throw EspressoError.shouldNotReachHere();
        }

        WithInstance forInstance(StaticObject obj) {
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
}
