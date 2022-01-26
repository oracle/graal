/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.function.Supplier;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.LazyFinalReference;

import sun.security.util.SecurityConstants;

/**
 * All classes that do not set a ProtectionDomain explicitly have a "default" ProtectionDomain. And
 * every ProtectionDomain has a CodeSource with a URL as its location.
 * 
 * If an application invokes {@link CodeSource#getLocation()}, it can reasonably expect a non-null
 * URL. We use the executable image's name as the location, because that is the best value we can
 * provide.
 * 
 * But computing the URL for the location pulls in a lot of JDK dependencies. For a simple
 * application like "Hello World", that would significantly increase the image size. So we only add
 * code to compute the URL if the application explicitly invokes {@link CodeSource#getLocation()}.
 * This is done using a reachability handler registered in ProtectionDomainFeature#beforeAnalysis().
 * 
 * Note that this still leads to observable differences in places where the location is used
 * implicitly, like {@link CodeSource#toString} and {@link CodeSource#implies}. We accept that
 * difference in behavior.
 */
public final class ProtectionDomainSupport {

    public static class Options {
        @Option(help = "Return the application path as the Class.getProtectionDomain().getCodeSource().getLocation() for all classes that have no explicit ProtectionDomain.", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> UseApplicationCodeSourceLocation = new HostedOptionKey<>(null);
    }

    private final LazyFinalReference<ProtectionDomain> allPermDomain = new LazyFinalReference<>(this::createAllPermDomain);

    /** Remains null as long as the reachability handler has not triggered. */
    Supplier<URL> executableURLSupplier;

    public static ProtectionDomain allPermDomain() {
        return ImageSingletons.lookup(ProtectionDomainSupport.class).allPermDomain.get();
    }

    private ProtectionDomain createAllPermDomain() {
        java.security.Permissions perms = new java.security.Permissions();
        perms.add(SecurityConstants.ALL_PERMISSION);
        /*
         * When enableCodeSource() was not called at image generation time, executableURLSupplier is
         * null. In that case, the static analysis does not see createExecutableURL() as reachable
         * because there is no code path to it.
         */
        URL executableURL = executableURLSupplier != null ? executableURLSupplier.get() : null;
        CodeSource cs = new CodeSource(executableURL, (Certificate[]) null);
        return new java.security.ProtectionDomain(cs, perms);
    }

    private static URL createExecutableURL() {
        /*
         * Try to use executable image's name as code source for the class. The file location can be
         * used by Java code to determine its location on disk, similar to argv[0].
         */
        String executableName = ProcessProperties.getExecutableName();
        if (executableName != null) {
            try {
                return new File(executableName).toURI().toURL();
            } catch (MalformedURLException ex) {
                /*
                 * This should not really happen; the file is canonicalized, absolute, so it should
                 * always have a file:// URL. But if it happens, it is OK to just ignore.
                 */
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void enableCodeSource() {
        ImageSingletons.lookup(ProtectionDomainSupport.class).executableURLSupplier = ProtectionDomainSupport::createExecutableURL;
    }
}
