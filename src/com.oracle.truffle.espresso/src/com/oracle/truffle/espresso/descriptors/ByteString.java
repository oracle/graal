package com.oracle.truffle.espresso.descriptors;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.ByteSequence;
import com.oracle.truffle.espresso.impl.Stable;
import com.oracle.truffle.espresso.jni.Utf8;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Modified-UTF8 byte string for internal use by Espresso.
 *
 * An improvement over j.l.String for internal symbols:
 * <ul>
 * <li>Compact representation
 * <li>Hard/clear separation between guest/host symbols
 * <li>0-cost tagging for added type-safety
 * <li>Lazy decoding
 * <li>Effectively partial-evaluation constant {@link CompilationFinal}
 * </ul>
 *
 * @param <T> generic tag for extra type-safety at the Java level.
 */
public final class ByteString<T> implements ByteSequence {

    @SuppressWarnings("rawtypes") public static final ByteString[] EMPTY_ARRAY = new ByteString[0];

    @SuppressWarnings("unchecked")
    public static <S> ByteString<S>[] emptyArray() {
        return (ByteString<S>[]) EMPTY_ARRAY;
    }

    @Stable @CompilationFinal(dimensions = 1) //
    private final byte[] value;

    private int hash;

    public ByteString<T> substring(int from) {
        return substring(from, length());
    }

    public ByteString<T> substring(int from, int to) {
        return new ByteString<>(Arrays.copyOfRange(value, from, to));
    }

    public static void copyBytes(ByteString<?> src, int srcPos, byte[] dest, int destPos, int length) {
        System.arraycopy(src.value, srcPos, dest, destPos, length);
    }

    public static class ModifiedUTF8 {
    }

    private static final ConcurrentHashMap<ByteString<?>, ByteString<?>> GLOBAL_SYMBOLS = new ConcurrentHashMap<>();

    private static ByteString<Name> makeGlobalName(String value) {
        ByteString<?> pepe = ByteString.fromJavaString(value);
        ByteString<?> prev = GLOBAL_SYMBOLS.putIfAbsent(pepe, pepe);
        return (ByteString<Name>) ((prev == null) ? pepe : prev);
    }

    static ByteString<Type> makeGlobalType(String value) {
        ByteString<?> pepe = ByteString.fromJavaString(value);
        ByteString<?> prev = GLOBAL_SYMBOLS.putIfAbsent(pepe, pepe);
        return (ByteString<Type>) ((prev == null) ? pepe : prev);
    }

    private static ByteString<Signature> makeGlobalSignature(ByteString<Type> returnType, ByteString<Type>... parameterTypes) {
        ByteString<?> pepe = Signatures.nonCachedMake(returnType, parameterTypes);
        ByteString<?> prev = GLOBAL_SYMBOLS.putIfAbsent(pepe, pepe);
        return (ByteString<Signature>) ((prev == null) ? pepe : prev);
    }


    static ByteString<Type> makeGlobalType(Class<?> clazz) {
        return makeGlobalType(Types.fromCanonicalClassName(clazz.getCanonicalName()));
    }

    public static class Name extends ModifiedUTF8 {

        public static final ByteString<Name> INIT = makeGlobalName("<init>");
        public static final ByteString<Name> CLINIT = makeGlobalName("<clinit>");

        public static final ByteString<Name> backtrace = makeGlobalName("backtrace");
        public static final ByteString<Name> clazz = makeGlobalName("clazz");
        public static final ByteString<Name> root = makeGlobalName("root");
        public static final ByteString<Name> value = makeGlobalName("value");
        public static final ByteString<Name> hash = makeGlobalName("hash");
        public static final ByteString<Name> hashCode = makeGlobalName("hashCode");
        public static final ByteString<Name> length = makeGlobalName("length");
        public static final ByteString<Name> findNative = makeGlobalName("findNative");
        public static final ByteString<Name> getSystemClassLoader = makeGlobalName("getSystemClassLoader");
        public static final ByteString<Name> valueOf = makeGlobalName("valueOf");
        public static final ByteString<Name> wrap = makeGlobalName("wrap");
        public static final ByteString<Name> initializeSystemClass = makeGlobalName("initializeSystemClass");
        public static final ByteString<Name> group = makeGlobalName("group");
        public static final ByteString<Name> name = makeGlobalName("name");
        public static final ByteString<Name> priority = makeGlobalName("priority");
        public static final ByteString<Name> blockerLock = makeGlobalName("blockerLock");
        public static final ByteString<Name> constantPoolOop = makeGlobalName("constantPoolOop");
        public static final ByteString<Name> main = makeGlobalName("main");
        public static final ByteString<Name> checkAndLoadMain = makeGlobalName("checkAndLoadMain");
        public static final ByteString<Name> forName = makeGlobalName("forName");
        public static final ByteString<Name> run = makeGlobalName("run");
        public static final ByteString<Name> loadClass = makeGlobalName("loadClass");
        public static final ByteString<Name> addClass = makeGlobalName("addClass");
        public static final ByteString<Name> getMessage = makeGlobalName("getMessage");
        public static final ByteString<Name> getProperty = makeGlobalName("getProperty");
        public static final ByteString<Name> setProperty = makeGlobalName("setProperty");

