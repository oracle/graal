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
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.net.NetProperties;

@TargetClass(java.net.URL.class)
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

    static final SignedWord UNINITIALIZED = WordFactory.signed(-2);
    static final SignedWord INITIALIZING = WordFactory.signed(-1);

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
                    initState.get().writeWord(0, WordFactory.signed(result ? 1 : 0));
                }
            }
        }
        return false;
    }
}

@AutomaticFeature
class JavaNetFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(URLProtocolsSupport.class, new URLProtocolsSupport());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Set<String> disabledURLProtocols = new HashSet<>(OptionUtils.flatten(",", SubstrateOptions.DisableURLProtocols.getValue()));

        JavaNetSubstitutions.defaultProtocols.forEach(protocol -> {
            if (!disabledURLProtocols.contains(protocol)) {
                boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
                VMError.guarantee(registered, "The URL protocol " + protocol + " is not available.");
            }
        });

        for (String protocol : OptionUtils.flatten(",", SubstrateOptions.EnableURLProtocols.getValue())) {
            if (disabledURLProtocols.contains(protocol)) {
                continue;
            }

            if (JavaNetSubstitutions.defaultProtocols.contains(protocol)) {
                printWarning("The URL protocol " + protocol + " is enabled by default. " +
                                "The option " + JavaNetSubstitutions.enableProtocolsOption + protocol + " is not needed.");
            } else if (JavaNetSubstitutions.onDemandProtocols.contains(protocol)) {
                boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
                VMError.guarantee(registered, "The URL protocol " + protocol + " is not available.");
            } else {
                printWarning("The URL protocol " + protocol + " is not tested and might not work as expected." +
                                System.lineSeparator() + JavaNetSubstitutions.supportedProtocols());
                boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
                if (!registered) {
                    printWarning("Registering the " + protocol + " URL protocol failed. " +
                                    "It will not be available at runtime." + System.lineSeparator());
                }
            }
        }
    }

    private static void printWarning(String warningMessage) {
        System.out.println(warningMessage);
    }
}

class URLProtocolsSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    static void put(String protocol, URLStreamHandler urlStreamHandler) {
        ImageSingletons.lookup(URLProtocolsSupport.class).imageHandlers.put(protocol, urlStreamHandler);
    }

    static URLStreamHandler get(String protocol) {
        return ImageSingletons.lookup(URLProtocolsSupport.class).imageHandlers.get(protocol);
    }

    private final HashMap<String, URLStreamHandler> imageHandlers = new HashMap<>();
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSubstitutions {

    public static final String FILE_PROTOCOL = "file";
    public static final String RESOURCE_PROTOCOL = "resource";
    public static final String HTTP_PROTOCOL = "http";
    public static final String HTTPS_PROTOCOL = "https";

    static final List<String> defaultProtocols = Arrays.asList(FILE_PROTOCOL, RESOURCE_PROTOCOL);
    static final List<String> onDemandProtocols = Arrays.asList(HTTP_PROTOCOL, HTTPS_PROTOCOL);

    static final String enableProtocolsOption = SubstrateOptionsParser.commandArgument(SubstrateOptions.EnableURLProtocols, "");

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

    static URLStreamHandler getURLStreamHandler(String protocol) throws MalformedURLException {
        URLStreamHandler result = URLProtocolsSupport.get(protocol);
        if (result == null) {
            if (onDemandProtocols.contains(protocol)) {
                unsupported("Accessing an URL protocol that was not enabled. The URL protocol " + protocol +
                                " is supported but not enabled by default. It must be enabled by adding the " + enableProtocolsOption + protocol +
                                " option to the native-image command.");
            } else {
                unsupported("Accessing an URL protocol that was not enabled. The URL protocol " + protocol +
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
}
