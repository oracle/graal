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
package com.oracle.truffle.espresso.vmaccess;

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalConstantReflectionProvider.safeGetClass;

import java.util.Objects;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.oracle.graal.vmaccess.InvocationException;
import com.oracle.graal.vmaccess.VMAccess;
import com.oracle.truffle.espresso.graal.DummyForeignCallsProvider;
import com.oracle.truffle.espresso.graal.DummyLoweringProvider;
import com.oracle.truffle.espresso.graal.DummyPlatformConfigurationProvider;
import com.oracle.truffle.espresso.graal.DummyReplacements;
import com.oracle.truffle.espresso.graal.DummyStampProvider;
import com.oracle.truffle.espresso.graal.EspressoConstantFieldProvider;
import com.oracle.truffle.espresso.graal.EspressoMetaAccessExtensionProvider;
import com.oracle.truffle.espresso.jvmci.DummyCodeCacheProvider;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * A {@link VMAccess} implementation that reflects on the state of an espresso VM in a Polyglot
 * {@link Context}.
 */
final class EspressoExternalVMAccess implements VMAccess {
    private final Context context;
    private final EspressoExternalResolvedPrimitiveType[] primitives;
    private final EspressoExternalMetaAccessProvider metaAccess;
    private final EspressoExternalConstantReflectionProvider constantReflection;
    private final MetaAccessExtensionProvider metaAccessExtensionProvider;
    private final Value jvmciHelper;
    private final EspressoExternalResolvedInstanceType javaLangObject;
    private final EspressoExternalResolvedInstanceType[] arrayInterfaces;
    private final Providers providers;
    private final JavaConstant platformClassLoader;
    private final ResolvedJavaMethod forName;
    private final ResolvedJavaMethod unsafeAllocateInstance;
    private final JavaConstant unsafe;
    private final ResolvedJavaType classNotFoundExceptionType;
    private JavaConstant systemClassLoader;

    @SuppressWarnings("this-escape")
    EspressoExternalVMAccess(Context context) {
        this.context = context;
        Value bindings = context.getBindings("java");
        jvmciHelper = bindings.getMember("<JVMCI_HELPER>");
        bindings.removeMember("<JVMCI_HELPER>");
        metaAccess = new EspressoExternalMetaAccessProvider(this);
        constantReflection = new EspressoExternalConstantReflectionProvider(this);
        metaAccessExtensionProvider = new EspressoMetaAccessExtensionProvider(constantReflection);
        primitives = createPrimitiveTypes();
        javaLangObject = new EspressoExternalResolvedInstanceType(this, lookupMetaObject(context, "java.lang.Object"));
        arrayInterfaces = new EspressoExternalResolvedInstanceType[]{
                        new EspressoExternalResolvedInstanceType(this, lookupMetaObject(context, "java.io.Serializable")),
                        new EspressoExternalResolvedInstanceType(this, lookupMetaObject(context, "java.lang.Cloneable")),
        };
        providers = createProviders();

        ResolvedJavaType classLoaderType = providers.getMetaAccess().lookupJavaType(ClassLoader.class);
        Signature classLoaderGetterSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/ClassLoader;");
        ResolvedJavaMethod getPlatformClassLoader = classLoaderType.findMethod("getPlatformClassLoader", classLoaderGetterSignature);
        platformClassLoader = invoke(getPlatformClassLoader, null);

        ResolvedJavaType classType = providers.getMetaAccess().lookupJavaType(Class.class);
        Signature forNameSignature = providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        forName = classType.findMethod("forName", forNameSignature);
        classNotFoundExceptionType = providers.getMetaAccess().lookupJavaType(ClassNotFoundException.class);

        ResolvedJavaType unsafeType = lookupBootClassLoaderType("jdk.internal.misc.Unsafe");
        unsafeAllocateInstance = unsafeType.findMethod("allocateInstance", providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/Class;)Ljava/lang/Object;"));
        assert unsafeAllocateInstance != null;
        ResolvedJavaMethod unsafeGetter = unsafeType.findMethod("getUnsafe", providers.getMetaAccess().parseMethodDescriptor("()Ljdk/internal/misc/Unsafe;"));
        unsafe = invoke(unsafeGetter, null);
    }

