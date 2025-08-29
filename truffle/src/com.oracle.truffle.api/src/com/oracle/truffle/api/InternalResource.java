/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.graalvm.nativeimage.ImageInfo;

/**
 * Represents an internal resource of a language that can be lazily unpacked to a cache user
 * directory.
 * <p>
 * A typical implementation of an {@code InternalResource} for a platform-specific library stored in
 * the {@code META-INF/resources/<language-id>/<resource-id>/<os>/<arch>} looks like this:
 *
 * <pre>
 * &#64;InternalResource.Id("resource-id")
 * final class NativeLibResource implements InternalResource {
 *
 *     &#64;Override
 *     public void unpackFiles(Env env, Path targetDirectory) throws IOException {
 *         Path base = Path.of("META-INF", "resources", "mylanguage", "resource-id",
 *                         env.getOS().toString(), env.getCPUArchitecture().toString());
 *         env.unpackResourceFiles(base.resolve("file-list"), targetDirectory, base);
 *     }
 *
 *     &#64;Override
 *     public String versionHash(Env env) {
 *         try {
 *             Path hashResource = Path.of("META-INF", "resources", "mylanguage", "resource-id",
 *                             env.getOS().toString(), env.getCPUArchitecture().toString(), "sha256");
 *             return env.readResourceLines(hashResource).get(0);
 *         } catch (IOException ioe) {
 *             throw CompilerDirectives.shouldNotReachHere(ioe);
 *         }
 *     }
 * }
 * </pre>
 *
 * The resource files are listed in the
 * {@code META-INF/resources/<language-id>/<resource-id>/<os>/<arch>/file-list} file. For the file
 * list format, refer to {@link InternalResource#unpackFiles(Env, Path)}. Additionally, the
 * {@code META-INF/resources/<language-id>/<resource-id>/<os>/<arch>/sha256} file contains an
 * SHA-256 hash of the resource files. It is recommended to use non-encapsulated resource paths that
 * include the component ID and resource ID, as this helps prevent ambiguity when the language or
 * instrument is used in an unnamed module.
 *
 * @since 23.1
 */
public interface InternalResource {

    /**
     * Unpacks all resources to a given target directory. The target directory is guaranteed to be
     * writable and the unpacking is synchronized by a file system lock. If a resource was
     * previously cached then {@link #versionHash(Env)} is invoked and the version string is
     * compared. If it matches then {@link #unpackFiles(Env, Path)} will not be invoked and the
     * directory will be used as previously unpacked. The target directory is guaranteed to exist
     * and guaranteed to be empty.
     * <p>
     * Ideally the result of this method should be idempotent in order to be safely cacheable. Care
     * should be taken, if system properties are used that change the result of this method. It is
     * safe to use {@link OS} and {@link CPUArchitecture} enums as internal resources are never
     * cached or moved across operating system architectures.
     * <p>
     * No guest language code must run as part of this method.
     *
     * @param targetDirectory the target directory to extract files to
     * @since 23.1
     */
    void unpackFiles(Env env, Path targetDirectory) throws IOException;

    /**
     * Returns the version hash to be used for this resource. It is the responsibility of the
     * implementer to ensure that this version hash is unique. For example, an SHA-256 could be a
     * good version identifier for file based resources. Since the version hash serves as a path
     * component on the host filesystem, its length is restricted to a maximum of 128 bytes. If the
     * version hash length exceeds this limit, an {@code IOException} will be thrown during
     * unpacking.
     *
     * @since 23.1
     */
    String versionHash(Env env) throws IOException;

    /**
     * Access to common utilities for unpacking resource files.
     *
     * @since 23.1
     */
    final class Env {

        private final Class<? extends InternalResource> resourceClass;
        private final Module owner;
        private final BooleanSupplier contextPreinitializationCheck;

