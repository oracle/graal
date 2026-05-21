/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeReflection;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.net.NetProperties;

@TargetClass(value = java.net.URL.class, onlyWith = JavaNetSubstitutions.BuildTimeURLProtocols.class)
final class Target_java_net_URL {

    @Delete private static Hashtable<?, ?> handlers;

    @Substitute
    private static URLStreamHandler getURLStreamHandler(String protocol) throws MalformedURLException {
        /*
         * The original version of this method does not throw MalformedURLException directly, but
         * instead returns null if no handler is found. The callers then check the result and throw
         * the exception when the return value is null. Implementing our substitution the same way
         * would mean that we cannot provide a helpful exception message why a protocol is not
         * available, and how to add a protocol at image build time.
         */
        return JavaNetSubstitutions.getURLStreamHandler(protocol);
    }

    @Substitute
    @SuppressWarnings("unused")
    public static void setURLStreamHandlerFactory(URLStreamHandlerFactory fac) {
        VMError.unsupportedFeature("Setting a custom URLStreamHandlerFactory.");
    }
}

@TargetClass(value = java.net.URL.class, onlyWith = JavaNetSubstitutions.RuntimeURLProtocols.class)
final class Target_java_net_URL_WithRuntimeURLProtocols {

    @Alias
    @KeepOriginal
    @TargetElement(name = "getURLStreamHandler")
    private static native URLStreamHandler getURLStreamHandlerOriginal(String protocol);

    @Substitute
    private static URLStreamHandler getURLStreamHandler(String protocol) {
        if (URLProtocolsSupport.isDisabled(protocol)) {
            return null;
        }
        URLStreamHandler result = URLProtocolsSupport.get(protocol);
        return result != null ? result : getURLStreamHandlerOriginal(protocol);
    }
}

@TargetClass(className = "sun.net.spi.DefaultProxySelector")
final class Target_sun_net_spi_DefaultProxySelector {

    @Alias @InjectAccessors(DefaultProxySelectorSystemProxiesAccessor.class) //
    static boolean hasSystemProxies;

    @Alias
    static native boolean init();
}

final class DefaultProxySelectorSystemProxiesAccessor {
    static Boolean hasSystemProxies = null;

    static boolean get() {
        if (hasSystemProxies == null) {
            hasSystemProxies = ensureInitialized();
        }
        return hasSystemProxies;
    }

    static final SignedWord UNINITIALIZED = Word.signed(-2);
    static final SignedWord INITIALIZING = Word.signed(-1);

    static final CGlobalData<Pointer> initState = CGlobalDataFactory.createWord(UNINITIALIZED);

