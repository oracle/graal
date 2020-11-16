/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PRIVATE;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_STATIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SUPER;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_VARARGS;
import static com.oracle.truffle.api.impl.asm.Opcodes.ALOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.H_INVOKESTATIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.RETURN;
import static com.oracle.truffle.api.impl.asm.Type.BOOLEAN_TYPE;

import java.lang.annotation.Annotation;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.FieldVisitor;
import com.oracle.truffle.api.impl.asm.Handle;
import com.oracle.truffle.api.impl.asm.Label;
import com.oracle.truffle.api.impl.asm.Opcodes;
import com.oracle.truffle.api.impl.asm.Type;
import com.oracle.truffle.api.impl.asm.commons.InstructionAdapter;

/**
 * Generates bytecode for a Java adapter class. Used by the {@link HostAdapterFactory}.
 *
 * <p>
 * For every protected or public constructor in the extended class, the adapter class will have one
 * public constructor (visibility of protected constructors in the extended class is promoted to
 * public).
 * <ul>
 * <li>In every case, a constructor taking a trailing {@link Value} argument preceded by original
 * constructor arguments is always created on the adapter class. When such a constructor is invoked,
 * the passed {@link Value}'s member functions are used to implement and/or override methods on the
 * original class, dispatched by name. A single invokable member will act as the implementation for
 * all overloaded methods of the same name. When methods on an adapter instance are invoked, the
 * functions are invoked having the {@link Value} passed in the instance constructor as their
 * receiver. Subsequent changes to the members of that {@link Value} (reassignment or removal of its
 * functions) are reflected in the adapter instance; the method implementations are not bound to
 * functions at constructor invocation time.
 *
 * {@code java.lang.Object} methods {@code equals}, {@code hashCode}, and {@code toString} can also
 * be overridden. The only restriction is that since every JavaScript object already has a
 * {@code toString} function through the {@code Object.prototype}, the {@code toString} in the
 * adapter is only overridden if the passed object has a {@code toString} function as its own
 * property, and not inherited from a prototype. All other adapter methods can be implemented or
 * overridden through a prototype-inherited function of the object passed to the constructor,
 * too.</li>
 * <li>If the original types collectively have only one abstract method, or have several of them,
 * but all share the same name, the constructor(s) will check if the delegate {@link Value} is
 * executable, and if so, will use the passed function as the implementation for all abstract
 * methods. For consistency, any concrete methods sharing the single abstract method name will also
 * be overridden by the function.</li>
 * <li>If the adapter being generated can have class-level overrides, constructors taking the same
 * arguments as the superclass constructors are also created. These constructors simply delegate to
 * the superclass constructor. They are used to create instances of the adapter class with no
 * instance-level overrides.</li>
 * </ul>
 * </p>
 * <p>
 * For adapter methods that return values, all the conversions supported by {@link Value#as} will be
 * in effect to coerce the delegate functions' return value to the expected Java return type.
 * </p>
 * <p>
 * Since we are adding a trailing argument to the generated constructors in the adapter class, they
 * will never be declared as variable arity, even if the original constructor in the superclass was
 * declared as variable arity.
 * </p>
 * <p>
 * It is possible to create two different adapter classes: those that can have class-level
 * overrides, and those that can have instance-level overrides. When
 * {@link HostAdapterFactory#getAdapterClassFor} is invoked with non-null {@code classOverrides}
 * parameter, an adapter class is created that can have class-level overrides, and the passed
 * delegate object will be used as the implementations for its methods, just as in the above case of
 * the constructor taking a script object. Note that in the case of class-level overrides, a new
 * adapter class is created on every invocation, and the implementation object is bound to the
 * class, not to any instance. All created instances will share these functions. If it is required
 * to have both class-level overrides and instance-level overrides, the class-level override adapter
 * class should be subclassed with an instance-override adapter. Since adapters delegate to super
 * class when an overriding method handle is not specified, this will behave as expected. It is not
 * possible to have both class-level and instance-level overrides in the same class for security
 * reasons: adapter classes are defined with a protection domain of their creator code, and an
 * adapter class that has both class and instance level overrides would need to have two potentially
 * different protection domains: one for class-based behavior and one for instance-based behavior;
 * since Java classes can only belong to a single protection domain, this could not be implemented
 * securely.
 *
 */
