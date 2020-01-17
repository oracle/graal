/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.analysis.Inflation;

/**
 * Support for {@link ServiceLoader} on Substrate VM.
 *
 * Services are registered in the folder {@link #LOCATION_PREFIX "META-INF/services/"} using files
 * whose name is the fully qualified service interface name. We do not know which services are going
 * to be used by a native image: The parameter of {@link ServiceLoader#load} is often but not always
 * a compile-time constant that we can track. But we also cannot put all registered services into
 * the native image.
 *
 * We therefore use the following heuristic: we add all service loader files and service
 * implementation classes when the service interfaces that are seen as reachable by the static
 * analysis.
 *
 * Each used service implementation class is added for reflection (using
 * {@link org.graalvm.nativeimage.hosted.RuntimeReflection#register(Class[])}) and for reflective
 * instantiation (using {@link RuntimeReflection#registerForReflectiveInstantiation(Class[])}).
 *
 * For each service interface, a single service loader file is added as a resource to the image. The
 * single file combines all the individual files that can come from different .jar files.
 */
@AutomaticFeature
public class ServiceLoaderFeature implements Feature {

    public static class Options {
        @Option(help = "Automatically register services for run-time lookup using ServiceLoader", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> UseServiceLoaderFeature = new HostedOptionKey<>(true);

        @Option(help = "When enabled, each service loader resource and class will be printed out to standard output", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> TraceServiceLoaderFeature = new HostedOptionKey<>(false);
    }

    /** Copy of private field {@code ServiceLoader.PREFIX}. */
    private static final String LOCATION_PREFIX = "META-INF/services/";

    /**
     * Set of types that are already processed (if they are a service interface) or are already
     * known to be not a service interface.
     */
    private final Map<AnalysisType, Boolean> processedTypes = new ConcurrentHashMap<>();

    private final boolean trace = Options.TraceServiceLoaderFeature.getValue();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.UseServiceLoaderFeature.getValue();
    }

    @SuppressWarnings("try")
    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        boolean workDone = false;
        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (handleType(type, access)) {
                workDone = true;
            }
        }
        if (workDone) {
            DebugContext debugContext = access.getDebugContext();
            try (DebugContext.Scope s = debugContext.scope("registerResource")) {
                debugContext.log("Resources have been added by ServiceLoaderFeature. Automatic registration can be disabled with " +
                                SubstrateOptionsParser.commandArgument(Options.UseServiceLoaderFeature, "-"));
            }
        }
    }

    @SuppressWarnings("try")
    private boolean handleType(AnalysisType type, DuringAnalysisAccessImpl access) {
        if (!type.isInTypeCheck() || type.isArray()) {
            /*
             * Type is not seen as used yet by the static analysis. Note that a constant class
             * literal is enough to register a type as "in type check". Arrays are also never
             * services.
             */
            return false;
        }
        if (processedTypes.putIfAbsent(type, Boolean.TRUE) != null) {
            /* Type already processed. */
            return false;
        }

        String serviceClassName = type.toClassName();
        String serviceResourceLocation = LOCATION_PREFIX + serviceClassName;

        /*
         * We are using a TreeSet to remove duplicate entries and to have a stable order for the
         * resource that is put into the image.
         */
        Set<String> implementationClassNames = new TreeSet<>();

        /*
         * We do not know if the type is actually a service interface. The easiest way to find that
         * out is to look up the resources that ServiceLoader would access. If no resources exist,
         * then it is not a service interface.
         */
        Enumeration<URL> resourceURLs;
        try {
            resourceURLs = access.getImageClassLoader().getClassLoader().getResources(serviceResourceLocation);
        } catch (IOException ex) {
            throw UserError.abort(ex, "Error loading service implementation resources for service `" + serviceClassName + "`");
        }
        while (resourceURLs.hasMoreElements()) {
            URL resourceURL = resourceURLs.nextElement();
            try {
                implementationClassNames.addAll(parseServiceResource(resourceURL));
            } catch (IOException ex) {
                throw UserError.abort(ex, "Error loading service implementations for service `" + serviceClassName + "` from URL `" + resourceURL + "`");
            }
        }

        if (implementationClassNames.size() == 0) {
            /*
             * No service implementations registered in the resources. Since we check all classes
             * that the static analysis finds, this case is very likely.
             */
            return false;
        }

        if (trace) {
            System.out.println("ServiceLoaderFeature: processing service class " + serviceClassName);
        }

        StringBuilder newResourceValue = new StringBuilder(1024);
        for (String implementationClassName : implementationClassNames) {
            if (implementationClassName.startsWith("org.graalvm.compiler") && implementationClassName.contains("hotspot")) {
                /*
                 * Workaround for compiler services. The classpath always contains the
                 * HotSpot-specific classes of Graal. This is caused by the current distribution
                 * .jar files and class loader hierarchies of Graal. We filter out HotSpot-specific
                 * service implementations using the naming convention: they have "hotspot" in the
                 * package name.
                 */
                if (trace) {
                    System.out.println("  IGNORING HotSpot-specific implementation class: " + implementationClassName);
                }
                continue;
            }

            if (trace) {
                System.out.println("  adding implementation class: " + implementationClassName);
            }

            Class<?> implementationClass = access.findClassByName(implementationClassName);
            if (implementationClass == null) {
                throw UserError.abort("Could not find registered service implementation class `" + implementationClassName + "` for service `" + serviceClassName + "`");
            }
            try {
                access.getMetaAccess().lookupJavaType(implementationClass);
            } catch (UnsupportedFeatureException ex) {
                if (trace) {
                    System.out.println("  cannot resolve: " + ex.getMessage());
                }
                continue;
            }

            if (((Inflation) access.getBigBang()).getAnnotationSubstitutionProcessor().isDeleted(implementationClass)) {
                /* Disallow services with implementation classes that are marked as @Deleted */
                continue;
            }

            /* Allow Class.forName at run time for the service implementation. */
            RuntimeReflection.register(implementationClass);
            /* Allow reflective instantiation at run time for the service implementation. */
            RuntimeReflection.registerForReflectiveInstantiation(implementationClass);

            /* Add line to the new resource that will be available at run time. */
            newResourceValue.append(implementationClass.getName());
            newResourceValue.append('\n');
        }

        DebugContext debugContext = access.getDebugContext();
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            debugContext.log("ServiceLoaderFeature: registerResource: " + serviceResourceLocation);
        }
        Resources.registerResource(serviceResourceLocation, new ByteArrayInputStream(newResourceValue.toString().getBytes(StandardCharsets.UTF_8)));

        /* Ensure that the static analysis runs again for the new implementation classes. */
        access.requireAnalysisIteration();
        return true;
    }

    /**
     * Parse a service configuration file. This code is inspired by the private implementation
     * methods of {@link ServiceLoader}.
     */
    private static Collection<String> parseServiceResource(URL resourceURL) throws IOException {
        Collection<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), "utf-8"))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }
                line = line.trim();
                if (line.length() != 0) {
                    /*
                     * We do not need to do further sanity checks on the class name. If the name is
                     * illegal, then we will not be able to load the class and report an error.
                     */
                    result.add(line);
                }
            }
        }
        return result;
    }
}
