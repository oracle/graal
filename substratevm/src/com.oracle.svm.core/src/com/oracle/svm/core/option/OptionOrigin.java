/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.option;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

import com.oracle.svm.core.util.VMError;

public abstract class OptionOrigin {

    public static final OptionOrigin commandLineAPIOptionOriginSingleton = new CommandLineOptionOrigin(true);
    public static final OptionOrigin commandLineNonAPIOptionOriginSingleton = new CommandLineOptionOrigin(false);
    public static final OptionOrigin driverStableOriginSingleton = new DriverOptionOrigin();
    public static final String argFilePrefix = "argfile:";

    public static final String originUser = "user";

    public static final String originDriver = "driver";

    public static final String isAPISuffix = "+api";

    protected final boolean isStable;

    public OptionOrigin(boolean isStable) {
        this.isStable = isStable;
    }

    public URI container() {
        return null;
    }

    public Path location() {
        return null;
    }

    public boolean commandLineLike() {
        return false;
    }

    public boolean isStable() {
        return isStable;
    }

    public boolean isInternal() {
        return false;
    }

    /**
     * Return the option values contained in the redirection file specified by the given path.
     * Depending on the specific kind of OptionOrigin the method to retrieve those values can
     * require different implementations.
     */
    public List<String> getRedirectionValues(@SuppressWarnings("unused") Path valuesFile) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    public static OptionOrigin from(String origin) {
        return from(origin, true);
    }

    public static OptionOrigin from(String originArg, boolean strict) {
        String origin = originArg;

        boolean isStable = false;
        if (origin != null && isAPI(origin)) {
            isStable = true;
            origin = origin.substring(0, origin.length() - isAPISuffix.length());
        }

        if (origin == null || originUser.equals(origin) || origin.startsWith(argFilePrefix)) {
            return isStable ? commandLineAPIOptionOriginSingleton : commandLineNonAPIOptionOriginSingleton;
        }

        if (originDriver.equals(origin)) {
            return driverStableOriginSingleton;
        }

        URI originURI = originURI(origin);
        if (originURI == null) {
            var macroOption = MacroOptionOrigin.from(isStable, origin);
            if (macroOption != null) {
                return macroOption;
            }
            if (strict) {
                throw VMError.shouldNotReachHere("Unsupported OptionOrigin: " + origin);
            }
            return null;
        }
        switch (originURI.getScheme()) {
            case "jar":
                return new JarOptionOrigin(isStable, originURI);
            case "file":
                Path originPath = Path.of(originURI);
                if (!Files.isReadable(originPath) && strict) {
                    VMError.shouldNotReachHere("Directory origin with path that cannot be read: " + originPath);
                }
                return new DirectoryOptionOrigin(isStable, originPath);
            default:
                if (strict) {
                    throw VMError.shouldNotReachHere("OptionOrigin of unsupported scheme: " + originURI);
                }
                return null;
        }
    }

    public static boolean isAPI(String originArg) {
        return originArg.endsWith(isAPISuffix);
    }

    protected static URI originURI(String origin) {
        try {
            return new URI(origin);
        } catch (URISyntaxException x) {
            return null;
        }
    }

    static List<String> getRedirectionValuesFromPath(Path normalizedRedirPath) throws IOException {
        if (Files.isReadable(normalizedRedirPath)) {
            return Files.readAllLines(normalizedRedirPath);
        }
        throw new FileNotFoundException("Unable to read file from " + normalizedRedirPath.toUri());
    }
}

final class CommandLineOptionOrigin extends OptionOrigin {
    CommandLineOptionOrigin(boolean isStable) {
        super(isStable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(this), isStable);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CommandLineOptionOrigin cloo && this.isStable == cloo.isStable;
    }

    @Override
    public boolean commandLineLike() {
        return true;
    }

    @Override
    public String toString() {
        return "command line";
    }
}

final class DriverOptionOrigin extends OptionOrigin {
    DriverOptionOrigin() {
        super(false);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(this), isStable);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DriverOptionOrigin doo && this.isStable == doo.isStable;
    }

    @Override
    public boolean commandLineLike() {
        return true;
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String toString() {
        return "internal";
    }
}

final class MacroOptionOrigin extends OptionOrigin {

    public final OptionUtils.MacroOptionKind kind;
    public final String name;
    public final Path optionDirectory;