        // Attribute names
        public static final ByteString<Name> Code = makeGlobalName("Code");
        public static final ByteString<Name> EnclosingMethod = makeGlobalName("EnclosingMethod");
        public static final ByteString<Name> Exceptions = makeGlobalName("Exceptions");
        public static final ByteString<Name> InnerClasses = makeGlobalName("InnerClasses");
        public static final ByteString<Name> RuntimeVisibleAnnotations = makeGlobalName("RuntimeVisibleAnnotations");
        public static final ByteString<Name> BootstrapMethods = makeGlobalName("BootstrapMethods");
        public static final ByteString<Name> exit = makeGlobalName("exit");
    }

    public static class Symbol extends ModifiedUTF8 {
    }

    public static class Descriptor extends ModifiedUTF8 {
    }

    public static class Type extends Descriptor {

        // Core types.
        public static final ByteString<Type> String = makeGlobalType(String.class);
        public static final ByteString<Type> String_array = makeGlobalType(String[].class);

        public static final ByteString<Type> Object = makeGlobalType(Object.class);
        public static final ByteString<Type> Object_array = makeGlobalType(Object[].class);

        public static final ByteString<Type> Class = makeGlobalType(Class.class);
        public static final ByteString<Type> Class_array = makeGlobalType(Class[].class);

        public static final ByteString<Type> Throwable = makeGlobalType(Throwable.class);
        public static final ByteString<Type> Exception = makeGlobalType(Exception.class);
        public static final ByteString<Type> System = makeGlobalType(System.class);
        public static final ByteString<Type> ClassLoader = makeGlobalType(java.lang.ClassLoader.class);

        // Primitive types. Use JavaKind.getType()?
        public static final ByteString<Type> _boolean = makeGlobalType(boolean.class);
        public static final ByteString<Type> _byte = makeGlobalType(byte.class);
        public static final ByteString<Type> _char = makeGlobalType(char.class);
        public static final ByteString<Type> _short = makeGlobalType(short.class);
        public static final ByteString<Type> _int = makeGlobalType(int.class);
        public static final ByteString<Type> _float = makeGlobalType(float.class);
        public static final ByteString<Type> _double = makeGlobalType(double.class);
        public static final ByteString<Type> _long = makeGlobalType(long.class);
        public static final ByteString<Type> _void = makeGlobalType(void.class);

        public static final ByteString<Type> _boolean_array = makeGlobalType(boolean[].class);
        public static final ByteString<Type> _byte_array = makeGlobalType(byte[].class);
        public static final ByteString<Type> _char_array = makeGlobalType(char[].class);
        public static final ByteString<Type> _short_array = makeGlobalType(short[].class);
        public static final ByteString<Type> _int_array = makeGlobalType(int[].class);
        public static final ByteString<Type> _float_array = makeGlobalType(float[].class);
        public static final ByteString<Type> _double_array = makeGlobalType(double[].class);
        public static final ByteString<Type> _long_array = makeGlobalType(long[].class);

        // Boxed types.
        public static final ByteString<Type> Boolean = makeGlobalType(Boolean.class);
        public static final ByteString<Type> Byte = makeGlobalType(Byte.class);
        public static final ByteString<Type> Character = makeGlobalType(Character.class);
        public static final ByteString<Type> Short = makeGlobalType(Short.class);
        public static final ByteString<Type> Integer = makeGlobalType(Integer.class);
        public static final ByteString<Type> Float = makeGlobalType(Float.class);
        public static final ByteString<Type> Double = makeGlobalType(Double.class);
        public static final ByteString<Type> Long = makeGlobalType(Long.class);
        public static final ByteString<Type> Void = makeGlobalType(Void.class);

        public static final ByteString<Type> Cloneable = makeGlobalType(Cloneable.class);

        public static final ByteString<Type> StackOverflowError = makeGlobalType(StackOverflowError.class);
        public static final ByteString<Type> OutOfMemoryError = makeGlobalType(OutOfMemoryError.class);

        public static final ByteString<Type> NullPointerException = makeGlobalType(NullPointerException.class);
        public static final ByteString<Type> ClassCastException = makeGlobalType(ClassCastException.class);
        public static final ByteString<Type> ArrayStoreException = makeGlobalType(ArrayStoreException.class);
        public static final ByteString<Type> ArithmeticException = makeGlobalType(ArithmeticException.class);
        public static final ByteString<Type> IllegalMonitorStateException = makeGlobalType(IllegalMonitorStateException.class);
        public static final ByteString<Type> IllegalArgumentException = makeGlobalType(IllegalArgumentException.class);
        public static final ByteString<Type> ClassNotFoundException = makeGlobalType(ClassNotFoundException.class);

