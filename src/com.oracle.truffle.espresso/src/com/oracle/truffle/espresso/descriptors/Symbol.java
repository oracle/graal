/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.descriptors;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.ProtectionDomain;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;
import com.oracle.truffle.espresso.meta.EspressoError;

import sun.misc.VM;

/**
 * An immutable byte string (modified-UTF8) for internal use in Espresso. <br>
 * Symbols are unique during it's lifetime and can be reference-compared for equality. Internal
 * meta-data in Espresso is stored using symbols: names, signatures, type descriptors and constant
 * pool (modified-)UTF8 entries.
 * <p>
 * Symbols provides several advantages over {@link String} for storing meta-data:
 * <ul>
 * <li>Clear separation between guest and host
 * <li>Compact representation
 * <li>Globally unique + cheap equality comparison
 * <li>Seamless de-duplication
 * <li>0-cost tagging for improved type-safety
 * <li>Copy-less symbolification, symbols are only copied once, when they are being
 * created/persisted
 * <li>Contents are effectively {@link CompilationFinal partial-evaluation constant}
 * </ul>
 *
 * Symbols can be tagged, with no runtime cost, for additional type safety:
 * <ul>
 * <li>Symbol&lt;{@link Name}&gt; identifiers, field/class/method names.
 * <li>Symbol&lt;{@link ModifiedUTF8}&gt; strings from the constant pool.
 * <li>Symbol&lt;? extends {@link Descriptor}&gt; valid types or signatures
 * <li>Symbol&lt;{@link Signature}&gt; valid signature descriptor in internal form
 * <li>Symbol&lt;{@link Type}&gt; valid type descriptor in internal form
 * </ul>
 *
 * <b> Note: Do not synchronize on symbols; class loading relies on exclusive ownership of the
 * symbol's monitors, it may cause unexpected dead-locks. </b>
 *
 * @param <T> generic tag for improved type-safety
 */
public final class Symbol<T> extends ByteSequence {

    @SuppressWarnings("rawtypes") public static final Symbol[] EMPTY_ARRAY = new Symbol[0];

    Symbol(byte[] bytes, int hashCode) {
        super(bytes, hashCode);
    }

    Symbol(byte[] value) {
        // hashCode must match ByteSequence.hashCodeOfRange.
        this(value, Arrays.hashCode(value));
    }

    @SuppressWarnings("unchecked")
    public static <S> Symbol<S>[] emptyArray() {
        return EMPTY_ARRAY;
    }

    ByteSequence substring(int from) {
        return substring(from, length());
    }

    ByteSequence substring(int from, int to) {
        assert 0 <= from && from <= to && to <= length();
        if (from == 0 && to == length()) {
            return this;
        }
        return subSequence(from, to - from);
    }

    static void copyBytes(Symbol<?> src, int srcPos, byte[] dest, int destPos, int length) {
        System.arraycopy(src.value, srcPos, dest, destPos, length);
    }

    @Override
    public byte byteAt(int index) {
        return value[index];
    }