    private MacroOptionOrigin(boolean isStable, OptionUtils.MacroOptionKind kind, String name, URI optionDirectory) {
        super(isStable);
        this.kind = kind;
        this.name = name;
        VMError.guarantee(optionDirectory != null, "Invalid optionDirectory origin");
        this.optionDirectory = Path.of(optionDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MacroOptionOrigin) {
            var that = (MacroOptionOrigin) obj;
            return Objects.equals(this.kind, that.kind) &&
                            Objects.equals(this.name, that.name);
        }
        return false;
    }

    public static MacroOptionOrigin from(boolean isStable, String rawOrigin) {
        for (OptionUtils.MacroOptionKind kind : OptionUtils.MacroOptionKind.values()) {
            String prefix = kind.getDescriptionPrefix(true);
            if (rawOrigin.startsWith(prefix)) {
                int optionDirectorySep = rawOrigin.indexOf('@');
                VMError.guarantee(optionDirectorySep > 0, "Missing macro-option optionDirectory origin");
                String optionDirectory = rawOrigin.substring(optionDirectorySep + 1);
                int argumentOriginSep = optionDirectory.indexOf('@');
                if (argumentOriginSep > 0) {
                    /* Strip optional trailing argumentOrigin */
                    optionDirectory = optionDirectory.substring(0, argumentOriginSep);
                }
                return new MacroOptionOrigin(isStable, kind, rawOrigin.substring(prefix.length()), originURI(optionDirectory));
            }
        }
        return null;
    }

    @Override
    public boolean commandLineLike() {
        return OptionUtils.MacroOptionKind.Macro.equals(kind);
    }

    @Override
    public String toString() {
        return kind + " option '" + name + "'";
    }

    @Override
    public List<String> getRedirectionValues(Path valuesFile) throws IOException {
        var normalizedRedirPath = optionDirectory.resolve(valuesFile).normalize();
        return getRedirectionValuesFromPath(normalizedRedirPath);
    }
}

abstract class URIOptionOrigin extends OptionOrigin {

    URIOptionOrigin(boolean isStable) {
        super(isStable);
    }

    protected URI container;

    @Override
    public URI container() {
        return container;
    }

    protected Path location;

    @Override
    public Path location() {
        return location;
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, location);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof URIOptionOrigin) {
            var that = (URIOptionOrigin) obj;
            return Objects.equals(this.container, that.container) &&
                            Objects.equals(this.location, that.location);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("'%s' in '%s'", location(), container());
    }
}

final class JarOptionOrigin extends URIOptionOrigin {
    protected JarOptionOrigin(boolean isStable, URI rawOrigin) {
        super(isStable);
        var specific = rawOrigin.getSchemeSpecificPart();
        int sep = specific.lastIndexOf('!');
        VMError.guarantee(sep > 0, "Invalid jar origin");
        var origin = specific.substring(0, sep);
        container = URIOptionOrigin.originURI(origin);
        location = Path.of(specific.substring(sep + 2));
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "location()).getParent() is never null")
    @SuppressWarnings("try")
    @Override
    public List<String> getRedirectionValues(Path valuesFile) throws IOException {
        URI jarFileURI = URI.create("jar:" + container());
        FileSystem probeJarFS;
        try {
            probeJarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
        } catch (UnsupportedOperationException e) {
            probeJarFS = null;
        }
        if (probeJarFS == null) {
            throw new IOException("Unable to create jar file system for " + jarFileURI);
        }
        try (FileSystem fs = probeJarFS) {
            var normalizedRedirPath = location().getParent().resolve(valuesFile).normalize();
            return getRedirectionValuesFromPath(normalizedRedirPath);
        }
    }
}

final class DirectoryOptionOrigin extends URIOptionOrigin {
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "originPath.getRoot() is never null")
    protected DirectoryOptionOrigin(boolean isStable, Path originPath) {
        super(isStable);
        int pathPos = 0;
        int metaInfPos = -1;
        for (Path entry : originPath) {
            if ("META-INF".equals(entry.toString())) {
                metaInfPos = pathPos;
                break;
            }
            ++pathPos;
        }
        VMError.guarantee(metaInfPos > 0, "Invalid directory origin");
        container = originPath.getRoot().resolve(originPath.subpath(0, metaInfPos)).toUri();
        location = originPath.subpath(metaInfPos, originPath.getNameCount());
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "location()).getParent() is never null")
    @Override
    public List<String> getRedirectionValues(Path valuesFile) throws IOException {
        var normalizedRedirPath = Path.of(container()).resolve(location()).getParent().resolve(valuesFile).normalize();
        return getRedirectionValuesFromPath(normalizedRedirPath);
    }
}