        Env(InternalResource resource, BooleanSupplier contextPreinitializationCheck) {
            this.resourceClass = resource.getClass();
            this.owner = resourceClass.getModule();
            this.contextPreinitializationCheck = Objects.requireNonNull(contextPreinitializationCheck, "ContextPreinitializationCheck  must be non-null.");
        }

        /**
         * Returns {@code true} if the engine causing the resource unpacking is being
         * pre-initialized.
         *
         * @since 23.1
         */
        public boolean inContextPreinitialization() {
            return contextPreinitializationCheck.getAsBoolean();
        }

        /**
         * Returns {@code true} if resource unpacking happens during the native image build.
         *
         * @since 23.1
         */
        @SuppressWarnings("static-method")
        public boolean inNativeImageBuild() {
            return ImageInfo.inImageBuildtimeCode();
        }

        /**
         * Returns the current processor architecture. The value can be used to resolve an
         * architecture specific files during resource unpacking.
         *
         * @since 23.1
         */
        @SuppressWarnings("static-method")
        public CPUArchitecture getCPUArchitecture() {
            return CPUArchitecture.getCurrent();
        }

        /**
         * Returns the current operating system. The value can be used to resolve an OS specific
         * files during resource unpacking.
         *
         * @since 23.1
         */
        @SuppressWarnings("static-method")
        public OS getOS() {
            return OS.getCurrent();
        }

