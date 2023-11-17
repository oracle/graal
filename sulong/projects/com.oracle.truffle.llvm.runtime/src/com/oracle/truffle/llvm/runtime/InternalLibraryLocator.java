/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.InternalResource.CPUArchitecture;
import com.oracle.truffle.api.InternalResource.OS;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.llvm.runtime.config.Configurations;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.spi.internal.LLVMResourceProvider;

/**
 * Locates internal libraries.
 */
public abstract class InternalLibraryLocator extends LibraryLocator implements LLVMCapability {

    private static final List<LLVMResourceProvider> LIBRARY_LOCATORS;
    static {
        List<LLVMResourceProvider> resourceLocators = new ArrayList<>();
        for (LLVMResourceProvider provider : Configurations.getService(LLVMResourceProvider.class)) {
            resourceLocators.add(provider);
        }
        LIBRARY_LOCATORS = resourceLocators;
    }

    public static InternalLibraryLocator create(String config, LLVMLanguage language, OS os, CPUArchitecture arch) {
        InternalLibraryLocator resourceLocator = null;
        for (LLVMResourceProvider provider : LIBRARY_LOCATORS) {
            if (provider.getConfiguration().equals(config)) {
                resourceLocator = new ResourceInternalLibraryLocator(provider, os, arch);
                break;
            }
        }

        String home = language.getLLVMLanguageHome();
        if (home != null) {
            Path libPath = Path.of(home, config, "lib");
            try {
                if (Files.exists(libPath)) {
                    // prefer language home, but still keep the resource locator as backup
                    resourceLocator = new HomeInternalLibraryLocator(config, resourceLocator);
                }
            } catch (SecurityException ex) {
                // ignore, treat "forbidden" the same as "not found"
            }
        }

        if (resourceLocator != null) {
            return resourceLocator;
        } else {
            throw new IllegalStateException(String.format("Could not find internal resources for configuration %s.", config));
        }
    }

    @Override
    protected abstract SourceBuilder locateLibrary(LLVMContext context, String lib, Object reason);

    private static final class HomeInternalLibraryLocator extends InternalLibraryLocator {

        private final String config;
        private final InternalLibraryLocator backup;

        private HomeInternalLibraryLocator(String config, InternalLibraryLocator backup) {
            this.config = config;
            this.backup = backup;
        }

        @Override
        protected SourceBuilder locateLibrary(LLVMContext context, String lib, Object reason) {
            String home = context.getLanguage().getLLVMLanguageHome();
            if (home != null) {
                Path libPath = Path.of(home, config, "lib", lib);
                TruffleFile file = context.getEnv().getInternalTruffleFile(libPath.toString());
                if (file.exists()) {
                    return Source.newBuilder("llvm", file).internal(true);
                }
            }
            if (backup != null) {
                return backup.locateLibrary(context, lib, reason);
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return "HomeInternalLibraryLocator [config=" + config + ", backup=" + backup + "]";
        }

    }

    private static final class ResourceInternalLibraryLocator extends InternalLibraryLocator {

        private final Class<?> resourceLocation;
        private final String basePath;

        private ResourceInternalLibraryLocator(LLVMResourceProvider provider, OS os, CPUArchitecture arch) {
            this.resourceLocation = provider.getClass();
            this.basePath = provider.getBasePath(os, arch);
        }

        @Override
        protected SourceBuilder locateLibrary(LLVMContext context, String lib, Object reason) {
            try (InputStream is = resourceLocation.getResourceAsStream(basePath + lib)) {
                if (is == null) {
                    return null;
                }
                return Source.newBuilder("llvm", ByteSequence.create(is.readAllBytes()), lib).internal(true);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return "ResourceInternalLibraryLocator [resourceLocation=" + resourceLocation + ", basePath=" + basePath + "]";
        }

    }
}
