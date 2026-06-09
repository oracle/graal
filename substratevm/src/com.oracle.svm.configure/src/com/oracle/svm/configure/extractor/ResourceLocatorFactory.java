/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.extractor;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oracle.svm.configure.ConfigurationUsageException;

/**
 * Factory for {@link ResourceLocator resource locators}.
 */
class ResourceLocatorFactory {
    /**
     * Defines the factories of the supported {@link ResourceLocator resource locators}. Append to
     * this list to add support for new file formats.
     */
    private final List<Function<MemorySegment, ResourceLocator>> supportedResourceLocatorFactories = List.of(
                    MachOResourceLocator::new,
                    ElfResourceLocator::new,
                    PECoffResourceLocator::new);

    /**
     * Creates and returns a {@link ResourceLocator} for the given memory segment by detecting the
     * file format through magic byte matching.
     *
     * @param segment the memory segment holding the image
     * @return a {@link ResourceLocator} instance suitable for the detected file format
     * @throws ConfigurationUsageException if no matching file format is found
     */
    ResourceLocator create(MemorySegment segment) throws ConfigurationUsageException {
        List<ResourceLocator> locators = new ArrayList<>();
        for (var locatorFactory : supportedResourceLocatorFactories) {
            ResourceLocator locator = locatorFactory.apply(segment);
            if (locator.matchesMagic()) {
                return locator;
            }
            locators.add(locator);
        }

        throw new ConfigurationUsageException("Unrecognized file format. Supported formats: %s."
                        .formatted(supportedFileFormats(locators)));
    }

    private static String supportedFileFormats(List<ResourceLocator> resourceLocators) {
        return resourceLocators.stream()
                        .map(ResourceLocator::getSupportedFileFormat)
                        .collect(Collectors.joining(", "));
    }
}
