/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.VMError;

public final class ProjectHeaderFile {

    @AutomaticFeature
    public static class RegisterSVMTestingResolverFeature extends RegisterFallbackResolverFeature {

        @Override
        public boolean isInConfiguration(IsInConfigurationAccess access) {
            return access.findClassByName("com.oracle.svm.tutorial.CInterfaceTutorial") != null;
        }

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            /**
             * Search for headers in a directory, relative to the current working directory, that
             * contains the Substrate VM projects. Using the "../substratevm*" relative path
             * accounts for running SVM from sibling suites.
             */
            HeaderResolversRegistry.registerAdditionalResolver(new FallbackHeaderResolver("../../graal/substratevm/src"));
        }
    }

    @AutomaticFeature
    public static class HeaderResolverRegistrationFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(HeaderResolversRegistry.class, new HeaderResolversRegistry());
        }
    }

    /**
     * Base class for fall back resolvers registration. Extending this class will ensure that the
     * {@link ProjectHeaderFile} will be added as a dependency.
     */
    public abstract static class RegisterFallbackResolverFeature implements Feature {

        @Override
        public List<Class<? extends Feature>> getRequiredFeatures() {
            return Collections.singletonList(HeaderResolverRegistrationFeature.class);
        }
    }

    /**
     * Resolves the path to a C header file that is located in a Substrate VM project.
     */
    public static String resolve(String projectName, String headerFile) {
        HeaderResolversRegistry resolvers = ImageSingletons.lookup(HeaderResolversRegistry.class);
        return resolvers.resolve(projectName, headerFile);
    }

    /**
     * A registry for all the header resolvers. The search order is important, we want first to
     * search the location(s) specified by CLibraryPath, then registered fall back locations if any.
     */
    public static class HeaderResolversRegistry {

        /** Register additional resolvers. */
        public static void registerAdditionalResolver(HeaderResolver resolver) {
            assert ImageSingletons.contains(HeaderResolversRegistry.class);
            HeaderResolversRegistry registry = ImageSingletons.lookup(HeaderResolversRegistry.class);
            registry.register(resolver);
        }

        private MainHeaderResolver mainResolver;
        private List<HeaderResolver> fallbackResolvers;

        public HeaderResolversRegistry() {
            mainResolver = new MainHeaderResolver();
            fallbackResolvers = new ArrayList<>();
        }

        private void register(HeaderResolver resolver) {
            fallbackResolvers.add(resolver);
        }

        public String resolve(String projectName, String headerFile) {

            /* First search using the main resolver. */
            HeaderSearchResult mainResult = mainResolver.resolveHeader(projectName, headerFile);
            if (mainResult.header.isPresent()) {
                return mainResult.header.get();
            }

            /* Then search using the fallback resolvers, if any. */
            List<String> fallbackLocations = new ArrayList<>();
            for (HeaderResolver resolver : fallbackResolvers) {
                HeaderSearchResult result = resolver.resolveHeader(projectName, headerFile);
                fallbackLocations.addAll(result.locations);
                if (result.header.isPresent()) {
                    return result.header.get();
                }
            }

            /* If the header was not found at any of the specified locations an error is thrown. */
            throw VMError.shouldNotReachHere("Header file " + headerFile +
                            " not found at main search location(s): \n" + String.join("\n", mainResult.locations) +
                            (fallbackLocations.size() > 0 ? "\n or any of the fallback locations: \n" + String.join("\n", fallbackLocations) : "") +
                            "\n Use option -H:CLibraryPath to specify header file search locations.");
        }

    }

    /** Used for resolving header files. */
    public interface HeaderResolver {
        /** Tries to resolve a header given the project name and the header file name. */
        HeaderSearchResult resolveHeader(String projectName, String headerFile);
    }

    /** Contains the search result and the locations searched. */
    public static class HeaderSearchResult {

        /** The header file, if found. */
        Optional<String> header;

        /** The locations where the this resolver searched for headers. */
        protected List<String> locations;

        public HeaderSearchResult(Optional<String> headerFile, List<String> locations) {
            this.header = headerFile;
            this.locations = locations;
        }

        public HeaderSearchResult(Optional<String> headerFile, String... locations) {
            this.header = headerFile;
            this.locations = Arrays.asList(locations);
        }

    }

    /** Header resolver based on CLibraryPath. */
    static class MainHeaderResolver implements HeaderResolver {

        @Override
        public HeaderSearchResult resolveHeader(String projectName, String headerFile) {
            List<String> locations = new ArrayList<>();
            for (String clibPathComponent : OptionUtils.flatten(",", SubstrateOptions.CLibraryPath.getValue())) {
                Path clibPathHeaderFile = Paths.get(clibPathComponent).resolve(headerFile).normalize().toAbsolutePath();
                locations.add(clibPathHeaderFile.toString());
                if (Files.exists(clibPathHeaderFile)) {
                    return new HeaderSearchResult(Optional.of("\"" + clibPathHeaderFile + "\""), locations);
                }
            }

            return new HeaderSearchResult(Optional.empty(), locations);
        }
    }

    /** This kind of resolving is for SubstrateVM internal use (to run our regression tests). */
    public static class FallbackHeaderResolver implements HeaderResolver {

        final String projectsDir;

        public FallbackHeaderResolver(String projectsDir) {
            this.projectsDir = projectsDir;
        }

        @Override
        public HeaderSearchResult resolveHeader(String projectName, String headerFile) {
            Path fallbackHeaderFile = Paths.get(projectsDir).resolve(projectName).resolve(headerFile).normalize().toAbsolutePath();
            if (Files.exists(fallbackHeaderFile)) {
                return new HeaderSearchResult(Optional.of("\"" + fallbackHeaderFile + "\""), fallbackHeaderFile.toString());
            }

            return new HeaderSearchResult(Optional.empty(), fallbackHeaderFile.toString());
        }

    }

}
