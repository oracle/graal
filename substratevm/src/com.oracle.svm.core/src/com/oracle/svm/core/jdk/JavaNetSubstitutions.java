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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.InternalFeature.InternalFeatureAccess;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeReflection;

import jdk.vm.ci.meta.ResolvedJavaType;

import sun.net.NetProperties;

@TargetClass(java.net.URL.class)
final class Target_java_net_URL {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    @SuppressWarnings({"final", "unused"}) //
    // Checkstyle: stop
    private static Hashtable<String, URLStreamHandler> handlers = new Hashtable<>();
    // Checkstyle: resume

    @Alias //
    private static volatile URLStreamHandlerFactory factory;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static URLStreamHandlerFactory defaultFactory = new DefaultFactory();

    @Alias //
    private static Object streamHandlerLock;

    @Alias //
    private static native boolean isOverrideable(String protocol);

    @Alias //
    private static native URLStreamHandler lookupViaProviders(String protocol);

    @Alias //
    private static native URLStreamHandler lookupViaProperty(String protocol);

    /**
     * Same as in the JDK except: disabled protocols are rejected before any handler lookup, jrt
     * handlers use Native Image JRT support when enabled, and resource is not overrideable.
     */
    @Substitute
    @TargetElement(name = "getURLStreamHandler")
    static URLStreamHandler getURLStreamHandler(String protocol) {
        if (JavaNetSubstitutions.isDisabledURLProtocol(protocol)) {
            return null;
        }

        if ("jrt".equalsIgnoreCase(protocol) && JRTSupport.Options.AllowJRTFileSystem.getValue()) {
            URLStreamHandler handler = JavaNetSubstitutions.newJRTURLStreamHandler();
            synchronized (streamHandlerLock) {
                handlers.put("jrt", handler);
            }
            return handler;
        }

        URLStreamHandler handler = handlers.get(protocol);
        if (handler != null) {
            return handler;
        }

        boolean checkedWithFactory = false;
        URLStreamHandlerFactory currentFactory;
        boolean overrideable = isOverrideable(protocol) && !"resource".equalsIgnoreCase(protocol);
        if (overrideable && Target_jdk_internal_misc_VM.isBooted()) {
            currentFactory = factory;
            if (currentFactory != null) {
                handler = currentFactory.createURLStreamHandler(protocol);
                checkedWithFactory = true;
            }
            if (handler == null && !"jar".equalsIgnoreCase(protocol)) {
                handler = lookupViaProviders(protocol);
            }
            if (handler == null) {
                handler = lookupViaProperty(protocol);
            }
        }

        if (handler == null) {
            handler = defaultFactory.createURLStreamHandler(protocol);
        }

        synchronized (streamHandlerLock) {
            URLStreamHandler handler2 = handlers.get(protocol);
            if (handler2 != null) {
                return handler2;
            }

            if (overrideable && !checkedWithFactory && (currentFactory = factory) != null) {
                handler2 = currentFactory.createURLStreamHandler(protocol);
            }

            if (handler2 != null) {
                handler = handler2;
            }

            if (handler != null) {
                handlers.put(protocol, handler);
            }
        }

        return handler;
    }