final class HostAdapterBytecodeGenerator {
    // Initializer names
    private static final String INIT = "<init>";
    private static final String CLASS_INIT = "<clinit>";

    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final String OBJECT_TYPE_NAME = OBJECT_TYPE.getInternalName();
    private static final Type POLYGLOT_VALUE_TYPE = Type.getType(Value.class);
    private static final String POLYGLOT_VALUE_TYPE_DESCRIPTOR = POLYGLOT_VALUE_TYPE.getDescriptor();
    private static final String BOOLEAN_TYPE_DESCRIPTOR = BOOLEAN_TYPE.getDescriptor();

    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type CLASS_LOADER_TYPE = Type.getType(ClassLoader.class);
    /** @see HostAdapterServices#hasMethod(Value, String) */
    private static final String HAS_METHOD_NAME = "hasMethod";
    private static final String HAS_METHOD_DESCRIPTOR = Type.getMethodDescriptor(BOOLEAN_TYPE, POLYGLOT_VALUE_TYPE, STRING_TYPE);
    /** @see HostAdapterServices#hasOwnMethod(Value, String) */
    private static final String HAS_OWN_METHOD_NAME = "hasOwnMethod";
    private static final String HAS_OWN_METHOD_DESCRIPTOR = Type.getMethodDescriptor(BOOLEAN_TYPE, POLYGLOT_VALUE_TYPE, STRING_TYPE);
    /** @see HostAdapterServices#getClassOverrides(ClassLoader) */
    private static final String GET_CLASS_OVERRIDES_METHOD_NAME = "getClassOverrides";
    private static final String GET_CLASS_OVERRIDES_METHOD_DESCRIPTOR = Type.getMethodDescriptor(POLYGLOT_VALUE_TYPE, CLASS_LOADER_TYPE);

    private static final Type RUNTIME_EXCEPTION_TYPE = Type.getType(RuntimeException.class);
    private static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
    private static final Type UNSUPPORTED_OPERATION_TYPE = Type.getType(UnsupportedOperationException.class);
    /** @see HostAdapterServices#unsupported(String) */
    private static final String UNSUPPORTED_METHOD_NAME = "unsupported";
    private static final String UNSUPPORTED_METHOD_DESCRIPTOR = Type.getMethodDescriptor(UNSUPPORTED_OPERATION_TYPE, STRING_TYPE);
    /** @see HostAdapterServices#wrapThrowable(Throwable) */
    private static final String WRAP_THROWABLE_METHOD_NAME = "wrapThrowable";
    private static final String WRAP_THROWABLE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(RUNTIME_EXCEPTION_TYPE, THROWABLE_TYPE);

    private static final String SERVICES_CLASS_TYPE_NAME = HostAdapterClassLoader.SERVICE_CLASS_NAME.replace('.', '/');
    private static final String RUNTIME_EXCEPTION_TYPE_NAME = RUNTIME_EXCEPTION_TYPE.getInternalName();
    private static final String ERROR_TYPE_NAME = Type.getInternalName(Error.class);
    private static final String THROWABLE_TYPE_NAME = THROWABLE_TYPE.getInternalName();

    private static final String CLASS_TYPE_NAME = Type.getInternalName(Class.class);
    private static final String GET_CLASS_LOADER_NAME = "getClassLoader";
    private static final String GET_CLASS_LOADER_DESCRIPTOR = Type.getMethodDescriptor(CLASS_LOADER_TYPE);

    // ASM handle to the bootstrap method
    private static final Handle BOOTSTRAP_HANDLE = new Handle(H_INVOKESTATIC, SERVICES_CLASS_TYPE_NAME, "bootstrap",
                    MethodType.methodType(CallSite.class, Lookup.class, String.class, MethodType.class, int.class).toMethodDescriptorString(), false);

    // Keep in sync with constants in HostAdapterServices
    static final int BOOTSTRAP_VALUE_INVOKE_MEMBER = 1 << 0;
    static final int BOOTSTRAP_VALUE_EXECUTE = 1 << 1;
    static final int BOOTSTRAP_VARARGS = 1 << 2;

    /*
     * Package used when the adapter can't be defined in the adaptee's package (either because it's
     * sealed, or because it's a java.* package.
     */
    private static final String ADAPTER_PACKAGE_PREFIX = "com/oracle/truffle/polyglot/hostadapters/";
    /*
     * Class name suffix used to append to the adaptee class name, when it can be defined in the
     * adaptee's package.
     */
    private static final String ADAPTER_CLASS_NAME_SUFFIX = "$$Adapter";
    private static final int MAX_GENERATED_TYPE_NAME_LENGTH = 255;

    private static final String DELEGATE_FIELD_NAME = "delegate";
    private static final String IS_FUNCTION_FIELD_NAME = "isFunction";
    private static final String PUBLIC_DELEGATE_FIELD_NAME = HostInteropReflect.ADAPTER_DELEGATE_MEMBER;

    // Method name prefix for invoking super-methods
    static final String SUPER_PREFIX = "super$";

