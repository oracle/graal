/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.util;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.shared.util.ReflectionUtil;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaModuleLayer;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/// This class supports Native Image's use of a [guest context][VMAccess]. It manages a
/// lazily [planted][#plantConfiguration(VMAccess)] [singleton][#get()] and offers
/// helper methods for extending the core functionality in [VMAccess].
///
/// To prepare for a world in which multiple images are built in a single process, with
/// each image build using a fresh guest context, references to guest context values
/// obtained from this object must not be stored in static fields. Such values will be
/// stale/invalid when the guest context is discarded.
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestAccess implements VMAccess {

    private final VMAccess delegate;

    // Caches
    private final Map<Class<?>, ResolvedJavaType> typeCache = new ConcurrentHashMap<>();
    private final Map<Executable, ResolvedJavaMethod> methodCache = new ConcurrentHashMap<>();
    private final Map<Field, ResolvedJavaField> fieldCache = new ConcurrentHashMap<>();
    private final Map<RecordComponent, ResolvedJavaRecordComponent> recordCache = new ConcurrentHashMap<>();
    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final SnippetReflectionProvider snippetReflection;

    /// The singleton initialized by [#plantConfiguration(VMAccess)].
    private static GuestAccess singleton;

    /**
     * Guards against multiple calls to {@link #plantConfiguration(VMAccess)}. The value is a stack
     * trace of the first call.
     */
    private static volatile String providersInit;

    private GuestAccess(VMAccess delegate) {
        this.delegate = delegate;
        Providers providers = delegate.getProviders();
        this.metaAccess = providers.getMetaAccess();
        this.constantReflection = providers.getConstantReflection();
        this.snippetReflection = providers.getSnippetReflection();
    }

    /// Prefix of system properties used to configure guest access.
    private static final String PROPERTY_PREFIX = ImageInfo.PROPERTY_NATIVE_IMAGE_PREFIX + "vmaccess.";

    /// Name of the property for selecting the guest context implementation.
    public static final String NAME_PROPERTY = PROPERTY_PREFIX + "name";

    //@formatter:off
    /// Name of the property for setting guest context options. If the value of the option
    /// starts with a non-alphanumeric character other than [File#separatorChar], then that
    /// character is used as the delimiter between multiple options. Empty options are silently
    /// ignored. For example:
    ///
    /// | option value                | options sent to [Builder#vmOption] |
    /// |-----------------------------|------------------------------------|
    /// | `log.level=ALL`             | "log.level=ALL"                    |
    /// | `,log.level=ALL,foo=bar,,,` | "log.level=ALL", "foo=bar"         |
    //@formatter:on
    public static final String OPTIONS_PROPERTY = PROPERTY_PREFIX + "options";

    /// Gets a [VMAccess] builder whose [name][Builder#getVMAccessName] is
    /// specified by the {@value #NAME_PROPERTY} system property. If no name is specified,
    /// `"host"` is used.
    ///
    /// @throws GraalError if the requested builder cannot be found
    public static VMAccess.Builder getVmAccessBuilder() {
        String oldProp = NAME_PROPERTY.replace(".name", "name");
        if (System.getProperty(oldProp) != null) {
            throw new GraalError("Use %s instead of %s to select VMAccess implementation", NAME_PROPERTY, oldProp);
        }
        String requestedAccessName = GraalServices.getSavedProperty(NAME_PROPERTY);
        String accessName = requestedAccessName == null ? "host" : requestedAccessName;
        Module vmAccessModule = Builder.class.getModule();
        ModuleLayer vmAccessLayer = vmAccessModule.getLayer();
        ServiceLoader<VMAccess.Builder> loader;
        if (vmAccessLayer == null) {
            // VMAccess was loaded on the class path (as an unnamed module).
            // In this context, it's expected that all VMAccess providers
            // are also on the class path.
            loader = ServiceLoader.load(VMAccess.Builder.class);
        } else {
            loader = ServiceLoader.load(vmAccessLayer, VMAccess.Builder.class);
        }
        VMAccess.Builder selected = null;
        List<VMAccess.Builder> builders = new ArrayList<>();
        for (VMAccess.Builder builder : loader) {
            builders.add(builder);
            if (accessName.equals(builder.getVMAccessName())) {
                selected = builder;
                break;
            }
        }
        if (selected == null) {
            if (builders.isEmpty()) {
                throw new GraalError("No %s service providers found", VMAccess.Builder.class.getName());
            }
            String available = builders.stream().map(b -> "'" + b.getVMAccessName() + "'").collect(Collectors.joining(", "));
            String origin = requestedAccessName == null ? "" : "specified by system property %s ".formatted(NAME_PROPERTY);
            throw new GraalError("%s service provider '%s' %snot found. Available providers: %s",
                            VMAccess.Builder.class.getName(),
                            accessName,
                            origin,
                            available);
        }
        if ("espresso".equals(selected.getVMAccessName())) {
            // Make sure we use the modules prepared for GraalVM
            selected.vmOption("JavaHome=" + System.getProperty("java.home"));
            // This is needed for Word types:
            selected.addModule("org.graalvm.word");
        }
        String options = GraalServices.getSavedProperty(OPTIONS_PROPERTY);
        if (options != null && !options.isEmpty()) {
            char char0 = options.charAt(0);
            if (!Character.isLetterOrDigit(char0) && char0 != File.separatorChar) {
                for (var option : options.substring(1).split(Pattern.quote(String.valueOf(char0)))) {
                    if (!option.isEmpty()) {
                        selected.vmOption(option);
                    }
                }
            } else {
                selected.vmOption(options);
            }
        }
        return selected;
    }

    /**
     * Initializes the {@link GuestAccess} singleton based on {@code vmAccess}.
     * <p>
     * If {@code vmAccess != null}, this method must be called before calling {@link #get()} and it
     * can only be called once to ensure the whole system uses a stable configuration.
     * <p>
     * Naming this method with a "plant" prefix (as opposed to "set" or "init") is intentional. It
     * conveys the fact that this initialization is done "from the side" where as ideally it should
     * be done in the static initializer of this class.
     *
     * @param access the {@link VMAccess} value to use for configuring {@link GuestAccess}. If
     *            {@code null}, then {@link #getVmAccessBuilder()} is used to create an instance
     *            that reflects the host configuration.
     */
    public static synchronized void plantConfiguration(VMAccess access) {
        GraalError.guarantee(providersInit == null, "Providers have already been planted: %s", providersInit);
        if (access == null) {
            VMAccess.Builder builder = getVmAccessBuilder();
            String cp = System.getProperty("java.class.path");
            if (cp != null) {
                builder.classPath(Arrays.asList(cp.split(File.pathSeparator)));
            }
            singleton = new GuestAccess(builder.build());
        } else {
            singleton = new GuestAccess(access);
        }
        StringWriter sw = new StringWriter();
        new Exception("providers previously planted here:").printStackTrace(new PrintWriter(sw));
        providersInit = sw.toString();
    }

    /**
     * Shortcut for {@code getProviders().getCodeCache().getTarget()}.
     */
    public TargetDescription getTarget() {
        return getProviders().getCodeCache().getTarget();
    }

    /**
     * Shortcut for {@code getProviders().getSnippetReflection()}.
     */
    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    /**
     * Gets the singleton {@link GuestAccess} value. If an externally configured {@link VMAccess} is
     * being used, then it must be {@linkplain #plantConfiguration(VMAccess) set} prior to the first
     * call to this method.
     */
    public static GuestAccess get() {
        if (providersInit == null) {
            synchronized (GuestAccess.class) {
                if (providersInit == null) {
                    plantConfiguration(null);
                }
            }
        }
        return singleton;
    }

    /**
     * Gets the {@link Annotated} equivalent value for element.
     *
     * @return {@code null} if element is a {@link Package} that has no annotations
     */
    public Annotated toAnnotated(AnnotatedElement element) {
        return switch (element) {
            case Class<?> clazz -> get().lookupType(clazz);
            case Method method -> lookupMethod(method);
            case Constructor<?> cons -> lookupMethod(cons);
            case Package pkg -> lookupPackage(pkg);
            case Field field -> lookupField(field);
            case RecordComponent rc -> lookupRecordComponent(rc);
            default -> throw new IllegalArgumentException(String.valueOf(element));
        };
    }

    public ResolvedJavaType lookupType(Class<?> cls) {
        return typeCache.computeIfAbsent(cls, metaAccess::lookupJavaType);
    }

    public ResolvedJavaMethod lookupMethod(Executable exe) {
        return methodCache.computeIfAbsent(exe, metaAccess::lookupJavaMethod);
    }

    public ResolvedJavaField lookupField(Field field) {
        return fieldCache.computeIfAbsent(field, metaAccess::lookupJavaField);
    }

    public ResolvedJavaRecordComponent lookupRecordComponent(RecordComponent rc) {
        return recordCache.computeIfAbsent(rc, metaAccess::lookupJavaRecordComponent);
    }

    private ResolvedJavaPackage lookupPackage(Package pkg) {
        /*
         * All Packages should have at least the package-info.class. We convert that Class object to
         * a ResolvedJavaType and use that to query the ResolvedJavaPackage.
         */
        Method getPackageInfo = ReflectionUtil.lookupMethod(Package.class, "getPackageInfo");
        Class<?> packageInfo = ReflectionUtil.invokeMethod(getPackageInfo, pkg);
        if (packageInfo == null) {
            throw new NullPointerException("Package info of " + pkg.getName() + " is null");
        }
        return getPackage(lookupType(packageInfo));
    }

    /**
     * Instantiates an instance of {@code supplierType} in the guest and invokes
     * {@link BooleanSupplier#getAsBoolean()} on it.
     *
     * @param supplierType a concrete {@link java.util.function.BooleanSupplier} type
     */
    public boolean callBooleanSupplier(ResolvedJavaType supplierType) {
        ResolvedJavaMethod cons = JVMCIReflectionUtil.getDeclaredConstructor(false, supplierType);
        JavaConstant supplier = invoke(cons, null);
        ResolvedJavaMethod getAsBoolean = JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, BooleanSupplier.class, "getAsBoolean");
        return invoke(getAsBoolean, supplier).asBoolean();
    }

    /**
     * Instantiates an instance of {@code functionType} in the guest and invokes
     * {@link Function#apply(Object)} on it.
     *
     * @param functionType a concrete {@link java.util.function.BooleanSupplier} type
     * @param arg the single function argument for {@code apply}
     */
    public JavaConstant callFunction(ResolvedJavaType functionType, JavaConstant arg) {
        ResolvedJavaMethod cons = JVMCIReflectionUtil.getDeclaredConstructor(false, functionType);
        JavaConstant function = invoke(cons, null);
        ResolvedJavaMethod apply = JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, Function.class, "apply", Object.class);
        return invoke(apply, function, arg);
    }

    /**
     * Shortcut for {@code lookupAppClassLoaderType(name)}.
     */
    public ResolvedJavaType lookupType(String name) {
        return lookupAppClassLoaderType(name);
    }

    /**
     * Looks up a method in the guest.
     *
     * @param declaringType the class declaring the method
     * @param name name of the method
     * @param parameterTypes types of the method's parameters
     */
    public ResolvedJavaMethod lookupMethod(ResolvedJavaType declaringType, String name, Class<?>... parameterTypes) {
        return JVMCIReflectionUtil.getUniqueDeclaredMethod(false, metaAccess, declaringType, name, parameterTypes);
    }

    /**
     * Converts the host string {@code value} to a guest instance and returns a reference to it as a
     * {@link JavaConstant}.
     */
    public JavaConstant asGuestString(String value) {
        return constantReflection.forString(value);
    }

    /**
     * Converts the guest {@code val} to a host {@code type} object instance.
     *
     * @return {@code null} if {@code val.isNull()} otherwise a non-null {@code type} instance
     * @throws IllegalArgumentException if conversion is not supported for {@code type}
     */
    public <T> T asHostObject(Class<T> type, JavaConstant val) {
        if (val.isNull()) {
            return null;
        }
        T res = snippetReflection.asObject(type, val);
        if (res == null) {
            throw new IllegalArgumentException("Cannot convert guest constant to a %s: %s".formatted(type.getName(), val));
        }
        return res;
    }

    /**
     * Boxes a primitive {@link JavaConstant} into its corresponding object wrapper.
     * <p>
     * This method takes a primitive {@link JavaConstant} and invokes the corresponding
     * {@code valueOf} method on the wrapper class to box the primitive value in the guest.
     * <p>
     * For example, if the input {@code primitive} is of kind {@link JavaKind#Int}, this method will
     * invoke {@code Integer.valueOf(primitive.asInt())} to box the integer value.
     *
     * @param primitive the primitive {@link JavaConstant} to be boxed
     * @return a {@link JavaConstant} representing the boxed object
     * @throws IllegalArgumentException if the kind of {@code primitive} is not a primitive type
     */
    public JavaConstant boxPrimitive(JavaConstant primitive) {
        if (!primitive.getJavaKind().isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive: " + primitive);
        }
        return switch (primitive.getJavaKind()) {
            case Boolean -> invokeStatic(GuestElements.get().java_lang_Boolean_valueOf, primitive);
            case Byte -> invokeStatic(GuestElements.get().java_lang_Byte_valueOf, primitive);
            case Short -> invokeStatic(GuestElements.get().java_lang_Short_valueOf, primitive);
            case Char -> invokeStatic(GuestElements.get().java_lang_Character_valueOf, primitive);
            case Int -> invokeStatic(GuestElements.get().java_lang_Integer_valueOf, primitive);
            case Long -> invokeStatic(GuestElements.get().java_lang_Long_valueOf, primitive);
            case Float -> invokeStatic(GuestElements.get().java_lang_Float_valueOf, primitive);
            case Double -> invokeStatic(GuestElements.get().java_lang_Double_valueOf, primitive);
            default -> throw new IllegalArgumentException("Unsupported primitive kind: " + primitive.getJavaKind());
        };
    }

    /**
     * Shortcut for {@code asHostObject(String.class, val)}.
     */
    public String asHostString(JavaConstant val) {
        return asHostObject(String.class, val);
    }

    public JavaConstant invokeStatic(ResolvedJavaMethod method, JavaConstant... args) {
        assert method.isStatic() : method;
        return invoke(method, null, args);
    }

    // delegating methods

    @Override
    public Providers getProviders() {
        return delegate.getProviders();
    }

    @Override
    public boolean owns(ResolvedJavaType value) {
        return delegate.owns(value);
    }

    @Override
    public boolean owns(ResolvedJavaMethod value) {
        return delegate.owns(value);
    }

    @Override
    public boolean owns(ResolvedJavaField value) {
        return delegate.owns(value);
    }

    @Override
    public JavaConstant invoke(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... args) {
        return delegate.invoke(method, receiver, args);
    }

    @Override
    public JavaConstant asArrayConstant(ResolvedJavaType componentType, JavaConstant... elements) {
        return delegate.asArrayConstant(componentType, elements);
    }

    @Override
    public ResolvedJavaMethod asResolvedJavaMethod(Constant constant) {
        return delegate.asResolvedJavaMethod(constant);
    }

    @Override
    public ResolvedJavaField asResolvedJavaField(Constant constant) {
        return delegate.asResolvedJavaField(constant);
    }

    @Override
    public JavaConstant asExecutableConstant(ResolvedJavaMethod method) {
        return delegate.asExecutableConstant(method);
    }

    @Override
    public JavaConstant asFieldConstant(ResolvedJavaField field) {
        return delegate.asFieldConstant(field);
    }

    @Override
    public ResolvedJavaType lookupAppClassLoaderType(String name) {
        return delegate.lookupAppClassLoaderType(name);
    }

    @Override
    public ResolvedJavaType lookupPlatformClassLoaderType(String name) {
        return delegate.lookupPlatformClassLoaderType(name);
    }

    @Override
    public ResolvedJavaType lookupBootClassLoaderType(String name) {
        return delegate.lookupBootClassLoaderType(name);
    }

    @Override
    public ResolvedJavaModule getModule(ResolvedJavaType type) {
        return delegate.getModule(type);
    }

    @Override
    public ResolvedJavaPackage getPackage(ResolvedJavaType type) {
        return delegate.getPackage(type);
    }

    @Override
    public Stream<ResolvedJavaPackage> bootLoaderPackages() {
        return delegate.bootLoaderPackages();
    }

    @Override
    public ResolvedJavaModuleLayer bootModuleLayer() {
        return delegate.bootModuleLayer();
    }

    @Override
    public URL getCodeSourceLocation(ResolvedJavaType type) {
        return delegate.getCodeSourceLocation(type);
    }
}