        /**
         * Reads a resource from the module, which owns the {@link InternalResource} implementation
         * class. If the resource is encapsulated in the module, see
         * {@link Module#getResourceAsStream(String)}, the module needs to open the {@code location}
         * enclosing package to the {@code org.graalvm.truffle} module. It is recommended to use
         * non-encapsulated resource paths.
         *
         * @param location relative path that identifies the resource in the module. The relative
         *            path gets resolved into an absolute path using the archive root.
         * @return the lines from the resource as a {@link List}
         * @throws IOException in case of IO error
         * @since 23.1
         */
        public List<String> readResourceLines(Path location) throws IOException {
            if (location.isAbsolute()) {
                throw new IllegalArgumentException("Location must be a relative path, but the absolute path " + location + " was given.");
            }
            String resourceName = getResourceName(location);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(getResourceStream(resourceName), StandardCharsets.UTF_8))) {
                List<String> content = new ArrayList<>();
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    content.add(line);
                }
                return content;
            }
        }

        /**
         * Extracts files from the module, which owns the {@link InternalResource} implementation
         * class, listed in the {@code source} file list and places them into the {@code target}
         * folder. If resources are encapsulated within the module, see
         * {@link Module#getResourceAsStream(String)}, the module needs to open the enclosing
         * package of the resources to the {@code org.graalvm.truffle} module. It is recommended to
         * use non-encapsulated resource paths.
         * <p>
         * The file list is a {@link Properties Java properties} file where resource files serve as
         * keys, and the corresponding values consist of serialized attributes separated by
         * {@code ','}. Currently, only the POSIX file permissions attribute is supported. The
         * format of this attribute follows the same convention used by the
         * {@link PosixFilePermissions#fromString(String)}.
         * <p>
         * Example of a file list content:
         *
         * <pre>
         * META-INF/resources/darwin/amd64/bin/libtrufflenfi.dylib = rwxr-xr-x
         * META-INF/resources/common/include/trufflenfi.h = rw-r--r--
         * </pre>
         *
         * @param source the relative path that identifies the file list resource in the module. The
         *            relative path gets resolved into an absolute path using the archive root.
         * @param target the folder to extract resources to
         * @param relativizeTo the path used to relativize the file list entries in the
         *            {@code target} folder. In other words, the file list entries are resolved
         *            using the {@code target} directory after removing the {@code relativizeTo}
         *            path.
         * @param filter resource path filter. Only resources at resource paths for which the filter
         *            predicate returns <code>true</code> are unpacked.
         * @throws IllegalArgumentException if {@code relativizeTo} is an absolute path or file list
         *             contains an absolute path
         * @throws IOException in case of IO error
         * @since 24.2
         */
        public void unpackResourceFiles(Path source, Path target, Path relativizeTo, Predicate<Path> filter) throws IOException {
            Objects.requireNonNull(filter);
            if (source.isAbsolute()) {
                throw new IllegalArgumentException("Source must be a relative path, but the absolute path " + source + " was given.");
            }
            if (relativizeTo.isAbsolute()) {
                throw new IllegalArgumentException("RelativizeTo must be a relative path, but the absolute path " + relativizeTo + " was given.");
            }
            Properties fileList = loadFileList(source);
            for (var e : fileList.entrySet()) {
                Path resource = Path.of((String) e.getKey());
                Set<PosixFilePermission> attrs = parseAttrs((String) e.getValue());
                if (resource.isAbsolute()) {
                    throw new IllegalArgumentException("The file list must contain only relative paths, but the absolute path " + resource + " was given.");
                }
                Path relativizedPath = resource.startsWith(relativizeTo) ? relativizeTo.relativize(resource) : relativizeTo;
                if (filter.test(resource)) {
                    copyResource(resource, target.resolve(relativizedPath), attrs);
                }
            }
        }

        /**
         * Extracts files from the module, which owns the {@link InternalResource} implementation
         * class, listed in the {@code source} file list and places them into the {@code target}
         * folder. Same as {@link #unpackResourceFiles(Path, Path, Path, Predicate)} with
         * <code>filter = p -> true</code>.
         *
         * @see #unpackResourceFiles(Path, Path, Path, Predicate)
         * @since 23.1
         */
        public void unpackResourceFiles(Path source, Path target, Path relativizeTo) throws IOException {
            unpackResourceFiles(source, target, relativizeTo, p -> true);
        }

        private Properties loadFileList(Path source) throws IOException {
            Properties props = new Properties();
            String resourceName = getResourceName(source);
            try (BufferedInputStream in = new BufferedInputStream(getResourceStream(resourceName))) {
                props.load(in);
            }
            return props;
        }

        /**
         * Parses attributes. Now a single attribute, the posix permissions, is supported. But the
         * format can be extended by other attributes separated by {@code ','}.
         */
        private static Set<PosixFilePermission> parseAttrs(String rawValue) {
            String[] attrComponents = rawValue.split(",");
            if (attrComponents.length != 1) {
                throw new IllegalArgumentException("Invalid attributes format " + rawValue + ". Attribute value can have only a single component");
            }
            return PosixFilePermissions.fromString(attrComponents[0].trim());
        }

        private static String getResourceName(Path path) {
            return path.toString().replace(File.separatorChar, '/');
        }

        private InputStream getResourceStream(String resourceName) throws IOException {
            InputStream stream;
            if (owner.isNamed()) {
                stream = owner.getResourceAsStream(resourceName);
            } else {
                Enumeration<URL> resources = resourceClass.getClassLoader().getResources(resourceName);
                URL resource = preferredResource(resources);
                stream = resource != null ? resource.openStream() : null;
            }
            if (stream == null) {
                throw new NoSuchFileException(resourceName);
            }
            return stream;
        }

        private URL preferredResource(Enumeration<URL> candidates) {
            if (!candidates.hasMoreElements()) {
                return null;    // No resource found
            }
            URL preferred = candidates.nextElement();
            if (!candidates.hasMoreElements()) {
                return preferred;   // Single resource
            }
            CodeSource codeSource = resourceClass.getProtectionDomain().getCodeSource();
            URL location = codeSource != null ? codeSource.getLocation() : null;
            if (location == null) {
                return preferred;  // Classloader does not provide code source location, return the
                // first resource
            }
            Path classOwner = fileURL(location);
            if (classOwner == null) {
                return preferred; // Code source is not a file, return the first resource
            }
            if (!isInClassSourceLocation(preferred, classOwner)) {
                while (candidates.hasMoreElements()) {
                    URL candidate = candidates.nextElement();
                    if (isInClassSourceLocation(candidate, classOwner)) {
                        preferred = candidate;
                        break;
                    }
                }
            }
            return preferred;
        }

        private static boolean isInClassSourceLocation(URL resource, Path classSourceLocation) {
            Path resourceOwner = fileURL(resource);
            return resourceOwner != null && resourceOwner.startsWith(classSourceLocation);
        }

        private static Path fileURL(URL url) {
            try {
                URI useURI = url.toURI();
                if ("jar".equals(url.getProtocol())) {
                    String path = useURI.getRawSchemeSpecificPart();
                    int index = path.indexOf("!/");
                    String jarPath = index >= 0 ? path.substring(0, index) : null;
                    useURI = jarPath != null ? new URI(jarPath) : null;
                }
                if (useURI != null && "file".equals(useURI.getScheme())) {
                    return Paths.get(useURI);
                }
                return null;
            } catch (URISyntaxException e) {
                return null;
            }
        }

        private void copyResource(Path source, Path target, Set<PosixFilePermission> attrs) throws IOException {
            String resourceName = getResourceName(source);
            Path parent = target.getParent();
            if (parent == null) {
                throw new AssertionError("RelativeResourcePath must be non-empty.");
            }
            Files.createDirectories(parent);
            try (BufferedInputStream in = new BufferedInputStream(getResourceStream(resourceName))) {
                Files.copy(in, target);
            }
            if (getOS() != OS.WINDOWS) {
                Files.setPosixFilePermissions(target, attrs);
            }
        }
    }

    /**
     * The annotation used to lookup {@link InternalResource} by an id. The internal resource can be
     * designated as either required or optional. For required internal resources, their
     * availability is imperative. In addition to being annotated with this annotation, they also
     * need to be registered using the {@link TruffleLanguage.Registration#internalResources()}
     * method to be associated with a specific language or instrument. On the other hand, optional
     * internal resources are not mandatory in the runtime. Instead of being registered using the
     * {@link TruffleLanguage.Registration#internalResources()} annotation, they should supply the
     * relevant language or instrument id through {@link Id#componentId()}.
     *
     * @since 23.1
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Id {

        /**
         * An internal resource identifier that is a valid path component and unique per language.
         *
         * @since 23.1
         */
        String value();

        /**
         * A Truffle language or instrument identifier for which the resource is registered. The
         * {@code componentId} is necessary only for optional internal resources to associate them
         * with the appropriate language or instrument. For required internal resources, the
         * {@code componentId} can be left unset.
         *
         * @since 23.1
         */
        String componentId() default "";

        /**
         * Marks the annotated resource as optional. By default, internal resources are considered
         * required and must be registered using {@code TruffleLanguage.Registration} or
         * {@code TruffleInstrument.Registration}. Optional internal resources are retrieved using
         * the {@link java.util.ServiceLoader} mechanism. The service implementation is generated by
         * an annotation processor. Therefore, for proper functionality, the Truffle DSL processor
         * must be included in the processor path.
         *
         * @since 23.1
         */
        boolean optional() default false;
    }

    /**
     * Represents a supported operating system.
     *
     * @since 23.1
     */
    enum OS {

        /**
         * The macOS operating system.
         *
         * @since 23.1
         */
        DARWIN("darwin"),

        /**
         * The Linux operating system.
         *
         * @since 23.1
         */
        LINUX("linux"),

        /**
         * The Windows operating system.
         *
         * @since 23.1
         */
        WINDOWS("windows"),

        /**
         * Represents an unsupported operating system.
         * <p>
         * To enable execution on unsupported platforms, the system property
         * {@code -Dpolyglot.engine.allowUnsupportedPlatform=true} must be explicitly set. If this
         * property is not set and the platform is unsupported, the {@link #getCurrent()} method
         * will throw an {@link IllegalStateException}.
         *
         * @since 26.0
         */
        UNSUPPORTED("unsupported");

        private static final String PROPERTY_ALLOW_UNSUPPORTED_PLATFORM = "polyglot.engine.allowUnsupportedPlatform";

        private static volatile Boolean allowsUnsupportedPlatformValue;

        private static boolean allowsUnsupportedPlatform() {
            Boolean res = allowsUnsupportedPlatformValue;
            if (res == null) {
                synchronized (OS.class) {
                    res = allowsUnsupportedPlatformValue;
                    if (res == null) {
                        res = Boolean.getBoolean(OS.PROPERTY_ALLOW_UNSUPPORTED_PLATFORM);
                        if (!ImageInfo.inImageBuildtimeCode()) {
                            /*
                             * Avoid caching the property value during image build time, as it would
                             * require resetting it in the image heap later.
                             */
                            allowsUnsupportedPlatformValue = res;
                        }
                    }
                }
            }
            return res;
        }

        private final String id;

        OS(String id) {
            this.id = id;
        }

        /**
         * Returns the string representing operating system name.
         *
         * @since 23.1
         */
        @Override
        public String toString() {
            return id;
        }

        /**
         * Returns the current operating system.
         *
         * @since 23.1
         */
        public static OS getCurrent() {
            String os = System.getProperty("os.name");
            if (os == null) {
                throw new AssertionError("The 'os.name' system property is not set.");
            } else if (os.equalsIgnoreCase("linux")) {
                return LINUX;
            } else if (os.equalsIgnoreCase("mac os x") || os.equalsIgnoreCase("darwin")) {
                return DARWIN;
            } else if (os.toLowerCase().startsWith("windows")) {
                return WINDOWS;
            } else if (allowsUnsupportedPlatform()) {
                return UNSUPPORTED;
            } else {
                throw new IllegalStateException(String.format("Unsupported operating system: '%s'. " +
                                "If you want to continue using this unsupported platform, set the system property '-D%s=true'. " +
                                "Note that unsupported platforms require additional command line options to be functional.", os, PROPERTY_ALLOW_UNSUPPORTED_PLATFORM));
            }
        }
    }

    /**
     * Represents a supported CPU architecture.
     *
     * @since 23.1
     */
    enum CPUArchitecture {

        /**
         * The ARMv8 64-bit architecture.
         *
         * @since 23.1
         */
        AARCH64("aarch64"),

        /**
         * The x86 64-bit architecture.
         *
         * @since 23.1
         */
        AMD64("amd64"),

        /**
         * Represents an unsupported architecture.
         * <p>
         * To enable execution on unsupported platforms, the system property
         * {@code -Dpolyglot.engine.allowUnsupportedPlatform=true} must be explicitly set. If this
         * property is not set and the platform is unsupported, the {@link #getCurrent()} method
         * will throw an {@link IllegalStateException}.
         *
         * @since 26.0
         */
        UNSUPPORTED("unsupported");

        private final String id;

        CPUArchitecture(String id) {
            this.id = id;
        }

        /**
         * Returns the string representing CPU architecture name.
         *
         * @since 23.1
         */
        @Override
        public String toString() {
            return id;
        }

        /**
         * Returns the current CPU architecture.
         *
         * @since 23.1
         */
        public static CPUArchitecture getCurrent() {
            String arch = System.getProperty("os.arch");
            if (arch == null) {
                throw new AssertionError("The 'os.arch' system property is not set.");
            }
            return switch (arch) {
                case "amd64", "x86_64" -> AMD64;
                case "aarch64", "arm64" -> AARCH64;
                default -> {
                    if (OS.allowsUnsupportedPlatform()) {
                        yield UNSUPPORTED;
                    } else {
                        throw new IllegalStateException(String.format("Unsupported CPU architecture: '%s'. " +
                                        "If you want to allow unsupported CPU architectures (which will cause the fallback Truffle runtime to be used and may disable some language features), " +
                                        "set the system property '-D%s=true'.", arch, OS.PROPERTY_ALLOW_UNSUPPORTED_PLATFORM));
                    }
                }
            };
        }
    }
}