    private EspressoExternalResolvedPrimitiveType[] createPrimitiveTypes() {
        EspressoExternalResolvedPrimitiveType[] prims = new EspressoExternalResolvedPrimitiveType[JavaKind.Void.getBasicType() + 1];
        prims[JavaKind.Boolean.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Boolean);
        prims[JavaKind.Byte.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Byte);
        prims[JavaKind.Short.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Short);
        prims[JavaKind.Char.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Char);
        prims[JavaKind.Int.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Int);
        prims[JavaKind.Float.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Float);
        prims[JavaKind.Long.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Long);
        prims[JavaKind.Double.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Double);
        prims[JavaKind.Void.getBasicType()] = new EspressoExternalResolvedPrimitiveType(this, JavaKind.Void);
        return prims;
    }

    private Providers createProviders() {
        TargetDescription target = DummyCodeCacheProvider.getHostTarget();
        CodeCacheProvider codeCache = new DummyCodeCacheProvider(target);
        ConstantFieldProvider constantFieldProvider = new EspressoConstantFieldProvider(metaAccess);
        ForeignCallsProvider foreignCalls = new DummyForeignCallsProvider();
        LoweringProvider lowerer = new DummyLoweringProvider(target);
        StampProvider stampProvider = new DummyStampProvider();
        PlatformConfigurationProvider platformConfigurationProvider = new DummyPlatformConfigurationProvider();
        SnippetReflectionProvider snippetReflection = new EspressoExternalSnippetReflectionProvider();
        WordTypes wordTypes = new WordTypes(metaAccess, target.wordJavaKind);
        LoopsDataProvider loopsDataProvider = new LoopsDataProviderImpl();
        Providers newProviders = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls,
                        lowerer, null, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider, snippetReflection,
                        wordTypes, loopsDataProvider);
        Replacements replacements = new DummyReplacements(newProviders);
        return (Providers) replacements.getProviders();
    }

    @Override
    public ResolvedJavaType lookupAppClassLoaderType(String name) {
        if (systemClassLoader == null) {
            ResolvedJavaType classLoaderType = providers.getMetaAccess().lookupJavaType(ClassLoader.class);
            Signature classLoaderGetterSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/ClassLoader;");
            ResolvedJavaMethod getSystemClassLoader = classLoaderType.findMethod("getSystemClassLoader", classLoaderGetterSignature);
            systemClassLoader = invoke(getSystemClassLoader, null);
        }
        return lookupType(name, systemClassLoader);
    }

    @Override
    public ResolvedJavaType lookupPlatformClassLoaderType(String name) {
        return lookupType(name, platformClassLoader);
    }

    @Override
    public ResolvedJavaType lookupBootClassLoaderType(String name) {
        return lookupType(name, JavaConstant.NULL_POINTER);
    }

    private ResolvedJavaType lookupType(String name, JavaConstant classLoader) {
        JavaConstant nameConstant = constantReflection.forString(name);
        JavaConstant cls;
        try {
            cls = invoke(forName, null, nameConstant, JavaConstant.FALSE, classLoader);
            assert !cls.isNull();
        } catch (InvocationException e) {
            JavaConstant exceptionObject = e.getExceptionObject();
            if (classNotFoundExceptionType.isInstance(exceptionObject)) {
                return null;
            }
            throw e;
        }
        return constantReflection.asJavaType(cls);
    }

    @Override
    public Providers getProviders() {
        return providers;
    }

    @Override
    public JavaConstant invoke(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... arguments) {
        if (!(method instanceof EspressoExternalResolvedJavaMethod espressoMethod)) {
            throw new IllegalArgumentException("Expected an EspressoExternalResolvedJavaMethod, got " + safeGetClass(method));
        }
        return espressoMethod.invoke(receiver, arguments);
    }

    @Override
    public JavaConstant asArrayConstant(ResolvedJavaType componentType, JavaConstant... elements) {
        if (!(componentType.getArrayClass() instanceof EspressoExternalResolvedArrayType arrayType)) {
            throw new IllegalArgumentException("Invalid component type");
        }
        EspressoResolvedJavaType elementalType = arrayType.getElementalType();
        Value array;
        boolean isPrimitiveArray;
        int dimensions = arrayType.getDimensions();
        if (elementalType instanceof EspressoExternalResolvedInstanceType elementalInstanceType) {
            array = invokeJVMCIHelper("newObjectArray", elementalInstanceType.getMetaObject(), dimensions, elements.length);
            isPrimitiveArray = false;
        } else {
            JavaKind javaKind = elementalType.getJavaKind();
            assert javaKind.isPrimitive() && javaKind != JavaKind.Void;
            array = invokeJVMCIHelper("newPrimitiveArray", (int) javaKind.getTypeChar(), dimensions, elements.length);
            isPrimitiveArray = dimensions == 1;
        }
        if (isPrimitiveArray) {
            JavaKind javaKind = elementalType.getJavaKind();
            for (int i = 0; i < elements.length; i++) {
                JavaConstant element = elements[i];
                if (javaKind != element.getJavaKind()) {
                    throw new IllegalArgumentException("Element " + i + " should be a " + javaKind + " but was " + element.getJavaKind());
                }
                if (element.isDefaultForKind()) {
                    continue;
                }
                array.setArrayElement(i, element.asBoxedPrimitive());
            }
        } else {
            for (int i = 0; i < elements.length; i++) {
                JavaConstant element = elements[i];
                if (element.isNull()) {
                    continue;
                }
                if (!(element instanceof EspressoExternalObjectConstant objectElement)) {
                    throw new IllegalArgumentException("Element " + i + " should be an espresso object constant, got " + safeGetClass(element));
                }
                try {
                    array.setArrayElement(i, objectElement.getValue());
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException("Element " + i + " is not an instance of the component type", e);
                }
            }
        }
        return new EspressoExternalObjectConstant(this, array);
    }

    static RuntimeException throwHostException(PolyglotException e) {
        if (!e.isGuestException()) {
            throw e;
        }
        Value guestException = e.getGuestObject();
        if (guestException == null || guestException.isNull()) {
            throw e;
        }
        Value guestExceptionMetaobject = guestException.getMetaObject();
        if (guestExceptionMetaobject == null || guestExceptionMetaobject.isNull()) {
            throw e;
        }
        String guestExceptionQualifiedType = guestExceptionMetaobject.getMetaQualifiedName();
        Throwable t = switch (guestExceptionQualifiedType) {
            case "java.lang.IndexOutOfBoundsException" -> new IndexOutOfBoundsException(e.getMessage());
            case "java.lang.IllegalArgumentException" -> new IllegalArgumentException(e.getMessage());
            case "java.lang.ClassFormatError" -> new ClassFormatError(e.getMessage());
            default -> e;
        };
        if (t != e) {
            t.initCause(e);
        }
        throw sneakyThrow(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    Value getPrimitiveClass(JavaKind kind) {
        return switch (kind) {
            case Boolean -> getPrimitiveClass("Boolean");
            case Byte -> getPrimitiveClass("Byte");
            case Short -> getPrimitiveClass("Short");
            case Char -> getPrimitiveClass("Character");
            case Int -> getPrimitiveClass("Integer");
            case Float -> getPrimitiveClass("Float");
            case Long -> getPrimitiveClass("Long");
            case Double -> getPrimitiveClass("Double");
            case Void -> getPrimitiveClass("Void");
            default -> throw new IllegalArgumentException("Bad primitive kind: " + kind);
        };
    }

    private Value getPrimitiveClass(String boxName) {
        String identifier = "java.lang." + boxName;
        Value result = lookupMetaObject(context, identifier).getMember("TYPE");
        assert result != null && !result.isNull() : "Couldn't find TYPE for " + identifier;
        assert result.getMember("static").isMetaObject() : result;
        return result;
    }

    private static Value lookupMetaObject(Context context, String name) {
        Value result = context.getBindings("java").getMember(name);
        assert result != null && !result.isNull() : " Couldn't find " + name;
        assert result.isMetaObject() : result;
        return result;
    }

    Value lookupMetaObject(String name) {
        return lookupMetaObject(context, name);
    }

    JavaType lookupType(String name, ResolvedJavaType accessingClass, boolean resolve) {
        Objects.requireNonNull(accessingClass);
        Objects.requireNonNull(name);
        if (!(accessingClass instanceof EspressoExternalResolvedInstanceType accessingInstantType)) {
            throw new IllegalArgumentException("Expected an espresso instance type as accessing class");
        }
        return lookupType(name, accessingInstantType, resolve);
    }

    EspressoExternalResolvedInstanceType getJavaLangObject() {
        return javaLangObject;
    }

    EspressoExternalResolvedInstanceType[] getArrayInterfaces() {
        return arrayInterfaces;
    }

    JavaType lookupType(String name, EspressoExternalResolvedInstanceType accessingClass, boolean resolve) {
        assert !name.isEmpty();
        if (name.charAt(0) == '[') {
            int dims = 0;
            do {
                dims++;
            } while (dims < name.length() && name.charAt(dims) == '[');
            if (dims >= name.length()) {
                throw new IllegalArgumentException("Invalid type: " + name);
            }
            JavaType javaType = lookupNonArrayType(name.substring(dims), accessingClass, resolve);
            if (javaType instanceof EspressoResolvedJavaType resolved) {
                return new EspressoExternalResolvedArrayType(resolved, dims, this);
            }
            if (resolve) {
                throw new NoClassDefFoundError(name);
            }
            return UnresolvedJavaType.create(name);
        }
        return lookupNonArrayType(name, accessingClass, resolve);
    }

    private JavaType lookupNonArrayType(String name, EspressoExternalResolvedInstanceType accessingClass, boolean resolve) {
        assert !name.isEmpty() && name.charAt(0) != '[';
        if (name.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            return forPrimitiveKind(kind);
        }
        assert name.charAt(0) == 'L' && name.charAt(name.length() - 1) == ';';
        Value meta = invokeJVMCIHelper("lookupInstanceType", name, accessingClass.getMetaObject(), resolve);
        assert meta != null;
        if (meta.isNull()) {
            assert !resolve;
            return UnresolvedJavaType.create(name);
        }
        return new EspressoExternalResolvedInstanceType(this, meta);
    }

    EspressoExternalResolvedPrimitiveType forPrimitiveKind(JavaKind kind) {
        if (!kind.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive kind: " + kind);
        }
        return forPrimitiveBasicType(kind.getBasicType());
    }

    private EspressoExternalResolvedPrimitiveType forPrimitiveBasicType(int basicType) {
        if (primitives[basicType] == null) {
            throw new IllegalArgumentException("No primitive type for basic type " + basicType);
        }
        return primitives[basicType];
    }

    Value invokeJVMCIHelper(String method, Object... args) {
        return jvmciHelper.invokeMember(method, args);
    }

    JavaType toJavaType(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return UnresolvedJavaType.create(value.asString());
        }
        return toResolvedJavaType(value);
    }

    private EspressoResolvedJavaType toResolvedJavaType(Value value) {
        // See com.oracle.truffle.espresso.impl.jvmci.external.TypeWrapper
        assert !value.isNull();
        assert !value.isString();
        char kindChar = (char) value.getMember("kind").asInt();
        if (kindChar == '[') {
            EspressoResolvedJavaType elemental = toResolvedJavaType(value.getMember("elemental"));
            int dimensions = value.getMember("dimensions").asInt();
            return new EspressoExternalResolvedArrayType(elemental, dimensions, this);
        } else if (kindChar == 'A') {
            // JVMCI and Espresso JavaKind return `A` as the object type char
            return new EspressoExternalResolvedInstanceType(this, value.getMember("meta"));
        } else {
            return forPrimitiveKind(JavaKind.fromPrimitiveOrVoidTypeChar(kindChar));
        }
    }

    public EspressoExternalObjectConstant unsafeAllocateInstance(EspressoExternalResolvedInstanceType type) {
        return (EspressoExternalObjectConstant) invoke(unsafeAllocateInstance, unsafe, getProviders().getConstantReflection().asJavaClass(type));
    }
}