    /** Avoids calling init() more than once per process, which can leak resources with isolates. */
    static boolean ensureInitialized() {
        Boolean b = NetProperties.getBoolean("java.net.useSystemProxies");
        if (b != null && b) {
            // NOTE: System.loadLibrary("net") has already been called early on
            while (true) {
                SignedWord value = initState.get().readWord(0);
                if (value.greaterOrEqual(0)) {
                    return value.notEqual(0);
                }
                if (initState.get().logicCompareAndSwapWord(0, UNINITIALIZED, INITIALIZING, LocationIdentity.ANY_LOCATION)) {
                    boolean result = Target_sun_net_spi_DefaultProxySelector.init();
                    initState.get().writeWord(0, Word.signed(result ? 1 : 0));
                }
            }
        }
        return false;
    }
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class)
@AutomaticallyRegisteredFeature
class JavaNetFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        JavaNetSubstitutions.validateURLProtocolOptions();
        JavaNetSubstitutions.disableURLProtocols();
        if (JavaNetSubstitutions.runtimeURLProtocolsEnabled()) {
            /*
             * Runtime URL protocols use the JDK java.net.URL implementation instead of the
             * Target_java_net_URL substitution. Keep the native-image resource protocol available to
             * the JDK URL factory so embedded resource URIs returned by ModuleReader.find can be
             * converted back to URLs by the original JDK lookup path.
             */
            if (!URLProtocolsSupport.isDisabled(JavaNetSubstitutions.RESOURCE_PROTOCOL)) {
                boolean registered = JavaNetSubstitutions.addURLStreamHandler(JavaNetSubstitutions.RESOURCE_PROTOCOL);
                VMError.guarantee(registered, "The URL protocol %s is not available. It should be available as it is supported by default.", JavaNetSubstitutions.RESOURCE_PROTOCOL);
            }
            return;
        }

        EconomicSet<String> disabledURLProtocols = EconomicSet.create(SubstrateOptions.DisableURLProtocols.getValue().values());

        JavaNetSubstitutions.defaultProtocols.forEach(protocol -> {
            if (!disabledURLProtocols.contains(protocol)) {
                boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
                VMError.guarantee(registered, "The URL protocol %s is not available. It should be available as it is supported by default.", protocol);
            }
        });

        List<String> enabledURLProtocols = SubstrateOptions.EnableURLProtocols.getValue().values();
        if (enabledURLProtocols.contains(JavaNetSubstitutions.ALL_PROTOCOLS)) {
            for (String protocol : JavaNetSubstitutions.knownJDKProtocols) {
                enableURLProtocol(disabledURLProtocols, protocol);
            }
        }
        for (String protocol : enabledURLProtocols) {
            if (!JavaNetSubstitutions.isSpecialURLProtocolOption(protocol)) {
                enableURLProtocol(disabledURLProtocols, protocol);
            }
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (JavaNetSubstitutions.runtimeURLProtocolsEnabled()) {
            registerRuntimeClassLoadingURLProtocolHandlers(access, true);
        }
    }

    private static void registerRuntimeClassLoadingURLProtocolHandlers(BeforeAnalysisAccess access, boolean reportFailures) {
        EconomicSet<String> disabledURLProtocols = EconomicSet.create(SubstrateOptions.DisableURLProtocols.getValue().values());

        List<String> enabledURLProtocols = SubstrateOptions.EnableURLProtocols.getValue().values();
        if (enabledURLProtocols.contains(JavaNetSubstitutions.ALL_PROTOCOLS) || enabledURLProtocols.contains(JavaNetSubstitutions.RUNTIME_PROTOCOLS)) {
            for (String protocol : JavaNetSubstitutions.knownJDKProtocols) {
                registerRuntimeClassLoadingURLProtocolHandler(access, disabledURLProtocols, protocol, reportFailures);
            }
        }
        for (String protocol : enabledURLProtocols) {
            if (!JavaNetSubstitutions.isSpecialURLProtocolOption(protocol)) {
                registerRuntimeClassLoadingURLProtocolHandler(access, disabledURLProtocols, protocol, reportFailures);
            }
        }
    }

    private static void registerRuntimeClassLoadingURLProtocolHandler(BeforeAnalysisAccess access, EconomicSet<String> disabledURLProtocols, String protocol, boolean reportFailures) {
        if (disabledURLProtocols.contains(protocol)) {
            return;
        }

        if (JavaNetSubstitutions.defaultProtocols.contains(protocol)) {
            if (reportFailures) {
                LogUtils.warning("The URL protocol " + protocol + " is enabled by default. The option " + JavaNetSubstitutions.enableProtocolsOption + protocol + " is not needed.");
            }
            return;
        }

        InternalFeatureAccess internalAccess = (InternalFeatureAccess) access;
        ResolvedJavaType handlerType = internalAccess.findTypeByName(JavaNetSubstitutions.jdkURLProtocolHandlerClassName(protocol));
        ResolvedJavaType urlStreamHandlerType = internalAccess.getMetaAccess().lookupJavaType(URLStreamHandler.class);
        if (handlerType != null && urlStreamHandlerType.isAssignableFrom(handlerType)) {
            registerURLProtocolHandlerForRuntimeClassLoading(access, protocol, handlerType);
        } else if (reportFailures) {
            if (JavaNetSubstitutions.onDemandProtocols.contains(protocol)) {
                VMError.guarantee(false, "The URL protocol %s is not available. It should be available as it is a supported on-demand protocol.", protocol);
            } else {
                LogUtils.warning("Registering the " + protocol + " URL protocol failed. It will not be available at runtime.");
            }
        }
    }

    private static void registerURLProtocolHandlerForRuntimeClassLoading(BeforeAnalysisAccess access, String protocol, ResolvedJavaType handlerType) {
        Class<?> handlerClass = OriginalClassProvider.getJavaClass(handlerType);
        access.registerAsInHeap(handlerClass);
        JVMCIRuntimeReflection.register(handlerType);
        ResolvedJavaMethod nullaryConstructor = JVMCIReflectionUtil.getDeclaredConstructor(true, handlerType);
        if (nullaryConstructor != null) {
            JVMCIRuntimeReflection.register(nullaryConstructor);
        } else {
            if (JavaNetSubstitutions.knownJDKProtocols.contains(protocol)) {
                throw VMError.shouldNotReachHere("JDK URL protocol handler has no nullary constructor: " + handlerType.toClassName());
            }
            LogUtils.warning("Registering the " + protocol + " URL protocol failed. It will not be available at runtime.");
        }
    }

    private static void enableURLProtocol(EconomicSet<String> disabledURLProtocols, String protocol) {
        if (disabledURLProtocols.contains(protocol)) {
            return;
        }

        if (JavaNetSubstitutions.defaultProtocols.contains(protocol)) {
            LogUtils.warning("The URL protocol " + protocol + " is enabled by default. The option " + JavaNetSubstitutions.enableProtocolsOption + protocol + " is not needed.");
        } else if (JavaNetSubstitutions.onDemandProtocols.contains(protocol)) {
            boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
            VMError.guarantee(registered, "The URL protocol %s is not available. It should be available as it is a supported on-demand protocol.", protocol);
        } else {
            boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
            if (!registered) {
                LogUtils.warning("Registering the " + protocol + " URL protocol failed. It will not be available at runtime.");
            }
        }
    }

}

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
@AutomaticallyRegisteredImageSingleton
class URLProtocolsSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    static void put(String protocol, URLStreamHandler urlStreamHandler) {
        ImageSingletons.lookup(URLProtocolsSupport.class).imageHandlers.put(protocol, urlStreamHandler);
    }

    static URLStreamHandler get(String protocol) {
        return ImageSingletons.lookup(URLProtocolsSupport.class).imageHandlers.get(protocol);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void disable(String protocol) {
        ImageSingletons.lookup(URLProtocolsSupport.class).disabledProtocols.add(protocol);
    }

    static boolean isDisabled(String protocol) {
        return ImageSingletons.lookup(URLProtocolsSupport.class).disabledProtocols.contains(protocol);
    }

    private final HashMap<String, URLStreamHandler> imageHandlers = new HashMap<>();
    private final HashSet<String> disabledProtocols = new HashSet<>();
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSubstitutions {

    public static final String FILE_PROTOCOL = "file";
    public static final String RESOURCE_PROTOCOL = "resource";
    public static final String HTTP_PROTOCOL = "http";
    public static final String HTTPS_PROTOCOL = "https";
    static final String ALL_PROTOCOLS = "all";
    static final String RUNTIME_PROTOCOLS = "runtime";

    static final List<String> defaultProtocols = Arrays.asList(FILE_PROTOCOL, RESOURCE_PROTOCOL);
    static final List<String> onDemandProtocols = Arrays.asList(HTTP_PROTOCOL, HTTPS_PROTOCOL);
    static final List<String> knownJDKProtocols = Arrays.asList(HTTP_PROTOCOL, HTTPS_PROTOCOL, "ftp", "jar", "mailto", "jrt", "jmod");

    static final String enableProtocolsOption = SubstrateOptionsParser.commandArgument(SubstrateOptions.EnableURLProtocols, "");
    private static final String JDK_URL_PROTOCOL_HANDLER_PACKAGE = "sun.net.www.protocol.";
    private static final String JDK_URL_PROTOCOL_HANDLER_CLASS_NAME_SUFFIX = ".Handler";

    static boolean runtimeURLProtocolsEnabled() {
        return RuntimeClassLoading.isSupported() && SubstrateOptions.EnableURLProtocols.getValue().values().contains(RUNTIME_PROTOCOLS);
    }

    static boolean isSpecialURLProtocolOption(String protocol) {
        return ALL_PROTOCOLS.equals(protocol) || RUNTIME_PROTOCOLS.equals(protocol);
    }

    static void validateURLProtocolOptions() {
        if (!RuntimeClassLoading.isSupported() && SubstrateOptions.EnableURLProtocols.getValue().values().contains(RUNTIME_PROTOCOLS)) {
            throw UserError.invalidOptionValue(SubstrateOptions.EnableURLProtocols, RUNTIME_PROTOCOLS,
                            "The value '" + RUNTIME_PROTOCOLS + "' requires runtime class loading. Use " +
                                            SubstrateOptionsParser.commandArgument(RuntimeClassLoading.Options.RuntimeClassLoading, "+") + " or select concrete URL protocols instead.");
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void disableURLProtocols() {
        for (String protocol : SubstrateOptions.DisableURLProtocols.getValue().values()) {
            URLProtocolsSupport.disable(protocol);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static boolean addURLStreamHandler(String protocol) {
        if (RESOURCE_PROTOCOL.equals(protocol)) {
            final URLStreamHandler resourcesURLStreamHandler = createResourcesURLStreamHandler();
            URLProtocolsSupport.put(RESOURCE_PROTOCOL, resourcesURLStreamHandler);
            return true;
        }
        try {
            URLStreamHandler handler = (URLStreamHandler) ReflectionUtil.lookupMethod(URL.class, "getURLStreamHandler", String.class).invoke(null, protocol);
            if (handler != null) {
                URLProtocolsSupport.put(protocol, handler);
                return true;
            }
            return false;
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    static String jdkURLProtocolHandlerClassName(String protocol) {
        return JDK_URL_PROTOCOL_HANDLER_PACKAGE + protocol + JDK_URL_PROTOCOL_HANDLER_CLASS_NAME_SUFFIX;
    }

    static URLStreamHandler getURLStreamHandler(String protocol) throws MalformedURLException {
        URLStreamHandler result = URLProtocolsSupport.get(protocol);
        if (result == null) {
            if (onDemandProtocols.contains(protocol)) {
                unsupported("Accessing a URL protocol that was not enabled. The URL protocol " + protocol +
                                " is supported but not enabled by default. It must be enabled by adding the " + enableProtocolsOption + protocol +
                                " option to the native-image command.");
            } else {
                unsupported("Accessing a URL protocol that was not enabled. The URL protocol " + protocol +
                                " is not tested and might not work as expected. It can be enabled by adding the " + enableProtocolsOption + protocol +
                                " option to the native-image command.");
            }
        }
        return result;
    }

    static URLStreamHandler createResourcesURLStreamHandler() {
        return new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new ResourceURLConnection(url);
            }
        };
    }

    private static void unsupported(String message) throws MalformedURLException {
        /*
         * We throw a MalformedURLException and not our own unsupported feature error to be
         * consistent with the specification of URL.
         */
        throw new MalformedURLException(message);
    }

    static String supportedProtocols() {
        return "Supported URL protocols enabled by default: " + String.join(",", JavaNetSubstitutions.defaultProtocols) +
                        ". Supported URL protocols available on demand: " + String.join(",", JavaNetSubstitutions.onDemandProtocols) + ".";
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class BuildTimeURLProtocols implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !runtimeURLProtocolsEnabled();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class RuntimeURLProtocols implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return runtimeURLProtocolsEnabled();
        }
    }
}
