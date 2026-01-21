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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

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
import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaModuleLayer;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
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
    private final EspressoExternalResolvedInstanceType[] arrayInterfaces;
    private final Providers providers;
    private final JavaConstant platformClassLoader;
    private final JavaConstant unsafe;
    private JavaConstant systemClassLoader;

    // Checkstyle: stop field name check
    // j.l.Object
    private final EspressoExternalResolvedInstanceType java_lang_Object;
    private final ResolvedJavaMethod java_lang_Object_toString;
    // j.l.Class
    private final ResolvedJavaMethod java_lang_Class_forName_String_boolean_ClassLoader;
    private final ResolvedJavaMethod java_lang_Class_getProtectionDomain;
    // j.l.ClassNotFoundException
    private final ResolvedJavaType java_lang_ClassNotFoundException;
    // j.l.Module
    final ResolvedJavaType java_lang_Module;
    private final ResolvedJavaMethod java_lang_Class_getModule;
    private final ResolvedJavaMethod java_lang_Class_getPackage;
    final EspressoExternalResolvedJavaMethod java_lang_Module_getDescriptor;
    final EspressoExternalResolvedJavaMethod java_lang_Module_getPackages;
    final EspressoExternalResolvedJavaMethod java_lang_Module_isExported_String;
    final EspressoExternalResolvedJavaMethod java_lang_Module_isExported_String_Module;
    final EspressoExternalResolvedJavaMethod java_lang_Module_isOpen_String;
    final EspressoExternalResolvedJavaMethod java_lang_Module_isOpen_String_Module;
    final EspressoExternalResolvedJavaMethod java_lang_Module_getName;
    // j.l.module.ModuleDescriptor
    final EspressoExternalResolvedJavaMethod java_lang_module_ModuleDescriptor_isAutomatic;
    // j.l.NamedPackage
    final EspressoExternalResolvedJavaField java_lang_NamedPackage_module;
    // j.l.Package
    final EspressoExternalResolvedJavaMethod java_lang_Package_getPackageInfo;
    // java.security
    private final ResolvedJavaMethod java_security_ProtectionDomain_getCodeSource;
    private final ResolvedJavaMethod java_security_CodeSource_getLocation;
    // jdk.internal.misc.Unsafe
    private final ResolvedJavaMethod jdk_internal_misc_Unsafe_allocateInstance_Class;
    // Checkstyle: resume field name check

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
        java_lang_Object = new EspressoExternalResolvedInstanceType(this, requireMetaObject(context, "java.lang.Object"));
        arrayInterfaces = new EspressoExternalResolvedInstanceType[]{
                        new EspressoExternalResolvedInstanceType(this, requireMetaObject(context, "java.io.Serializable")),
                        new EspressoExternalResolvedInstanceType(this, requireMetaObject(context, "java.lang.Cloneable")),
        };
        providers = createProviders();

        ResolvedJavaType classLoaderType = providers.getMetaAccess().lookupJavaType(ClassLoader.class);
        Signature classLoaderGetterSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/ClassLoader;");
        ResolvedJavaMethod getPlatformClassLoader = classLoaderType.findMethod("getPlatformClassLoader", classLoaderGetterSignature);
        platformClassLoader = invoke(getPlatformClassLoader, null);

        ResolvedJavaType classType = providers.getMetaAccess().lookupJavaType(Class.class);
        Signature forNameSignature = providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        java_lang_Class_forName_String_boolean_ClassLoader = classType.findMethod("forName", forNameSignature);
        java_lang_ClassNotFoundException = providers.getMetaAccess().lookupJavaType(ClassNotFoundException.class);

        ResolvedJavaType unsafeType = lookupBootClassLoaderType("jdk.internal.misc.Unsafe");
        jdk_internal_misc_Unsafe_allocateInstance_Class = unsafeType.findMethod("allocateInstance", providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/Class;)Ljava/lang/Object;"));
        ResolvedJavaMethod unsafeGetter = unsafeType.findMethod("getUnsafe", providers.getMetaAccess().parseMethodDescriptor("()Ljdk/internal/misc/Unsafe;"));
        unsafe = invoke(unsafeGetter, null);

        ResolvedJavaType protectionDomainType = providers.getMetaAccess().lookupJavaType(ProtectionDomain.class);
        ResolvedJavaType codeSourceType = providers.getMetaAccess().lookupJavaType(CodeSource.class);

        Signature getProtectionDomainSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/security/ProtectionDomain;");
        java_lang_Class_getProtectionDomain = classType.findMethod("getProtectionDomain", getProtectionDomainSignature);

        Signature getCodeSourceSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/security/CodeSource;");
        java_security_ProtectionDomain_getCodeSource = protectionDomainType.findMethod("getCodeSource", getCodeSourceSignature);

        Signature getLocationSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/net/URL;");
        java_security_CodeSource_getLocation = codeSourceType.findMethod("getLocation", getLocationSignature);

        Signature toStringSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/String;");
        java_lang_Object_toString = java_lang_Object.findMethod("toString", toStringSignature);

        Signature getModuleSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/Module;");
        java_lang_Class_getModule = classType.findMethod("getModule", getModuleSignature);

        Signature getPackageSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/Package;");
        java_lang_Class_getPackage = classType.findMethod("getPackage", getPackageSignature);

        java_lang_Module = providers.getMetaAccess().lookupJavaType(Module.class);

        Signature getDescriptorSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/module/ModuleDescriptor;");
        java_lang_Module_getDescriptor = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("getDescriptor", getDescriptorSignature);

        Signature getPackagesSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/util/Set;");
        java_lang_Module_getPackages = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("getPackages", getPackagesSignature);

        Signature isExportedStringSignature = providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/String;)Z");
        java_lang_Module_isExported_String = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("isExported", isExportedStringSignature);

        Signature isExportedStringModuleSignature = providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/String;Ljava/lang/Module;)Z");
        java_lang_Module_isExported_String_Module = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("isExported", isExportedStringModuleSignature);

        Signature isOpenStringSignature = providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/String;)Z");
        java_lang_Module_isOpen_String = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("isOpen", isOpenStringSignature);

        Signature isOpenStringModuleSignature = providers.getMetaAccess().parseMethodDescriptor("(Ljava/lang/String;Ljava/lang/Module;)Z");
        java_lang_Module_isOpen_String_Module = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("isOpen", isOpenStringModuleSignature);

        Signature getNameSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/String;");
        java_lang_Module_getName = (EspressoExternalResolvedJavaMethod) java_lang_Module.findMethod("getName", getNameSignature);

        ResolvedJavaType moduleDescriptorType = providers.getMetaAccess().lookupJavaType(java.lang.module.ModuleDescriptor.class);
        Signature isAutomaticSignature = providers.getMetaAccess().parseMethodDescriptor("()Z");
        java_lang_module_ModuleDescriptor_isAutomatic = (EspressoExternalResolvedJavaMethod) moduleDescriptorType.findMethod("isAutomatic", isAutomaticSignature);

        ResolvedJavaType namedPackageType = lookupBootClassLoaderType("java.lang.NamedPackage");
        java_lang_NamedPackage_module = (EspressoExternalResolvedJavaField) lookupField(namedPackageType, "module");

        ResolvedJavaType packageType = providers.getMetaAccess().lookupJavaType(java.lang.Package.class);
        Signature getPackageInforSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/Class;");
        java_lang_Package_getPackageInfo = (EspressoExternalResolvedJavaMethod) packageType.findMethod("getPackageInfo", getPackageInforSignature);

        JVMCIError.guarantee(java_lang_Class_forName_String_boolean_ClassLoader != null, "Required method: forName");
        JVMCIError.guarantee(jdk_internal_misc_Unsafe_allocateInstance_Class != null, "Required method: unsafeAllocateInstance");
        JVMCIError.guarantee(java_lang_Class_getProtectionDomain != null, "Required method: getProtectionDomain");
        JVMCIError.guarantee(java_security_ProtectionDomain_getCodeSource != null, "Required method: getCodeSource");
        JVMCIError.guarantee(java_security_CodeSource_getLocation != null, "Required method: getLocation");
        JVMCIError.guarantee(java_lang_Object_toString != null, "Required method: toString");
        JVMCIError.guarantee(java_lang_Class_getModule != null, "Required method: getModule");
        JVMCIError.guarantee(java_lang_Class_getPackage != null, "Required method: getPackage");
        JVMCIError.guarantee(java_lang_Module_getDescriptor != null, "Required method: Module.getDescriptor");
        JVMCIError.guarantee(java_lang_module_ModuleDescriptor_isAutomatic != null, "Required method: ModuleDescriptor.isAutomatic");
        JVMCIError.guarantee(java_lang_Module_getPackages != null, "Required method: Module.getPackages");
        JVMCIError.guarantee(java_lang_Module_isExported_String != null, "Required method: Module.isExported(String)");
        JVMCIError.guarantee(java_lang_Module_isExported_String_Module != null, "Required method: Module.isExported(String, Module)");
        JVMCIError.guarantee(java_lang_Module_isOpen_String != null, "Required method: Module.isOpen(String)");
        JVMCIError.guarantee(java_lang_Module_isOpen_String_Module != null, "Required method: Module.isOpen(String, Module)");
        JVMCIError.guarantee(java_lang_Module_getName != null, "Required method: Module.getName");
        JVMCIError.guarantee(java_lang_Package_getPackageInfo != null, "Required method: Package.getPackageInfo()");
        JVMCIError.guarantee(java_lang_NamedPackage_module != null, "Required field: NamedPackage.module");
    }

    private static ResolvedJavaField lookupField(ResolvedJavaType namedPackageType, String fieldName) {
        ResolvedJavaField[] namedPackageFields = namedPackageType.getInstanceFields(false);
        for (ResolvedJavaField field : namedPackageFields) {
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        return null;
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
        SnippetReflectionProvider snippetReflection = new EspressoExternalSnippetReflectionProvider(this, metaAccess, constantReflection);
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

    @Override
    public ResolvedJavaModule getModule(ResolvedJavaType type) {
        JavaConstant originalClass = providers.getConstantReflection().asJavaClass(type);
        JavaConstant module = invoke(java_lang_Class_getModule, originalClass);
        if (!(module instanceof EspressoExternalObjectConstant espressoConstant)) {
            throw new IllegalArgumentException("Constant has unexpected type " + module.getClass() + ": " + module);
        }
        Value value = espressoConstant.getValue();
        return new EspressoExternalResolvedJavaModule(this, value);
    }

    @Override
    public ResolvedJavaPackage getPackage(ResolvedJavaType type) {
        JavaConstant originalClass = providers.getConstantReflection().asJavaClass(type);
        JavaConstant pkg = invoke(java_lang_Class_getPackage, originalClass);
        if (!(pkg instanceof EspressoExternalObjectConstant espressoConstant)) {
            throw new IllegalArgumentException("Constant has unexpected type " + pkg.getClass() + ": " + pkg);
        }
        Value value = espressoConstant.getValue();
        return new EspressoExternalResolvedJavaPackage(this, value);
    }

    @Override
    public Stream<ResolvedJavaPackage> bootLoaderPackages() {
        /*
         * Obtain jdk.internal.loader.BootLoader.packages() from the guest and materialize it to an
         * array to bridge into a Java Stream on the host.
         */
        Value bootLoaderMeta = requireMetaObject("jdk.internal.loader.BootLoader");
        Value stream = bootLoaderMeta.getMember("packages").execute();
        // Stream#toArray() -> Object[]
        Value array = stream.invokeMember("toArray");
        if (array == null || array.isNull()) {
            return Stream.empty();
        }
        long size = array.getArraySize();
        Stream.Builder<ResolvedJavaPackage> builder = Stream.builder();
        for (long i = 0; i < size; i++) {
            Value pkg = array.getArrayElement(i);
            if (pkg != null && !pkg.isNull()) {
                builder.add(new EspressoExternalResolvedJavaPackage(this, pkg));
            }
        }
        return builder.build();
    }

    @Override
    public ResolvedJavaModuleLayer bootModuleLayer() {
        // Obtain java.lang.ModuleLayer.boot() from the guest and wrap it.
        Value moduleLayerMeta = requireMetaObject("java.lang.ModuleLayer");
        Value bootLayer = moduleLayerMeta.getMember("boot").execute();
        return new EspressoExternalResolvedJavaModuleLayer(this, bootLayer);
    }

    @Override
    public URL getCodeSourceLocation(ResolvedJavaType type) {
        JavaConstant originalClass = providers.getConstantReflection().asJavaClass(type);

        JavaConstant pd = invoke(java_lang_Class_getProtectionDomain, originalClass);
        JavaConstant cs = invoke(java_security_ProtectionDomain_getCodeSource, pd);
        if (cs.isNull()) {
            return null;
        }
        JavaConstant location = invoke(java_security_CodeSource_getLocation, cs);
        if (location.isNull()) {
            return null;
        }
        JavaConstant locationStringConstant = invoke(java_lang_Object_toString, location);
        String locationString = providers.getSnippetReflection().asObject(String.class, locationStringConstant);
        try {
            return new URI(locationString).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    private ResolvedJavaType lookupType(String name, JavaConstant classLoader) {
        JavaConstant nameConstant = constantReflection.forString(name);
        JavaConstant cls;
        try {
            cls = invoke(java_lang_Class_forName_String_boolean_ClassLoader, null, nameConstant, JavaConstant.FALSE, classLoader);
            JVMCIError.guarantee(!cls.isNull(), "forName should return a result or throw");
        } catch (InvocationException e) {
            JavaConstant exceptionObject = e.getExceptionObject();
            if (java_lang_ClassNotFoundException.isInstance(exceptionObject)) {
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

    /**
     * Marker interface for a concrete implementation of a {@link ResolvedJavaType},
     * {@link ResolvedJavaMethod} or {@link ResolvedJavaField} owned by
     * {@link EspressoExternalVMAccess}.
     */
    interface Element {
    }

    @Override
    public boolean owns(ResolvedJavaType value) {
        return value instanceof Element;
    }

    @Override
    public boolean owns(ResolvedJavaMethod value) {
        return value instanceof Element;
    }

    @Override
    public boolean owns(ResolvedJavaField value) {
        return value instanceof Element;
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

    @Override
    public ResolvedJavaMethod asResolvedJavaMethod(Constant constant) {
        if (constant instanceof EspressoExternalObjectConstant espressoConstant) {
            // j.l.r.Executable?
            Value value = espressoConstant.getValue();
            String name = value.getMetaObject().getMetaQualifiedName();
            if ("java.lang.reflect.Method".equals(name) || "java.lang.reflect.Constructor".equals(name)) {
                return EspressoExternalConstantReflectionProvider.methodAsJavaResolvedMethod(value, this);
            }
        }
        return null;
    }

    @Override
    public ResolvedJavaField asResolvedJavaField(Constant constant) {
        if (constant instanceof EspressoExternalObjectConstant espressoConstant) {
            // j.l.r.Field?
            Value value = espressoConstant.getValue();
            if ("java.lang.reflect.Field".equals(value.getMetaObject().getMetaQualifiedName())) {
                return EspressoExternalConstantReflectionProvider.fieldAsJavaResolvedField(value, this);
            }
        }
        return null;
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
        Value result = requireMetaObject(context, identifier).getMember("TYPE");
        assert result.getMember("static").isMetaObject() : result;
        return result;
    }

    private static Value requireMetaObject(Context context, String name) {
        Value result = lookupMetaObject(context, name);
        JVMCIError.guarantee(result != null && !result.isNull(), "Couldn't find %s", name);
        return result;
    }

    private static Value lookupMetaObject(Context context, String name) {
        Value result = context.getBindings("java").getMember(name);
        JVMCIError.guarantee(result == null || result.isMetaObject(), "Unexpected result: %s", result);
        return result;
    }

    Value lookupMetaObject(String name) {
        return lookupMetaObject(context, name);
    }

    Value requireMetaObject(String name) {
        return requireMetaObject(context, name);
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
        return java_lang_Object;
    }

    EspressoExternalResolvedInstanceType[] getArrayInterfaces() {
        return arrayInterfaces;
    }

    JavaType lookupType(String name, EspressoExternalResolvedInstanceType accessingClass, boolean resolve) {
        JVMCIError.guarantee(!name.isEmpty(), "Name must not be empty");
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
        JVMCIError.guarantee(name.charAt(0) == 'L' && name.charAt(name.length() - 1) == ';', "Invalid type: " + name);
        Value meta = invokeJVMCIHelper("lookupInstanceType", name, accessingClass.getMetaObject(), resolve);
        if (meta.isNull()) {
            JVMCIError.guarantee(!resolve, "Expected a resolved type");
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
        return (EspressoExternalObjectConstant) invoke(jdk_internal_misc_Unsafe_allocateInstance_Class, unsafe, getProviders().getConstantReflection().asJavaClass(type));
    }

    byte[] getRawAnnotationBytes(Value metaObject, int category) {
        Value bytes = invokeJVMCIHelper("getRawAnnotationBytes", metaObject, category);
        if (bytes.isNull()) {
            return null;
        }
        long size = bytes.getBufferSize();
        byte[] result = new byte[Math.toIntExact(size)];
        bytes.readBuffer(0, result, 0, result.length);
        return result;
    }

    public boolean hasAnnotations(Value metaObject) {
        return invokeJVMCIHelper("hasAnnotations", metaObject).asBoolean();
    }
}
