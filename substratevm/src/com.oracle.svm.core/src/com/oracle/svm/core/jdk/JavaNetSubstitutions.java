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

// Checkstyle: allow reflection

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@TargetClass(java.net.URLStreamHandler.class)
final class Target_java_net_URLStreamHandler {

    @Alias
    protected native void parseURL(URL u, String spec, int start, int limit);
}

@TargetClass(java.net.URL.class)
final class Target_java_net_URL {

    @Delete private static Hashtable<?, ?> handlers;

    @Alias
    private native void checkSpecifyHandler(SecurityManager sm);

    @Alias
    private native boolean isValidProtocol(String proto);

    @Alias transient Target_java_net_URLStreamHandler handler;

    @Alias private String protocol;

    @Alias private String host;

    @Alias private int port;

    @Alias private String file;

    @Alias private transient String query;

    @Alias private String authority;

    @Alias private transient String path;

    @Alias private transient String userInfo;

    @Alias private String ref;

    @Substitute
    private static URLStreamHandler getURLStreamHandler(String protocol) {
        return JavaNetSubstitutions.getURLStreamHandler(protocol);
    }

    @Substitute
    Target_java_net_URL(Target_java_net_URL context, String spec, Target_java_net_URLStreamHandler handlerParam)
                    throws MalformedURLException {
        String original = spec;
        int i;
        int limit;
        int c;
        int start = 0;
        String newProtocol = null;
        boolean aRef = false;
        boolean isRelative = false;
        // to avoid checkstyle whinging about parameter assignment later on,
        // even though that's exactly what the original JDK code does.
        Target_java_net_URLStreamHandler handlerTmp = handlerParam;

        // Check for permission to specify a handler
        if (handlerTmp != null) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                checkSpecifyHandler(sm);
            }
        }

        try {
            limit = spec.length();
            while ((limit > 0) && (spec.charAt(limit - 1) <= ' ')) {
                limit--;        // eliminate trailing whitespace
            }
            while ((start < limit) && (spec.charAt(start) <= ' ')) {
                start++;        // eliminate leading whitespace
            }

            if (spec.regionMatches(true, start, "url:", 0, 4)) {
                start += 4;
            }
            if (start < spec.length() && spec.charAt(start) == '#') {
                /*
                 * we're assuming this is a ref relative to the context URL. This means protocols
                 * cannot start w/ '#', but we must parse ref URL's like: "hello:there" w/ a ':' in
                 * them.
                 */
                aRef = true;
            }
            for (i = start; !aRef && (i < limit) &&
                            ((c = spec.charAt(i)) != '/'); i++) {
                if (c == ':') {
                    String s = JavaNetSubstitutions.toLowerCase(spec.substring(start, i));
                    if (isValidProtocol(s)) {
                        newProtocol = s;
                        start = i + 1;
                    }
                    break;
                }
            }

            // Only use our context if the protocols match.
            protocol = newProtocol;
            if ((context != null) && ((newProtocol == null) ||
                            newProtocol.equalsIgnoreCase(context.protocol))) {
                // inherit the protocol handler from the context
                // if not specified to the constructor
                if (handlerTmp == null) {
                    handlerTmp = context.handler;
                }

                // If the context is a hierarchical URL scheme and the spec
                // contains a matching scheme then maintain backwards
                // compatibility and treat it as if the spec didn't contain
                // the scheme; see 5.2.3 of RFC2396
                if (context.path != null && context.path.startsWith("/")) {
                    newProtocol = null;
                }

                if (newProtocol == null) {
                    protocol = context.protocol;
                    authority = context.authority;
                    userInfo = context.userInfo;
                    host = context.host;
                    port = context.port;
                    file = context.file;
                    path = context.path;
                    isRelative = true;
                }
            }

            if (protocol == null) {
                throw new MalformedURLException("no protocol: " + original);
            }

            // Get the protocol handler if not specified or the protocol
            // of the context could not be used
            if (handlerTmp == null &&
                            (handlerTmp = SubstrateUtil.cast(getURLStreamHandler(protocol), Target_java_net_URLStreamHandler.class)) == null) {
                if (JavaNetSubstitutions.onDemandProtocols.contains(protocol)) {
                    JavaNetSubstitutions.unsupported("Accessing an URL protocol that was not enabled. The URL protocol " + protocol +
                                    " is supported but not enabled by default. It must be enabled by adding the " + JavaNetSubstitutions.enableProtocolsOption + protocol +
                                    " option to the native-image command.");
                } else {
                    JavaNetSubstitutions.unsupported("Accessing an URL protocol that was not enabled. The URL protocol " + protocol +
                                    " is not tested and might not work as expected. It can be enabled by adding the " + JavaNetSubstitutions.enableProtocolsOption + protocol +
                                    " option to the native-image command.");
                }
            }

            this.handler = handlerTmp;

            i = spec.indexOf('#', start);
            if (i >= 0) {
                ref = spec.substring(i + 1, limit);
                limit = i;
            }

            /*
             * Handle special case inheritance of query and fragment implied by RFC2396 section
             * 5.2.2.
             */
            if (isRelative && start == limit) {
                query = context.query;
                if (ref == null) {
                    ref = context.ref;
                }
            }

            handlerTmp.parseURL(URL.class.cast(this), spec, start, limit);

        } catch (MalformedURLException e) {
            throw e;
        } catch (Exception e) {
            MalformedURLException exception = new MalformedURLException(e.getMessage());
            exception.initCause(e);
            throw exception;
        }
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
        JavaNetSubstitutions.defaultProtocols.forEach(protocol -> {
            boolean registered = JavaNetSubstitutions.addURLStreamHandler(protocol);
            VMError.guarantee(registered, "The URL protocol " + protocol + " is not available.");
        });

        for (String protocol : OptionUtils.flatten(",", SubstrateOptions.EnableURLProtocols.getValue())) {
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
        // Checkstyle: stop
        System.out.println(warningMessage);
        // Checkstyle: resume}
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

    static URLStreamHandler getURLStreamHandler(String protocol) {
        return URLProtocolsSupport.get(protocol);
    }

    static URLStreamHandler createResourcesURLStreamHandler() {
        return new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                return new URLConnection(url) {
                    @Override
                    public void connect() throws IOException {
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        Resources.ResourcesSupport support = ImageSingletons.lookup(Resources.ResourcesSupport.class);
                        // remove "protcol:" from url to get the resource name
                        String resName = url.toString().substring(1 + JavaNetSubstitutions.RESOURCE_PROTOCOL.length());
                        final List<byte[]> bytes = support.resources.get(resName);
                        if (bytes == null || bytes.size() < 1) {
                            return null;
                        } else {
                            return new ByteArrayInputStream(bytes.get(0));
                        }
                    }
                };
            }
        };
    }

    static void unsupported(String message) throws MalformedURLException {
        throw new MalformedURLException(message);
    }

    static String supportedProtocols() {
        return "Supported URL protocols enabled by default: " + String.join(",", JavaNetSubstitutions.defaultProtocols) +
                        ". Supported URL protocols available on demand: " + String.join(",", JavaNetSubstitutions.onDemandProtocols) + ".";
    }

    static String toLowerCase(String protocol) {
        if (protocol.equals("jrt") || protocol.equals("file") || protocol.equals("jar")) {
            return protocol;
        } else {
            return protocol.toLowerCase(Locale.ROOT);
        }
    }
}