    /**
     * Same as in the JDK except: it handles the resource protocol, it does not pull in the JAR
     * provider by default, and it handles error messages related to metadata better.
     */
    @BasedOnJDKClass(className = "java.net.URL$DefaultFactory")
    private static final class DefaultFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if (JavaNetSubstitutions.isDisabledURLProtocol(protocol)) {
                return null;
            }
            // Avoid using reflection during bootstrap.
            switch (protocol) {
                case "file":
                    return new sun.net.www.protocol.file.Handler();
                case "resource":
                    return new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL url) {
                            return new ResourceURLConnection(url);
                        }
                    };
                case "jrt":
                    if (JRTSupport.Options.AllowJRTFileSystem.getValue()) {
                        return JavaNetSubstitutions.newJRTURLStreamHandler();
                    }
                    break;
            }
            String name = JavaNetSubstitutions.handlerClassName(protocol);
            try {
                Object handler = Class.forName(name, false, ClassLoader.getSystemClassLoader()).getDeclaredConstructor().newInstance();
                return (URLStreamHandler) handler;
            } catch (ClassNotFoundException e) {
                if (JavaNetSubstitutions.KNOWN_JDK_PROTOCOLS.contains(protocol)) {
                    JavaNetSubstitutions.unsupported(protocol, name);
                }
            } catch (Exception e) {
                // For compatibility, all Exceptions are ignored.
            }
            return null;
        }
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
            // NOTE: System.loadLibrary("net") has already been called early on.
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

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
@AutomaticallyRegisteredFeature
class JavaNetFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            ImageSingletons.add(URLProtocolsSupport.class, new URLProtocolsSupport(SubstrateOptions.DisableURLProtocols.getValue().values()));
        }

        LinkedHashSet<String> protocols = new LinkedHashSet<>();
        for (String protocol : SubstrateOptions.EnableURLProtocols.getValue().values()) {
            if (JavaNetSubstitutions.isAllURLProtocolsOption(protocol) || JavaNetSubstitutions.isRuntimeURLProtocolsOption(protocol)) {
                protocols.addAll(JavaNetSubstitutions.KNOWN_JDK_PROTOCOLS);
            } else {
                protocols.add(protocol);
            }
        }
        for (String protocol : protocols) {
            JavaNetSubstitutions.registerURLProtocol(access, protocol);
        }

        RuntimeResourceSupport.singleton().addResources(AccessCondition.unconditional(), "META-INF/services/java.net.spi.URLStreamHandlerProvider", "JavaNetFeature for URL");
    }
}

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class URLProtocolsSupport {
    private final Set<String> disabledProtocols;

    URLProtocolsSupport(Collection<String> disabledProtocols) {
        this.disabledProtocols = Set.copyOf(disabledProtocols);
    }

    boolean isDisabled(String protocol) {
        return disabledProtocols.contains(protocol);
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSubstitutions {
    static final String ALL_PROTOCOLS = "all";
    static final String RUNTIME_PROTOCOLS = "runtime";
    private static final String PROTOCOL_QUALIFIER = "sun.net.www.protocol.";
    static final Set<String> KNOWN_JDK_PROTOCOLS = Set.of(
                    "file", "ftp", "http", "https", "jar", "jmod", "jrt", "mailto");

    static boolean isAllURLProtocolsOption(String protocol) {
        return ALL_PROTOCOLS.equals(protocol);
    }

    static boolean isRuntimeURLProtocolsOption(String protocol) {
        return RUNTIME_PROTOCOLS.equals(protocol);
    }

    static boolean isDisabledURLProtocol(String protocol) {
        return ImageSingletons.contains(URLProtocolsSupport.class) && ImageSingletons.lookup(URLProtocolsSupport.class).isDisabled(protocol);
    }

    static String handlerClassName(String protocol) {
        return PROTOCOL_QUALIFIER + protocol + ".Handler";
    }

    static void registerURLProtocol(DuringSetupAccess access, String protocol) {
        if (isDisabledURLProtocol(protocol)) {
            LogUtils.warning("The URL protocol " + protocol + " was both enabled and disabled. The disable option takes precedence.");
            return;
        }
        String handlerClassName = handlerClassName(protocol);
        try {
            ResolvedJavaType handlerClass = ((InternalFeatureAccess) access).findTypeByName(handlerClassName);
            if (handlerClass == null) {
                throw new ClassNotFoundException(handlerClassName);
            }
            JVMCIRuntimeReflection.register(handlerClass);
            JVMCIRuntimeReflection.register(JVMCIReflectionUtil.getDeclaredConstructor(handlerClass));
        } catch (ClassNotFoundException | LinkageError e) {
            LogUtils.warning("Registering the " + protocol + " URL protocol failed. This protocol will not be available at runtime. The protocol was set with " +
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.EnableURLProtocols, protocol) +
                            "Cause of the failure: " + e.getMessage());
        }
    }

    static void unsupported(String protocol, String handlerClassName) {
        throw sneakyThrow(new MalformedURLException("Accessing a URL protocol that was not enabled. The URL protocol " + protocol +
                        " is supported but not enabled by default. It must be enabled by adding the " + handlerClassName + " to reachability metadata."));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    static URLStreamHandler newJRTURLStreamHandler() {
        return new JRTURLStreamHandler();
    }

    private static final class JRTURLStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return new JRTURLConnection(url);
        }
    }
}