    // This is the superclass for our generated adapter.
    private final Class<?> superClass;
    // Interfaces implemented by our generated adapter.
    private final List<Class<?>> interfaces;
    /*
     * Class loader used as the parent for the class loader we'll create to load the generated
     * class. It will be a class loader that has the visibility of all original types (class to
     * extend and interfaces to implement) and of the language classes.
     */
    private final ClassLoader commonLoader;
    private final HostClassCache hostClassCache;
    // Is this a generator for the version of the class that can have overrides on the class level?
    private final boolean classOverride;
    // Binary name of the superClass
    private final String superClassName;
    // Binary name of the generated class.
    private final String generatedClassName;
    private final Set<String> abstractMethodNames = new HashSet<>();
    private final String samName;
    private final Set<MethodInfo> finalMethods = new HashSet<>();
    private final Set<MethodInfo> methodInfos = new HashSet<>();
    private final boolean autoConvertibleFromFunction;
    private final boolean hasSuperMethods;
    private final boolean hasPublicDelegateField;

    private final ClassWriter cw;

    /**
     * Creates a generator for the bytecode for the adapter for the specified superclass and
     * interfaces.
     *
     * @param superClass the superclass the adapter will extend.
     * @param interfaces the interfaces the adapter will implement.
     * @param commonLoader the class loader that can see all of superClass, interfaces, and
     *            {@link HostAdapterServices}.
     * @param hostClassCache {@link HostClassCache}
     * @param classOverride true to generate the bytecode for the adapter that has both class-level
     *            and instance-level overrides, false to generate the bytecode for the adapter that
     *            only has instance-level overrides.
     *
     *            throws AdaptationException if the adapter can not be generated for some reason.
     */
    HostAdapterBytecodeGenerator(final Class<?> superClass, final List<Class<?>> interfaces, final ClassLoader commonLoader, HostClassCache hostClassCache, final boolean classOverride) {
        assert superClass != null && !superClass.isInterface();
        assert interfaces != null;

        this.superClass = superClass;
        this.interfaces = interfaces;
        this.commonLoader = commonLoader;
        this.hostClassCache = hostClassCache;
        this.classOverride = classOverride;

        this.superClassName = Type.getInternalName(superClass);
        this.generatedClassName = getGeneratedClassName(superClass, interfaces);

        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                /*
                 * We need to override ClassWriter.getCommonSuperClass to use this factory's
                 * commonLoader as a class loader to find the common superclass of two types when
                 * needed.
                 */
                return HostAdapterBytecodeGenerator.this.getCommonSuperClass(type1, type2);
            }
        };

        cw.visit(Opcodes.V1_8, ACC_PUBLIC | ACC_SUPER, generatedClassName, null, superClassName, getInternalTypeNames(interfaces));

        generatePrivateField(DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);

        this.hasPublicDelegateField = !classOverride;
        if (hasPublicDelegateField) {
            generatePublicDelegateField();
        }

        gatherMethods(superClass);
        gatherMethods(interfaces);
        if (abstractMethodNames.size() == 1) {
            this.samName = abstractMethodNames.iterator().next();
            generatePrivateField(IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);
        } else {
            this.samName = null;
        }

        generateClassInit();
        this.autoConvertibleFromFunction = generateConstructors();
        generateMethods();
        this.hasSuperMethods = generateSuperMethods();

        cw.visitEnd();
    }

    private void generatePrivateField(final String name, final String fieldDesc) {
        cw.visitField(ACC_PRIVATE | ACC_FINAL | (classOverride ? ACC_STATIC : 0), name, fieldDesc, null, null).visitEnd();
    }

    private void generatePublicDelegateField() {
        FieldVisitor fw = cw.visitField(ACC_PUBLIC | ACC_FINAL, PUBLIC_DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR, null, null);
        fw.visitEnd();
    }

    private void loadField(final InstructionAdapter mv, final String name, final String desc) {
        if (classOverride) {
            mv.getstatic(generatedClassName, name, desc);
        } else {
            mv.visitVarInsn(ALOAD, 0);
            mv.getfield(generatedClassName, name, desc);
        }
    }

    HostAdapterClassLoader createAdapterClassLoader() {
        return new HostAdapterClassLoader(generatedClassName, cw.toByteArray());
    }

    boolean isAutoConvertibleFromFunction() {
        return autoConvertibleFromFunction;
    }

    boolean hasSuperMethods() {
        return hasSuperMethods;
    }

    private static String getGeneratedClassName(final Class<?> superType, final List<Class<?>> interfaces) {
        /*
         * The class we use to primarily name our adapter is either the superclass, or if it is
         * Object (meaning we're just implementing interfaces or extending Object), then the first
         * implemented interface or Object.
         */
        final Class<?> namingType = superType == Object.class ? (interfaces.isEmpty() ? Object.class : interfaces.get(0)) : superType;
        String namingTypeName = namingType.getSimpleName();
        if (namingTypeName.isEmpty()) {
            namingTypeName = "Adapter";
        }
        final StringBuilder buf = new StringBuilder();
        buf.append(ADAPTER_PACKAGE_PREFIX).append(namingTypeName);
        final Iterator<Class<?>> it = interfaces.iterator();
        if (superType == Object.class && it.hasNext()) {
            it.next(); // Skip first interface, it was used to primarily name the adapter
        }
        // Append interface names to the adapter name
        while (it.hasNext()) {
            buf.append("$$").append(it.next().getSimpleName());
        }
        buf.append(ADAPTER_CLASS_NAME_SUFFIX);
        return buf.toString().substring(0, Math.min(MAX_GENERATED_TYPE_NAME_LENGTH, buf.length()));
    }

    /**
     * Given a list of class objects, return an array with their binary names. Used to generate the
     * array of interface names to implement.
     *
     * @param classes the classes
     * @return an array of names
     */
    private static String[] getInternalTypeNames(final List<Class<?>> classes) {
        final int interfaceCount = classes.size();
        final String[] interfaceNames = new String[interfaceCount];
        for (int i = 0; i < interfaceCount; ++i) {
            interfaceNames[i] = Type.getInternalName(classes.get(i));
        }
        return interfaceNames;
    }

    private void generateClassInit() {
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_STATIC, CLASS_INIT, Type.getMethodDescriptor(Type.VOID_TYPE), null, null));

        if (classOverride) {
            mv.visitLdcInsn(getGeneratedClassAsType());
            mv.invokevirtual(CLASS_TYPE_NAME, GET_CLASS_LOADER_NAME, GET_CLASS_LOADER_DESCRIPTOR, false);
            mv.invokestatic(SERVICES_CLASS_TYPE_NAME, GET_CLASS_OVERRIDES_METHOD_NAME, GET_CLASS_OVERRIDES_METHOD_DESCRIPTOR, false);
            // stack: [delegate]

            if (samName != null) {
                // If the class is a SAM, allow having a ScriptFunction passed as class overrides
                mv.dup();
                emitIsFunction(mv);
                mv.putstatic(generatedClassName, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);
            }

            mv.putstatic(generatedClassName, DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);
        }

        endInitMethod(mv);
    }

    private Type getGeneratedClassAsType() {
        return Type.getType('L' + generatedClassName + ';');
    }

    private static void emitIsFunction(final InstructionAdapter mv) {
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "isFunction", Type.getMethodDescriptor(Type.getType(boolean.class), OBJECT_TYPE), false);
    }

    private boolean generateConstructors() {
        boolean gotCtor = false;
        boolean canBeAutoConverted = false;
        for (final Constructor<?> ctor : superClass.getDeclaredConstructors()) {
            final int modifier = ctor.getModifiers();
            if ((modifier & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0 && !isCallerSensitive(ctor)) {
                canBeAutoConverted |= generateConstructors(ctor);
                gotCtor = true;
            }
        }
        if (!gotCtor) {
            throw new IllegalArgumentException("No accessible constructor: " + superClass.getCanonicalName());
        }
        return canBeAutoConverted;
    }

    private boolean generateConstructors(final Constructor<?> ctor) {
        if (classOverride) {
            /*
             * Generate a constructor that just delegates to ctor. This is used with class-level
             * overrides, when we want to create instances without further per-instance overrides.
             */
            generateDelegatingConstructor(ctor);
            return false;
        }

        /*
         * Generate a constructor that delegates to ctor, but takes an additional ScriptObject
         * parameter at the beginning of its parameter list.
         */
        // generateOverridingConstructor(ctor, false);
        boolean fromFunction = samName != null;
        generateOverridingConstructor(ctor, fromFunction);

        if (!fromFunction) {
            return false;
        }

        /*
         * If all our abstract methods have a single name, generate an additional constructor, one
         * that takes a ScriptFunction as its first parameter and assigns it as the implementation
         * for all abstract methods.
         */
        // generateOverridingConstructor(ctor, true);
        /*
         * If the original type only has a single abstract method name, as well as a default ctor,
         * then it can be automatically converted from JS function.
         */
        return ctor.getParameterCount() == 0;
    }

    private void generateDelegatingConstructor(final Constructor<?> ctor) {
        final Type originalCtorType = Type.getType(ctor);
        final Type[] argTypes = originalCtorType.getArgumentTypes();

        // All constructors must be public, even if in the superclass they were protected.
        final String methodDescriptor = Type.getMethodDescriptor(originalCtorType.getReturnType(), argTypes);
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC | (ctor.isVarArgs() ? ACC_VARARGS : 0), INIT, methodDescriptor, null, null));

        mv.visitCode();
        emitSuperConstructorCall(mv, originalCtorType.getDescriptor());

        endInitMethod(mv);
    }

    /**
     * Generates a constructor for the instance adapter class. This constructor will take the same
     * arguments as the supertype constructor passed as the argument here, and delegate to it.
     * However, it will take an additional argument, either an object or a function (based on the
     * value of the "fromFunction" parameter), and initialize all the method handle fields of the
     * adapter instance with functions from the script object (or the script function itself, if
     * that's what's passed).
     *
     * The generated constructor will be public, regardless of whether the supertype constructor was
     * public or protected. The generated constructor will not be variable arity, even if the
     * supertype constructor was.
     *
     * @param ctor the supertype constructor that is serving as the base for the generated
     *            constructor.
     * @param fromFunction true if we're generating a constructor that initializes SAM types from a
     *            single ScriptFunction passed to it, false if we're generating a constructor that
     *            initializes an arbitrary type from a ScriptObject passed to it.
     */
    private void generateOverridingConstructor(final Constructor<?> ctor, final boolean fromFunction) {
        assert !classOverride;
        final Type originalCtorType = Type.getType(ctor);
        final Type[] originalArgTypes = originalCtorType.getArgumentTypes();
        final int argLen = originalArgTypes.length;
        final Type[] newArgTypes = new Type[argLen + 1];

        // Insert ScriptFunction|Object as the last argument to the constructor
        final Type extraArgumentType = POLYGLOT_VALUE_TYPE;
        newArgTypes[argLen] = extraArgumentType;
        System.arraycopy(originalArgTypes, 0, newArgTypes, 0, argLen);

        // All constructors must be public, even if in the superclass they were protected.
        // Existing super constructor <init>(this, args...) triggers generating <init>(this,
        // scriptObj, args...).
        String signature = Type.getMethodDescriptor(originalCtorType.getReturnType(), newArgTypes);
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, INIT, signature, null, null));

        mv.visitCode();
        // First, invoke super constructor with original arguments.
        int offset = emitSuperConstructorCall(mv, originalCtorType.getDescriptor());

        if (fromFunction) {
            final Label notAFunction = new Label();
            final Label end = new Label();
            mv.visitVarInsn(ALOAD, offset);
            emitIsFunction(mv);
            mv.ifeq(notAFunction);

            // Function branch
            // isFunction = true
            mv.visitVarInsn(ALOAD, 0);
            mv.iconst(1);
            mv.putfield(generatedClassName, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);

            mv.goTo(end);
            mv.visitLabel(notAFunction);

            // Object branch
            // isFunction = false
            mv.visitVarInsn(ALOAD, 0);
            mv.iconst(0);
            mv.putfield(generatedClassName, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);

            mv.visitLabel(end);
        }

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, offset); // delegate object
        mv.putfield(generatedClassName, DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);

        if (hasPublicDelegateField) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, offset); // delegate object
            mv.putfield(generatedClassName, PUBLIC_DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);
        }

        endInitMethod(mv);
    }

    private static void endInitMethod(final InstructionAdapter mv) {
        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private static void endMethod(final InstructionAdapter mv) {
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Encapsulation of the information used to generate methods in the adapter classes. Basically,
     * a wrapper around the reflective Method object, a cached MethodType, and the name of the field
     * in the adapter class that will hold the method handle serving as the implementation of this
     * method in adapter instances.
     *
     */
    private static final class MethodInfo {
        private final Method method;
        private final MethodType type;

        private MethodInfo(final Method method) {
            this.method = method;
            this.type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof MethodInfo && equals((MethodInfo) obj);
        }

        private boolean equals(final MethodInfo other) {
            // Only method name and type are used for comparison; method handle field name is not.
            return getName().equals(other.getName()) && type.equals(other.type);
        }

        String getName() {
            return method.getName();
        }

        @Override
        public int hashCode() {
            return getName().hashCode() ^ type.hashCode();
        }

        @Override
        public String toString() {
            return method.toString();
        }
    }

    private void generateMethods() {
        for (final MethodInfo mi : methodInfos) {
            generateMethod(mi);
        }
    }

    /**
     * Generates a method in the adapter class that adapts a method from the original class.
     *
     * If the super class has a single abstract method, the generated methods will inspect
     * the @{code isFunction} field, i.e., if a function object was passed to the constructor; if it
     * is true, then methods with the same name will execute the provided function object and other
     * methods will fall back to the super method. Else, it will invoke a method of the passed
     * "overrides" object if it has an invokable member with the method's name, otherwise it will
     * fall back to the super method. If the super method it is abstract, it will throw an
     * {@link UnsupportedOperationException}.
     *
     * If the invoked member or function results in a Throwable that is not one of the method's
     * declared exceptions, and is not an unchecked throwable, then it is wrapped into a
     * {@link RuntimeException} and the runtime exception is thrown.
     *
     * @param mi the method info describing the method to be generated.
     */
    private void generateMethod(final MethodInfo mi) {
        final Method method = mi.method;
        final Class<?>[] declaredExceptions = method.getExceptionTypes();
        final String[] exceptionNames = getExceptionNames(declaredExceptions);
        final MethodType type = mi.type;
        final String methodDesc = type.toMethodDescriptorString();
        final String name = mi.getName();

        final Type asmType = Type.getMethodType(methodDesc);
        final Type[] asmArgTypes = asmType.getArgumentTypes();

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(getAccessModifiers(method), name, methodDesc, null, exceptionNames));

        mv.visitCode();

        final Type asmReturnType = Type.getType(type.returnType());
        final int bootstrapFlags = method.isVarArgs() ? BOOTSTRAP_VARARGS : 0;

        final Label defaultBehavior = new Label();
        final Label hasMethod = new Label();

        final List<TryBlock> tryBlocks = new ArrayList<>();

        loadField(mv, DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);
        // For the cases like scripted overridden methods invoked from super constructors get
        // adapter global/delegate fields as null, since we
        // cannot set these fields before invoking super constructor better solution is opt out of
        // scripted overridden method if global/delegate fields
        // are null and invoke super method instead
        mv.ifnull(defaultBehavior);
        // stack: []

        // If this is a SAM type...
        if (samName != null) {
            // ...every method will be checking whether we're initialized with a function.
            loadField(mv, IS_FUNCTION_FIELD_NAME, BOOLEAN_TYPE_DESCRIPTOR);
            // stack: [isFunction]
            if (name.equals(samName)) {
                final Label notFunction = new Label();
                mv.ifeq(notFunction);
                // stack: []
                // If it's a SAM method, it'll load delegate as the "callee".
                loadField(mv, DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);
                // stack: [delegate]

                // Load all parameters on the stack for dynamic invocation.
                loadParams(mv, asmArgTypes, 1);

                final Label tryBlockStart = new Label();
                mv.visitLabel(tryBlockStart);

                // Invoke the target method handle
                mv.visitInvokeDynamicInsn(name, type.insertParameterTypes(0, Value.class).toMethodDescriptorString(), BOOTSTRAP_HANDLE, BOOTSTRAP_VALUE_EXECUTE | bootstrapFlags);

                final Label tryBlockEnd = new Label();
                mv.visitLabel(tryBlockEnd);
                tryBlocks.add(new TryBlock(tryBlockStart, tryBlockEnd));

                mv.areturn(asmReturnType);

                mv.visitLabel(notFunction);
                // stack: []
            } else {
                // If it's not a SAM method, and the delegate is a function,
                // it'll fall back to default behavior
                mv.ifne(defaultBehavior);
                // stack: []
            }
        }

        loadField(mv, DELEGATE_FIELD_NAME, POLYGLOT_VALUE_TYPE_DESCRIPTOR);
        // stack: [delegate]

        if (name.equals("toString")) {
            Label hasNoToString = new Label();
            // Since every JS Object has a toString, we only override
            // "String toString()" it if it's explicitly specified on the object.
            mv.dup();
            mv.visitLdcInsn(name);
            // stack: ["toString", delegate, delegate]
            mv.invokestatic(SERVICES_CLASS_TYPE_NAME, HAS_OWN_METHOD_NAME, HAS_OWN_METHOD_DESCRIPTOR, false);
            // stack: [hasOwnToString, delegate]
            mv.ifne(hasNoToString);
            mv.pop();
            // stack: []
            mv.goTo(defaultBehavior);
            mv.visitLabel(hasNoToString);
        }

        mv.dup();
        mv.visitLdcInsn(name);
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, HAS_METHOD_NAME, HAS_METHOD_DESCRIPTOR, false);
        mv.ifne(hasMethod);
        // stack: [delegate]

        // else: no override available, clear the stack
        mv.pop();

        // No override available, fall back to default behavior
        mv.visitLabel(defaultBehavior);
        if (Modifier.isAbstract(method.getModifiers())) {
            // If the super method is abstract, throw an exception
            mv.visitLdcInsn(name);
            mv.invokestatic(SERVICES_CLASS_TYPE_NAME, UNSUPPORTED_METHOD_NAME, UNSUPPORTED_METHOD_DESCRIPTOR, false);
            mv.athrow();
        } else {
            // If the super method is not abstract, delegate to it.
            emitSuperCall(mv, method.getDeclaringClass(), name, methodDesc);
            mv.areturn(Type.getMethodType(methodDesc).getReturnType());
        }

        mv.visitLabel(hasMethod);
        // stack: [delegate]

        // Load all parameters on the stack for dynamic invocation.
        loadParams(mv, asmArgTypes, 1);

        final Label tryBlockStart = new Label();
        mv.visitLabel(tryBlockStart);

        // Invoke the target method handle
        mv.visitInvokeDynamicInsn(name, type.insertParameterTypes(0, Value.class).toMethodDescriptorString(), BOOTSTRAP_HANDLE, BOOTSTRAP_VALUE_INVOKE_MEMBER | bootstrapFlags);

        final Label tryBlockEnd = new Label();
        mv.visitLabel(tryBlockEnd);
        tryBlocks.add(new TryBlock(tryBlockStart, tryBlockEnd));

        mv.areturn(asmReturnType);

        emitTryCatchBlocks(mv, declaredExceptions, tryBlocks);

        endMethod(mv);
    }

    private static final class TryBlock {
        final Label start;
        final Label end;

        TryBlock(Label start, Label end) {
            this.start = start;
            this.end = end;
        }
    }

    private static void emitTryCatchBlocks(final InstructionAdapter mv, final Class<?>[] declaredExceptions, final List<TryBlock> tryBlocks) {
        if (isThrowableDeclared(declaredExceptions)) {
            // Method declares Throwable, no need for a try-catch
            return;
        }

        // If Throwable is not declared, we need an adapter from Throwable to RuntimeException
        final Label rethrowHandler = new Label();
        mv.visitLabel(rethrowHandler);
        // Rethrow handler for RuntimeException, Error, and all declared exception types
        mv.athrow();

        // Add "throw new RuntimeException(Throwable)" handler for Throwable
        final Label throwableHandler = new Label();
        mv.visitLabel(throwableHandler);
        wrapThrowable(mv);
        mv.athrow();

        for (TryBlock tryBlock : tryBlocks) {
            mv.visitTryCatchBlock(tryBlock.start, tryBlock.end, rethrowHandler, RUNTIME_EXCEPTION_TYPE_NAME);
            mv.visitTryCatchBlock(tryBlock.start, tryBlock.end, rethrowHandler, ERROR_TYPE_NAME);
            for (Class<?> exception : declaredExceptions) {
                mv.visitTryCatchBlock(tryBlock.start, tryBlock.end, rethrowHandler, Type.getInternalName(exception));
            }
            mv.visitTryCatchBlock(tryBlock.start, tryBlock.end, throwableHandler, THROWABLE_TYPE_NAME);
        }
    }

    private static void wrapThrowable(InstructionAdapter mv) {
        // original Throwable on the top of the stack
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, WRAP_THROWABLE_METHOD_NAME, WRAP_THROWABLE_METHOD_DESCRIPTOR, false);
    }

    private static boolean isThrowableDeclared(final Class<?>[] exceptions) {
        for (final Class<?> exception : exceptions) {
            if (exception == Throwable.class) {
                return true;
            }
        }
        return false;
    }

    private boolean generateSuperMethods() {
        boolean hasAccessibleNonAbstractSuperMethods = false;
        for (final MethodInfo mi : methodInfos) {
            if (!Modifier.isAbstract(mi.method.getModifiers()) && hostClassCache.allowsAccess(mi.method)) {
                generateSuperMethod(mi);
                hasAccessibleNonAbstractSuperMethods = true;
            }
        }
        return hasAccessibleNonAbstractSuperMethods;
    }

    private void generateSuperMethod(MethodInfo mi) {
        final Method method = mi.method;

        final String methodDesc = mi.type.toMethodDescriptorString();
        final String name = mi.getName();

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(getAccessModifiers(method), SUPER_PREFIX + name, methodDesc, null, getExceptionNames(method.getExceptionTypes())));

        mv.visitCode();

        emitSuperCall(mv, method.getDeclaringClass(), name, methodDesc);
        mv.areturn(Type.getMethodType(methodDesc).getReturnType());

        endMethod(mv);
    }

    // find the appropriate super type to use for invokespecial on the given interface
    private Class<?> findInvokespecialOwnerFor(final Class<?> owner) {
        assert Modifier.isInterface(owner.getModifiers()) : owner + " is not an interface";

        if (owner.isAssignableFrom(superClass)) {
            return superClass;
        }

        for (final Class<?> iface : interfaces) {
            if (owner.isAssignableFrom(iface)) {
                return iface;
            }
        }

        throw new AssertionError("Cannot find the class/interface that extends " + owner);
    }

    private int emitSuperConstructorCall(final InstructionAdapter mv, final String methodDesc) {
        return emitSuperCall(mv, null, INIT, methodDesc, true);
    }

    private int emitSuperCall(final InstructionAdapter mv, final Class<?> owner, final String name, final String methodDesc) {
        return emitSuperCall(mv, owner, name, methodDesc, false);
    }

    private int emitSuperCall(final InstructionAdapter mv, final Class<?> owner, final String name, final String methodDesc, boolean constructor) {
        mv.visitVarInsn(ALOAD, 0);
        int nextParam = loadParams(mv, Type.getMethodType(methodDesc).getArgumentTypes(), 1);

        // default method - non-abstract interface method
        if (!constructor && Modifier.isInterface(owner.getModifiers())) {
            // we should call default method on the immediate "super" type - not on (possibly)
            // the indirectly inherited interface class!
            final Class<?> superType = findInvokespecialOwnerFor(owner);
            mv.invokespecial(Type.getInternalName(superType), name, methodDesc, Modifier.isInterface(superType.getModifiers()));
        } else {
            mv.invokespecial(superClassName, name, methodDesc, false);
        }
        return nextParam;
    }

    private static int loadParams(final InstructionAdapter mv, final Type[] paramTypes, final int paramOffset) {
        int varOffset = paramOffset;
        for (final Type t : paramTypes) {
            mv.load(varOffset, t);
            varOffset += t.getSize();
        }
        return varOffset;
    }

    private static String[] getExceptionNames(final Class<?>[] exceptions) {
        final String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; ++i) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }
        return exceptionNames;
    }

    private static int getAccessModifiers(final Method method) {
        return ACC_PUBLIC | (method.isVarArgs() ? ACC_VARARGS : 0);
    }

    /**
     * Gathers methods that can be implemented or overridden from the specified type into this
     * factory's {@link #methodInfos} set. It will add all non-final, non-static methods that are
     * either public or protected from the type if the type itself is public. If the type is a
     * class, the method will recursively invoke itself for its superclass and the interfaces it
     * implements, and add further methods that were not directly declared on the class.
     *
     * @param type the type defining the methods.
     */
    private void gatherMethods(final Class<?> type) {
        if (Modifier.isPublic(type.getModifiers())) {
            final Method[] typeMethods = type.isInterface() ? type.getMethods() : type.getDeclaredMethods();

            for (final Method typeMethod : typeMethods) {
                final String name = typeMethod.getName();
                if (name.startsWith(SUPER_PREFIX)) {
                    continue;
                }
                final int mod = typeMethod.getModifiers();
                if (Modifier.isStatic(mod)) {
                    continue;
                }
                if (Modifier.isPublic(mod) || Modifier.isProtected(mod)) {
                    final MethodInfo mi = new MethodInfo(typeMethod);
                    if (Modifier.isFinal(mod) || isExcluded(typeMethod) || isCallerSensitive(typeMethod)) {
                        finalMethods.add(mi);
                    } else if (!finalMethods.contains(mi) && methodInfos.add(mi)) {
                        if (Modifier.isAbstract(mod)) {
                            abstractMethodNames.add(mi.getName());
                        }
                    }
                }
            }
        }
        /*
         * If the type is a class, visit its superclasses and declared interfaces. If it's an
         * interface, we're done. Needing to invoke the method recursively for a non-interface Class
         * object is the consequence of needing to see all declared protected methods, and
         * Class.getDeclaredMethods() doesn't provide those declared in a superclass. For
         * interfaces, we used Class.getMethods(), as we're only interested in public ones there,
         * and getMethods() does provide those declared in a superinterface.
         */
        if (!type.isInterface()) {
            final Class<?> superType = type.getSuperclass();
            if (superType != null) {
                gatherMethods(superType);
            }
            for (final Class<?> itf : type.getInterfaces()) {
                gatherMethods(itf);
            }
        }
    }

    private void gatherMethods(final List<Class<?>> classes) {
        for (final Class<?> c : classes) {
            gatherMethods(c);
        }
    }

    /**
     * Returns true for methods that are not final, but we still never allow them to be overridden
     * in adapters, as explicitly declaring them automatically is a bad idea. Currently, this means
     * {@code Object.finalize()} and {@code Object.clone()}.
     *
     * @return true if the method is one of those methods that we never override in adapter classes.
     */
    private static boolean isExcluded(Method method) {
        if (method.getParameterCount() == 0) {
            switch (method.getName()) {
                case "finalize":
                    return true;
                case "clone":
                    return true;
            }
        }
        return false;
    }

    private String getCommonSuperClass(final String type1, final String type2) {
        try {
            final Class<?> c1 = Class.forName(type1.replace('/', '.'), false, commonLoader);
            final Class<?> c2 = Class.forName(type2.replace('/', '.'), false, commonLoader);
            if (c1.isAssignableFrom(c2)) {
                return type1;
            }
            if (c2.isAssignableFrom(c1)) {
                return type2;
            }
            if (c1.isInterface() || c2.isInterface()) {
                return OBJECT_TYPE_NAME;
            }
            return assignableSuperClass(c1, c2).getName().replace('.', '/');
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> assignableSuperClass(final Class<?> c1, final Class<?> c2) {
        final Class<?> superClass = c1.getSuperclass();
        return superClass.isAssignableFrom(c2) ? superClass : assignableSuperClass(superClass, c2);
    }

    private static boolean isCallerSensitive(final Executable e) {
        return CALLER_SENSITIVE_ANNOTATION_CLASS != null && e.isAnnotationPresent(CALLER_SENSITIVE_ANNOTATION_CLASS);
    }

    private static final Class<? extends Annotation> CALLER_SENSITIVE_ANNOTATION_CLASS = findCallerSensitiveAnnotationClass();

    private static Class<? extends Annotation> findCallerSensitiveAnnotationClass() {
        try {
            // JDK 8
            return Class.forName("sun.reflect.CallerSensitive").asSubclass(Annotation.class);
        } catch (ClassNotFoundException e1) {
        }
        try {
            // JDK 9
            return Class.forName("jdk.internal.reflect.CallerSensitive").asSubclass(Annotation.class);
        } catch (ClassNotFoundException e2) {
        }
        return null; // not found
    }
}