    @Override
    public String toString() {
        try {
            return ModifiedUtf8.toJavaString(value);
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public int length() {
        return value.length;
    }

    @Override
    public int offset() {
        return 0;
    }

    public static class ModifiedUTF8 {

        @SuppressWarnings("unchecked")
        public static Symbol<ModifiedUTF8> fromSymbol(Symbol<? extends ModifiedUTF8> symbol) {
            return (Symbol<ModifiedUTF8>) symbol;
        }
    }

    public static class Descriptor extends ModifiedUTF8 {
    }

    public static final class Name extends ModifiedUTF8 {

        public static void init() {
            /* nop */
        }

        public static final Symbol<Name> INIT = StaticSymbols.putName("<init>");
        public static final Symbol<Name> CLINIT = StaticSymbols.putName("<clinit>");

        public static final Symbol<Name> backtrace = StaticSymbols.putName("backtrace");
        public static final Symbol<Name> cause = StaticSymbols.putName("cause");
        public static final Symbol<Name> fillInStackTrace = StaticSymbols.putName("fillInStackTrace");
        public static final Symbol<Name> fillInStackTrace0 = StaticSymbols.putName("fillInStackTrace0");
        public static final Symbol<Name> clazz = StaticSymbols.putName("clazz");
        public static final Symbol<Name> type = StaticSymbols.putName("type");
        public static final Symbol<Name> slot = StaticSymbols.putName("slot");
        public static final Symbol<Name> flags = StaticSymbols.putName("flags");
        public static final Symbol<Name> root = StaticSymbols.putName("root");
        public static final Symbol<Name> value = StaticSymbols.putName("value");
        public static final Symbol<Name> hash = StaticSymbols.putName("hash");
        public static final Symbol<Name> hashCode = StaticSymbols.putName("hashCode");
        public static final Symbol<Name> length = StaticSymbols.putName("length");
        public static final Symbol<Name> findNative = StaticSymbols.putName("findNative");
        public static final Symbol<Name> getSystemClassLoader = StaticSymbols.putName("getSystemClassLoader");
        public static final Symbol<Name> valueOf = StaticSymbols.putName("valueOf");
        public static final Symbol<Name> wrap = StaticSymbols.putName("wrap");
        public static final Symbol<Name> initializeSystemClass = StaticSymbols.putName("initializeSystemClass");
        public static final Symbol<Name> security = StaticSymbols.putName("security");
        public static final Symbol<Name> impliesCreateAccessControlContext = StaticSymbols.putName("impliesCreateAccessControlContext");
        public static final Symbol<Name> isAuthorized = StaticSymbols.putName("isAuthorized");
        public static final Symbol<Name> isPrivileged = StaticSymbols.putName("isPrivileged");
        public static final Symbol<Name> context = StaticSymbols.putName("context");
        public static final Symbol<Name> privilegedContext = StaticSymbols.putName("privilegedContext");
        public static final Symbol<Name> group = StaticSymbols.putName("group");
        public static final Symbol<Name> name = StaticSymbols.putName("name");
        public static final Symbol<Name> priority = StaticSymbols.putName("priority");
        public static final Symbol<Name> blockerLock = StaticSymbols.putName("blockerLock");
        public static final Symbol<Name> parkBlocker = StaticSymbols.putName("parkBlocker");
        public static final Symbol<Name> constantPoolOop = StaticSymbols.putName("constantPoolOop");
        public static final Symbol<Name> main = StaticSymbols.putName("main");
        public static final Symbol<Name> checkAndLoadMain = StaticSymbols.putName("checkAndLoadMain");
        public static final Symbol<Name> forName = StaticSymbols.putName("forName");
        public static final Symbol<Name> run = StaticSymbols.putName("run");
        public static final Symbol<Name> parent = StaticSymbols.putName("parent");
        public static final Symbol<Name> loadClass = StaticSymbols.putName("loadClass");
        public static final Symbol<Name> addClass = StaticSymbols.putName("addClass");
        public static final Symbol<Name> getMessage = StaticSymbols.putName("getMessage");
        public static final Symbol<Name> getProperty = StaticSymbols.putName("getProperty");
        public static final Symbol<Name> setProperty = StaticSymbols.putName("setProperty");
        public static final Symbol<Name> exit = StaticSymbols.putName("exit");
        public static final Symbol<Name> stop = StaticSymbols.putName("stop");
        public static final Symbol<Name> override = StaticSymbols.putName("override");
        public static final Symbol<Name> parameterTypes = StaticSymbols.putName("parameterTypes");
        public static final Symbol<Name> shutdown = StaticSymbols.putName("shutdown");
        public static final Symbol<Name> clone = StaticSymbols.putName("clone");
        public static final Symbol<Name> printStackTrace = StaticSymbols.putName("printStackTrace");
        public static final Symbol<Name> maxPriority = StaticSymbols.putName("maxPriority");
        public static final Symbol<Name> interrupt = StaticSymbols.putName("interrupt");
        public static final Symbol<Name> daemon = StaticSymbols.putName("daemon");
        public static final Symbol<Name> inheritedAccessControlContext = StaticSymbols.putName("inheritedAccessControlContext");
        public static final Symbol<Name> threadStatus = StaticSymbols.putName("threadStatus");
        public static final Symbol<Name> checkAccess = StaticSymbols.putName("checkAccess");
        public static final Symbol<Name> remove = StaticSymbols.putName("remove");
        public static final Symbol<Name> dispatchUncaughtException = StaticSymbols.putName("dispatchUncaughtException");
        public static final Symbol<Name> toThreadState = StaticSymbols.putName("toThreadState");
        public static final Symbol<Name> form = StaticSymbols.putName("form");
        public static final Symbol<Name> vmentry = StaticSymbols.putName("vmentry");
        public static final Symbol<Name> target = StaticSymbols.putName("target");
        public static final Symbol<Name> isCompiled = StaticSymbols.putName("isCompiled");
        public static final Symbol<Name> compileToBytecode = StaticSymbols.putName("compileToBytecode");
        public static final Symbol<Name> address = StaticSymbols.putName("address");
        public static final Symbol<Name> capacity = StaticSymbols.putName("capacity");
        public static final Symbol<Name> finalize = StaticSymbols.putName("finalize");
        public static final Symbol<Name> register = StaticSymbols.putName("register");
        public static final Symbol<Name> add = StaticSymbols.putName("add");

        public static final Symbol<Name> lookup = StaticSymbols.putName("lookup");
        public static final Symbol<Name> findMethodHandleType = StaticSymbols.putName("findMethodHandleType");
        public static final Symbol<Name> linkMethod = StaticSymbols.putName("linkMethod");
        public static final Symbol<Name> linkCallSite = StaticSymbols.putName("linkCallSite");
        public static final Symbol<Name> linkMethodHandleConstant = StaticSymbols.putName("linkMethodHandleConstant");
        public static final Symbol<Name> toMethodDescriptorString = StaticSymbols.putName("toMethodDescriptorString");
        public static final Symbol<Name> fromMethodDescriptorString = StaticSymbols.putName("fromMethodDescriptorString");
        public static final Symbol<Name> getSignature = StaticSymbols.putName("getSignature");
        public static final Symbol<Name> signature = StaticSymbols.putName("signature");
        public static final Symbol<Name> getParameterTypes = StaticSymbols.putName("getParameterTypes");

        public static final Symbol<Name> classes = StaticSymbols.putName("classes");
        public static final Symbol<Name> classEnabled = StaticSymbols.putName("classEnabled");
        public static final Symbol<Name> packages = StaticSymbols.putName("packages");
        public static final Symbol<Name> packageEnabled = StaticSymbols.putName("packageEnabled");
        public static final Symbol<Name> deflt = StaticSymbols.putName("deflt");
        public static final Symbol<Name> Null = StaticSymbols.putName("null");

        // Polymorphic signature method names
        public static final Symbol<Name> invoke = StaticSymbols.putName("invoke");
        public static final Symbol<Name> invokeExact = StaticSymbols.putName("invokeExact");
        public static final Symbol<Name> invokeBasic = StaticSymbols.putName("invokeBasic");
        public static final Symbol<Name> invokeWithArguments = StaticSymbols.putName("invokeWithArguments");
        public static final Symbol<Name> linkToVirtual = StaticSymbols.putName("linkToVirtual");
        public static final Symbol<Name> linkToStatic = StaticSymbols.putName("linkToStatic");
        public static final Symbol<Name> linkToInterface = StaticSymbols.putName("linkToInterface");
        public static final Symbol<Name> linkToSpecial = StaticSymbols.putName("linkToSpecial");
        public static final Symbol<Name> member = StaticSymbols.putName("member");

        public static final Symbol<Name> referent = StaticSymbols.putName("referent");
        public static final Symbol<Name> discovered = StaticSymbols.putName("discovered");
        public static final Symbol<Name> next = StaticSymbols.putName("next");
        public static final Symbol<Name> pending = StaticSymbols.putName("pending");
        public static final Symbol<Name> queue = StaticSymbols.putName("queue");
        public static final Symbol<Name> lock = StaticSymbols.putName("lock");
        public static final Symbol<Name> NULL = StaticSymbols.putName("NULL");

        // Attribute names
        public static final Symbol<Name> Code = StaticSymbols.putName("Code");
        public static final Symbol<Name> EnclosingMethod = StaticSymbols.putName("EnclosingMethod");
        public static final Symbol<Name> Exceptions = StaticSymbols.putName("Exceptions");
        public static final Symbol<Name> InnerClasses = StaticSymbols.putName("InnerClasses");
        public static final Symbol<Name> LineNumberTable = StaticSymbols.putName("LineNumberTable");
        public static final Symbol<Name> LocalVariableTable = StaticSymbols.putName("LocalVariableTable");

        public static final Symbol<Name> BootstrapMethods = StaticSymbols.putName("BootstrapMethods");
        public static final Symbol<Name> StackMapTable = StaticSymbols.putName("StackMapTable");
        public static final Symbol<Name> ConstantValue = StaticSymbols.putName("ConstantValue");
        public static final Symbol<Name> RuntimeVisibleAnnotations = StaticSymbols.putName("RuntimeVisibleAnnotations");
        public static final Symbol<Name> RuntimeVisibleTypeAnnotations = StaticSymbols.putName("RuntimeVisibleTypeAnnotations");
        public static final Symbol<Name> RuntimeInvisibleTypeAnnotations = StaticSymbols.putName("RuntimeInvisibleTypeAnnotations");
        public static final Symbol<Name> RuntimeVisibleParameterAnnotations = StaticSymbols.putName("RuntimeVisibleParameterAnnotations");
        public static final Symbol<Name> AnnotationDefault = StaticSymbols.putName("AnnotationDefault");
        public static final Symbol<Name> MethodParameters = StaticSymbols.putName("MethodParameters");
        public static final Symbol<Name> Signature = StaticSymbols.putName("Signature");
        public static final Symbol<Name> SourceFile = StaticSymbols.putName("SourceFile");
        public static final Symbol<Name> Synthetic = StaticSymbols.putName("Synthetic");
        public static final Symbol<Name> Deprecated = StaticSymbols.putName("Deprecated");
        public static final Symbol<Name> LocalVariableTypeTable = StaticSymbols.putName("LocalVariableTypeTable");

        // Hidden field names. Starts with a 0 in order for the names to be illegal identifiers.
        public static final Symbol<Name> HIDDEN_VMTARGET = StaticSymbols.putName("0HIDDEN_VMTARGET");
        public static final Symbol<Name> HIDDEN_VMINDEX = StaticSymbols.putName("0HIDDEN_VMINDEX");
        public static final Symbol<Name> HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = StaticSymbols.putName("0HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS");
        public static final Symbol<Name> HIDDEN_METHOD_KEY = StaticSymbols.putName("0HIDDEN_METHOD_KEY");
        public static final Symbol<Name> HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = StaticSymbols.putName("0HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS");
        public static final Symbol<Name> HIDDEN_CONSTRUCTOR_KEY = StaticSymbols.putName("0HIDDEN_CONSTRUCTOR_KEY");
        public static final Symbol<Name> HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = StaticSymbols.putName("0HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS");
        public static final Symbol<Name> HIDDEN_FIELD_KEY = StaticSymbols.putName("0HIDDEN_FIELD_KEY");
        public static final Symbol<Name> HIDDEN_FRAMES = StaticSymbols.putName("0HIDDEN_FRAMES");
        public static final Symbol<Name> HIDDEN_HOST_THREAD = StaticSymbols.putName("0HIDDEN_HOST_THREAD");
        public static final Symbol<Name> HIDDEN_MIRROR_KLASS = StaticSymbols.putName("0HIDDEN_MIRROR_KLASS");
        public static final Symbol<Name> HIDDEN_SIGNERS = StaticSymbols.putName("0HIDDEN_SIGNERS");
        public static final Symbol<Name> HIDDEN_IS_ALIVE = StaticSymbols.putName("0HIDDEN_IS_ALIVE");
        public static final Symbol<Name> HIDDEN_INTERRUPTED = StaticSymbols.putName("0HIDDEN_INTERRUPTED");
        public static final Symbol<Name> HIDDEN_DEATH = StaticSymbols.putName("0HIDDEN_DEATH");
        public static final Symbol<Name> HIDDEN_SUSPEND_LOCK = StaticSymbols.putName("0HIDDEN_SUSPEND_LOCK");
        public static final Symbol<Name> HIDDEN_PROTECTION_DOMAIN = StaticSymbols.putName("0HIDDEN_PROTECTION_DOMAIN");
        public static final Symbol<Name> HIDDEN_HOST_REFERENCE = StaticSymbols.putName("0HIDDEN_HOST_REFERENCE");
    }

    public static final class Type extends Descriptor {

        public static void init() {
            /* nop */
        }

        // Core types.
        public static final Symbol<Type> String = StaticSymbols.putType(String.class);
        public static final Symbol<Type> String_array = StaticSymbols.putType(String[].class);

        public static final Symbol<Type> Object = StaticSymbols.putType(Object.class);
        public static final Symbol<Type> Object_array = StaticSymbols.putType(Object[].class);

        public static final Symbol<Type> Class = StaticSymbols.putType(Class.class);
        public static final Symbol<Type> Class_array = StaticSymbols.putType(Class[].class);

        public static final Symbol<Type> Throwable = StaticSymbols.putType(Throwable.class);
        public static final Symbol<Type> Exception = StaticSymbols.putType(Exception.class);
        public static final Symbol<Type> System = StaticSymbols.putType(System.class);
        public static final Symbol<Type> ProtectionDomain = StaticSymbols.putType(ProtectionDomain.class);
        public static final Symbol<Type> ProtectionDomain_array = StaticSymbols.putType(ProtectionDomain[].class);
        public static final Symbol<Type> AccessControlContext = StaticSymbols.putType(AccessControlContext.class);
        public static final Symbol<Type> SecurityManager = StaticSymbols.putType(SecurityManager.class);
        public static final Symbol<Type> CodeSource = StaticSymbols.putType(CodeSource.class);
        public static final Symbol<Type> PermissionCollection = StaticSymbols.putType(PermissionCollection.class);

        public static final Symbol<Type> ClassLoader = StaticSymbols.putType(java.lang.ClassLoader.class);
        public static final Symbol<Type> sun_misc_Launcher_ExtClassLoader = StaticSymbols.putType("Lsun/misc/Launcher$ExtClassLoader;");
        public static final Symbol<Type> Null = StaticSymbols.putType("LNull;");

        // Primitive types. Use JavaKind.getType()?
        public static final Symbol<Type> _boolean = StaticSymbols.putType(boolean.class);
        public static final Symbol<Type> _byte = StaticSymbols.putType(byte.class);
        public static final Symbol<Type> _char = StaticSymbols.putType(char.class);
        public static final Symbol<Type> _short = StaticSymbols.putType(short.class);
        public static final Symbol<Type> _int = StaticSymbols.putType(int.class);
        public static final Symbol<Type> _float = StaticSymbols.putType(float.class);
        public static final Symbol<Type> _double = StaticSymbols.putType(double.class);
        public static final Symbol<Type> _long = StaticSymbols.putType(long.class);
        public static final Symbol<Type> _void = StaticSymbols.putType(void.class);

        public static final Symbol<Type> _boolean_array = StaticSymbols.putType(boolean[].class);
        public static final Symbol<Type> _byte_array = StaticSymbols.putType(byte[].class);
        public static final Symbol<Type> _char_array = StaticSymbols.putType(char[].class);
        public static final Symbol<Type> _short_array = StaticSymbols.putType(short[].class);
        public static final Symbol<Type> _int_array = StaticSymbols.putType(int[].class);
        public static final Symbol<Type> _float_array = StaticSymbols.putType(float[].class);
        public static final Symbol<Type> _double_array = StaticSymbols.putType(double[].class);
        public static final Symbol<Type> _long_array = StaticSymbols.putType(long[].class);

        // Boxed types.
        public static final Symbol<Type> Boolean = StaticSymbols.putType(Boolean.class);
        public static final Symbol<Type> Byte = StaticSymbols.putType(Byte.class);
        public static final Symbol<Type> Character = StaticSymbols.putType(Character.class);
        public static final Symbol<Type> Short = StaticSymbols.putType(Short.class);
        public static final Symbol<Type> Integer = StaticSymbols.putType(Integer.class);
        public static final Symbol<Type> Float = StaticSymbols.putType(Float.class);
        public static final Symbol<Type> Double = StaticSymbols.putType(Double.class);
        public static final Symbol<Type> Long = StaticSymbols.putType(Long.class);
        public static final Symbol<Type> Void = StaticSymbols.putType(Void.class);

        public static final Symbol<Type> Cloneable = StaticSymbols.putType(Cloneable.class);

        public static final Symbol<Type> StackOverflowError = StaticSymbols.putType(StackOverflowError.class);
        public static final Symbol<Type> OutOfMemoryError = StaticSymbols.putType(OutOfMemoryError.class);
        public static final Symbol<Type> AssertionError = StaticSymbols.putType(AssertionError.class);

        public static final Symbol<Type> NullPointerException = StaticSymbols.putType(NullPointerException.class);
        public static final Symbol<Type> ClassCastException = StaticSymbols.putType(ClassCastException.class);
        public static final Symbol<Type> ArrayStoreException = StaticSymbols.putType(ArrayStoreException.class);
        public static final Symbol<Type> ArithmeticException = StaticSymbols.putType(ArithmeticException.class);
        public static final Symbol<Type> IllegalMonitorStateException = StaticSymbols.putType(IllegalMonitorStateException.class);
        public static final Symbol<Type> IllegalArgumentException = StaticSymbols.putType(IllegalArgumentException.class);
        public static final Symbol<Type> ClassNotFoundException = StaticSymbols.putType(ClassNotFoundException.class);
        public static final Symbol<Type> NoClassDefFoundError = StaticSymbols.putType(NoClassDefFoundError.class);
        public static final Symbol<Type> InterruptedException = StaticSymbols.putType(InterruptedException.class);
        public static final Symbol<Type> NegativeArraySizeException = StaticSymbols.putType(NegativeArraySizeException.class);
        public static final Symbol<Type> RuntimeException = StaticSymbols.putType(RuntimeException.class);
        public static final Symbol<Type> InvocationTargetException = StaticSymbols.putType(java.lang.reflect.InvocationTargetException.class);

        public static final Symbol<Type> Thread = StaticSymbols.putType(Thread.class);
        public static final Symbol<Type> ThreadGroup = StaticSymbols.putType(ThreadGroup.class);
        public static final Symbol<Type> Runnable = StaticSymbols.putType(Runnable.class);

        public static final Symbol<Type> sun_misc_VM = StaticSymbols.putType(VM.class);
        public static final Symbol<Type> ThreadStateEnum = StaticSymbols.putType(Thread.State.class);

        public static final Symbol<Type> sun_nio_ch_DirectBuffer = StaticSymbols.putType(sun.nio.ch.DirectBuffer.class);
        public static final Symbol<Type> Buffer = StaticSymbols.putType(java.nio.Buffer.class);

        // Guest reflection.
        public static final Symbol<Type> Field = StaticSymbols.putType(java.lang.reflect.Field.class);
        public static final Symbol<Type> Method = StaticSymbols.putType(java.lang.reflect.Method.class);
        public static final Symbol<Type> Constructor = StaticSymbols.putType(java.lang.reflect.Constructor.class);
        public static final Symbol<Type> Parameter = StaticSymbols.putType(java.lang.reflect.Parameter.class);
        public static final Symbol<Type> Executable = StaticSymbols.putType(java.lang.reflect.Executable.class);

        // MagicAccessorImpl is not public.
        public static final Symbol<Type> MagicAccessorImpl = StaticSymbols.putType("Lsun/reflect/MagicAccessorImpl;");
        // DelegatingClassLoader is not public.
        public static final Symbol<Type> sun_reflect_DelegatingClassLoader = StaticSymbols.putType("Lsun/reflect/DelegatingClassLoader;");

        // MethodAccessorImpl is not public.
        public static final Symbol<Type> MethodAccessorImpl = StaticSymbols.putType("Lsun/reflect/MethodAccessorImpl;");
        public static final Symbol<Type> ConstructorAccessorImpl = StaticSymbols.putType("Lsun/reflect/ConstructorAccessorImpl;");

        public static final Symbol<Type> sun_reflect_ConstantPool = StaticSymbols.putType(sun.reflect.ConstantPool.class);

        public static final Symbol<Type> Serializable = StaticSymbols.putType(java.io.Serializable.class);
        public static final Symbol<Type> ByteBuffer = StaticSymbols.putType(java.nio.ByteBuffer.class);
        public static final Symbol<Type> PrivilegedActionException = StaticSymbols.putType(java.security.PrivilegedActionException.class);

        // Shutdown is not public.
        public static final Symbol<Type> Shutdown = StaticSymbols.putType("Ljava/lang/Shutdown;");

        public static final Symbol<Type> sun_launcher_LauncherHelper = StaticSymbols.putType(sun.launcher.LauncherHelper.class);

        // Finalizer is not public.
        public static final Symbol<Type> java_lang_ref_Finalizer = StaticSymbols.putType("Ljava/lang/ref/Finalizer;");
        public static final Symbol<Type> java_lang_ref_Reference = StaticSymbols.putType(java.lang.ref.Reference.class);
        public static final Symbol<Type> java_lang_ref_FinalReference = StaticSymbols.putType("Ljava/lang/ref/FinalReference;");
        public static final Symbol<Type> java_lang_ref_WeakReference = StaticSymbols.putType(java.lang.ref.WeakReference.class);
        public static final Symbol<Type> java_lang_ref_ReferenceQueue = StaticSymbols.putType(java.lang.ref.ReferenceQueue.class);
        public static final Symbol<Type> java_lang_ref_Reference_Lock = StaticSymbols.putType("Ljava/lang/ref/Reference$Lock;");

        public static final Symbol<Type> StackTraceElement = StaticSymbols.putType(StackTraceElement.class);

        public static final Symbol<Type> Error = StaticSymbols.putType(Error.class);
        public static final Symbol<Type> NoSuchFieldError = StaticSymbols.putType(NoSuchFieldError.class);
        public static final Symbol<Type> NoSuchMethodError = StaticSymbols.putType(NoSuchMethodError.class);
        public static final Symbol<Type> IllegalAccessError = StaticSymbols.putType(IllegalAccessError.class);
        public static final Symbol<Type> IncompatibleClassChangeError = StaticSymbols.putType(IncompatibleClassChangeError.class);
        public static final Symbol<Type> AbstractMethodError = StaticSymbols.putType(AbstractMethodError.class);
        public static final Symbol<Type> InternalError = StaticSymbols.putType(InternalError.class);
        public static final Symbol<Type> VerifyError = StaticSymbols.putType(VerifyError.class);

        public static final Symbol<Type> MethodType = StaticSymbols.putType(java.lang.invoke.MethodType.class);

        public static final Symbol<Type> AssertionStatusDirectives = StaticSymbols.putType("Ljava/lang/AssertionStatusDirectives;");

        public static final Symbol<Type> MethodHandles = StaticSymbols.putType(java.lang.invoke.MethodHandles.class);
        public static final Symbol<Type> Lookup = StaticSymbols.putType(java.lang.invoke.MethodHandles.Lookup.class);
        public static final Symbol<Type> CallSite = StaticSymbols.putType(java.lang.invoke.CallSite.class);
        public static final Symbol<Type> DirectMethodHandle = StaticSymbols.putType("Ljava/lang/invoke/DirectMethodHandle;");

        // MethodHandleNatives is not public.
        public static final Symbol<Type> MethodHandleNatives = StaticSymbols.putType("Ljava/lang/invoke/MethodHandleNatives;");
        public static final Symbol<Type> MemberName = StaticSymbols.putType("Ljava/lang/invoke/MemberName;");
        public static final Symbol<Type> MethodHandle = StaticSymbols.putType(java.lang.invoke.MethodHandle.class);
        public static final Symbol<Type> LambdaForm = StaticSymbols.putType("Ljava/lang/invoke/LambdaForm;");
        public static final Symbol<Type> LambdaForm$Compiled = StaticSymbols.putType("Ljava/lang/invoke/LambdaForm$Compiled;");
        public static final Symbol<Type> sun_reflect_CallerSensitive = StaticSymbols.putType(sun.reflect.CallerSensitive.class);

        // Special threads
        public static final Symbol<Type> FinalizerThread = StaticSymbols.putType("Ljava/lang/ref/Finalizer$FinalizerThread;");
        public static final Symbol<Type> ReferenceHandler = StaticSymbols.putType("Ljava/lang/ref/Reference$ReferenceHandler;");
    }

    public static final class Signature extends Descriptor {

        public static void init() {
            /* nop */
        }

        public static final Symbol<Signature> _int = StaticSymbols.putSignature(Type._int);
        public static final Symbol<Signature> _void = StaticSymbols.putSignature(Type._void);
        public static final Symbol<Signature> _boolean = StaticSymbols.putSignature(Type._boolean);

        public static final Symbol<Signature> _void_Object = StaticSymbols.putSignature(Type._void, Type.Object);

        public static final Symbol<Signature> Object = StaticSymbols.putSignature(Type.Object);
        public static final Symbol<Signature> String = StaticSymbols.putSignature(Type.String);
        public static final Symbol<Signature> ClassLoader = StaticSymbols.putSignature(Type.ClassLoader);

        public static final Symbol<Signature> Class_String_boolean = StaticSymbols.putSignature(Type.Class, Type.String, Type._boolean);
        public static final Symbol<Signature> _void_Class = StaticSymbols.putSignature(Type._void, Type.Class);
        public static final Symbol<Signature> Class_array = StaticSymbols.putSignature(Type.Class_array);

        public static final Symbol<Signature> Object_String_String = StaticSymbols.putSignature(Type.Object, Type.String, Type.String);
        public static final Symbol<Signature> String_String = StaticSymbols.putSignature(Type.String, Type.String);
        public static final Symbol<Signature> _void_String_array = StaticSymbols.putSignature(Type._void, Type.String_array);
        public static final Symbol<Signature> Class_boolean_int_String = StaticSymbols.putSignature(Type.Class, Type._boolean, Type._int, Type.String);
        public static final Symbol<Signature> Class_String_boolean_ClassLoader = StaticSymbols.putSignature(Type.Class, Type.String, Type._boolean, Type.ClassLoader);

        public static final Symbol<Signature> _void_Throwable = StaticSymbols.putSignature(Type._void, Type.Throwable);
        public static final Symbol<Signature> _void_String_Throwable = StaticSymbols.putSignature(Type._void, Type.String, Type.Throwable);
        public static final Symbol<Signature> _void_String = StaticSymbols.putSignature(Type._void, Type.String);
        public static final Symbol<Signature> Class_String = StaticSymbols.putSignature(Type.Class, Type.String);
        public static final Symbol<Signature> ByteBuffer_byte_array = StaticSymbols.putSignature(Type.ByteBuffer, Type._byte_array);
        public static final Symbol<Signature> _long_ClassLoader_String = StaticSymbols.putSignature(Type._long, Type.ClassLoader, Type.String);
        public static final Symbol<Signature> _void_Exception = StaticSymbols.putSignature(Type._void, Type.Exception);
        public static final Symbol<Signature> _void_String_String_String_int = StaticSymbols.putSignature(Type._void, Type.String, Type.String, Type.String, Type._int);
        public static final Symbol<Signature> _void_int = StaticSymbols.putSignature(Type._void, Type._int);

        public static final Symbol<Signature> Boolean_boolean = StaticSymbols.putSignature(Type.Boolean, Type._boolean);
        public static final Symbol<Signature> Byte_byte = StaticSymbols.putSignature(Type.Byte, Type._byte);
        public static final Symbol<Signature> Character_char = StaticSymbols.putSignature(Type.Character, Type._char);
        public static final Symbol<Signature> Short_short = StaticSymbols.putSignature(Type.Short, Type._short);
        public static final Symbol<Signature> Float_float = StaticSymbols.putSignature(Type.Float, Type._float);
        public static final Symbol<Signature> Integer_int = StaticSymbols.putSignature(Type.Integer, Type._int);
        public static final Symbol<Signature> Double_double = StaticSymbols.putSignature(Type.Double, Type._double);
        public static final Symbol<Signature> Long_long = StaticSymbols.putSignature(Type.Long, Type._long);

        public static final Symbol<Signature> Object_ObjectArray = StaticSymbols.putSignature(Type.Object, Type.Object_array);

        public static final Symbol<Signature> MethodType_cons = StaticSymbols.putSignature(Type.MethodType, Type.Class, Type.Class_array);
        public static final Symbol<Signature> fromMethodDescriptorString_signature = StaticSymbols.putSignature(Type.MethodType, Type.String, Type.ClassLoader);

        public static final Symbol<Signature> compileToBytecode = StaticSymbols.putSignature(Type.MemberName);

        public static final Symbol<Signature> linkMethod_signature = StaticSymbols.putSignature(Type.MemberName, Type.Class, Type._int, Type.Class, Type.String, Type.Object, Type.Object_array);
        public static final Symbol<Signature> linkMethodHandleConstant_signature = StaticSymbols.putSignature(Type.MethodHandle, Type.Class, Type._int, Type.Class, Type.String, Type.Object);
        public static final Symbol<Signature> linkCallSite_signature = StaticSymbols.putSignature(Type.MemberName, Type.Object, Type.Object, Type.Object, Type.Object, Type.Object, Type.Object_array);
        public static final Symbol<Signature> lookup_signature = StaticSymbols.putSignature(Type.Lookup);

        public static final Symbol<Signature> toThreadState = StaticSymbols.putSignature(Type.ThreadStateEnum, Type._int);
        public static final Symbol<Signature> ThreadGroup_remove = StaticSymbols.putSignature(Type._void, Type.Thread);
        public static final Symbol<Signature> _void_ThreadGroup_String = StaticSymbols.putSignature(Type._void, Type.ThreadGroup, Type.String);
        public static final Symbol<Signature> _void_ThreadGroup_Runnable = StaticSymbols.putSignature(Type._void, Type.ThreadGroup, Type.Runnable);
        public static final Symbol<Signature> _void_Thread = StaticSymbols.putSignature(Type._void, Type.Thread);

        public static final Symbol<Signature> CodeSource_PermissionCollection = StaticSymbols.putSignature(Type._void, Type.CodeSource, Type.PermissionCollection);

    }
}