        public static final ByteString<Type> Thread = makeGlobalType(Thread.class);
        public static final ByteString<Type> ThreadGroup = makeGlobalType(ThreadGroup.class);

        public static final ByteString<Type> Field = makeGlobalType(java.lang.reflect.Field.class);
        public static final ByteString<Type> Method = makeGlobalType(java.lang.reflect.Method.class);
        public static final ByteString<Type> Constructor = makeGlobalType(java.lang.reflect.Constructor.class);

        public static final ByteString<Type> Serializable = makeGlobalType(java.io.Serializable.class);
        public static final ByteString<Type> ByteBuffer = makeGlobalType(java.nio.ByteBuffer.class);
        public static final ByteString<Type> PrivilegedActionException = makeGlobalType(java.security.PrivilegedActionException.class);

        public static final ByteString<Type> sun_launcher_LauncherHelper = makeGlobalType(sun.launcher.LauncherHelper.class);

        // Finalizer is not public.
        public static final ByteString<Type> java_lang_ref_Finalizer = makeGlobalType("Ljava/lang/ref/Finalizer;");
        public static final ByteString<Type> StackTraceElement = makeGlobalType(StackTraceElement.class);
    }

    public static class Signature extends Descriptor {
        public static final ByteString<Signature> _void = makeGlobalSignature(Type._void);
        public static final ByteString<Signature> Class_String_boolean = makeGlobalSignature(Type.Class, Type.String, Type._boolean);
        public static final ByteString<Signature> _void_Class = makeGlobalSignature(Type._void, Type.Class);
        public static final ByteString<Signature> Object = makeGlobalSignature(Type.Object);
        public static final ByteString<Signature> Object_String_String = makeGlobalSignature(Type.Object, Type.String, Type.String);
        public static final ByteString<Signature> String_String = makeGlobalSignature(Type.String, Type.String);
        public static final ByteString<Signature> _void_String_array = makeGlobalSignature(Type._void, Type.String_array);
        public static final ByteString<Signature> Class_boolean_int_String = makeGlobalSignature(Type.Class, Type._boolean, Type._int, Type.String);
        public static final ByteString<Signature> String = makeGlobalSignature(Type.String);
        public static final ByteString<Signature> _void_Throwable = makeGlobalSignature(Type._void, Type.Throwable);
        public static final ByteString<Signature> _void_String = makeGlobalSignature(Type._void, Type.String);
        public static final ByteString<Signature> Class_String = makeGlobalSignature(Type.Class, Type.String);
        public static final ByteString<Signature> ByteBuffer_byte_array = makeGlobalSignature(Type.ByteBuffer, Type._byte_array);
        public static final ByteString<Signature> ClassLoader = makeGlobalSignature(Type.ClassLoader);
        public static final ByteString<Signature> _long_ClassLoader_String = makeGlobalSignature(Type._long, Type.ClassLoader, Type.String);
        public static final ByteString<Signature> _void_Exception = makeGlobalSignature(Type._void, Type.Exception);
        public static final ByteString<Signature> _void_String_String_String_int = makeGlobalSignature(Type._void, Type.String, Type.String, Type.String, Type._int);
        public static final ByteString<Signature> _int = makeGlobalSignature(Type._int);

        public static final ByteString<Signature> Boolean_boolean = makeGlobalSignature(Type.Boolean, Type._boolean);
        public static final ByteString<Signature> Byte_byte = makeGlobalSignature(Type.Byte, Type._byte);
        public static final ByteString<Signature> Character_char = makeGlobalSignature(Type.Character, Type._char);
        public static final ByteString<Signature> Short_short = makeGlobalSignature(Type.Short, Type._short);
        public static final ByteString<Signature> Float_float = makeGlobalSignature(Type.Float, Type._float);
        public static final ByteString<Signature> Integer_int = makeGlobalSignature(Type.Integer, Type._int);
        public static final ByteString<Signature> Double_double = makeGlobalSignature(Type.Double, Type._double);
        public static final ByteString<Signature> Long_long = makeGlobalSignature(Type.Long, Type._long);
        public static final ByteString<Signature> _void_int = makeGlobalSignature(Type._void, Type._int);
    }

    @Override
    public byte byteAt(int index) {
        return value[index];
    }

    @Override
    public ByteSequence subSequence(int start, int end) {
        throw EspressoError.unimplemented();
    }

    public ByteString(byte[] value) {
        this.value = value;
    }

    @Override
    public final int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            h = Arrays.hashCode(value);
            hash = h;
        }
        return h;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ByteString) {
            ByteString other = (ByteString) obj;
            return Arrays.equals(value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        try {
            return Utf8.toJavaString(value);
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public final int length() {
        return value.length;
    }

    public static <T> ByteString<T> singleASCII(char ch) {
        if (ch > 127) {
            throw new IllegalArgumentException("non-ASCII char");
        }
        return new ByteString<>(new byte[]{(byte) ch});
    }

    @Deprecated
    public static <T> ByteString<T> fromJavaString(String string) {
        return Utf8.fromJavaString(string);
    }
}
