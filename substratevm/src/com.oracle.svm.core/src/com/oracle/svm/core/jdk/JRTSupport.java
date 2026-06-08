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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JRTSupport.JRTDisabled;
import com.oracle.svm.core.jdk.JRTSupport.JRTEnabled;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.internal.loader.Resource;
import sun.net.www.ParseUtil;

/**
 * Support to access system Java modules and the <b>jrt://</b> file system.
 * 
 * <p>
 * <b>javac</b> and other tools that access the system modules, depend on the
 * <b>-Djava.home=/path/to/jdk</b> property to be set e.g. required by
 * <code>java.lang.module.ModuleFinder#ofSystem()</code>.
 */
public final class JRTSupport {
    private static volatile Target_jdk_internal_jimage_ImageReader_JRTEnabled imageReader;

    public static class Options {
        @Option(help = "Enable support for reading Java modules (jimage format) and the jrt:// file system. Requires java.home to be set at runtime.", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> AllowJRTFileSystem = new HostedOptionKey<>(false);
    }

    static class JRTEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return Options.AllowJRTFileSystem.getValue();
        }
    }

    static class JRTDisabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !Options.AllowJRTFileSystem.getValue();
        }
    }

    static boolean hasRuntimeModulesImage() {
        String javaHome = System.getProperty("java.home");
        return javaHome != null && Files.isRegularFile(Path.of(javaHome, "lib", "modules"));
    }

    @SuppressWarnings("deprecation")
    static URL toJrtURL(String module, String name) {
        try {
            return new URL("jrt:/" + module + "/" + name);
        } catch (MalformedURLException e) {
            throw new InternalError(e);
        }
    }

    @SuppressWarnings("deprecation")
    static URL toJrtURL(String module) {
        try {
            return new URL("jrt:/" + module);
        } catch (MalformedURLException e) {
            throw new InternalError(e);
        }
    }

    static Resource findEmbeddedResource(String module, String name) {
        if (module == null || name == null) {
            return null;
        }
        return ResourceBasedModuleReaderSupport.getEmbeddedResourceData(module, name, bytes -> new Resource() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public URL getURL() {
                return toJrtURL(module, name);
            }

            @Override
            public URL getCodeSourceURL() {
                return toJrtURL(module);
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public int getContentLength() {
                return bytes.length;
            }
        }).orElse(null);
    }

    static Resource findResource(String module, String name) {
        Resource embedded = findEmbeddedResource(module, name);
        if (embedded != null) {
            return embedded;
        }

        Target_jdk_internal_jimage_ImageReader_JRTEnabled reader = runtimeImageReader();
        if (reader == null) {
            return null;
        }

        Target_jdk_internal_jimage_ImageLocation_JRTEnabled location = reader.findLocation(module, name);
        if (location == null) {
            return null;
        }

        URL url = toJrtURL(module, name);
        return new Resource() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public URL getURL() {
                return url;
            }

            @Override
            public URL getCodeSourceURL() {
                return toJrtURL(module);
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(reader.getResource(location));
            }

            @Override
            public int getContentLength() {
                long size = location.getUncompressedSize();
                return size > Integer.MAX_VALUE ? -1 : (int) size;
            }
        };
    }

    private static Target_jdk_internal_jimage_ImageReader_JRTEnabled runtimeImageReader() {
        if (!hasRuntimeModulesImage()) {
            return null;
        }
        Target_jdk_internal_jimage_ImageReader_JRTEnabled localRef = imageReader;
        if (localRef == null) {
            synchronized (JRTSupport.class) {
                localRef = imageReader;
                if (localRef == null) {
                    imageReader = localRef = Target_jdk_internal_jimage_ImageReaderFactory_JRTEnabled.getImageReader();
                }
            }
        }
        return localRef;
    }
}

@AutomaticallyRegisteredFeature
class JRTFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (JRTSupport.Options.AllowJRTFileSystem.getValue()) {
            RuntimeClassInitialization.initializeAtRunTime(ReflectionUtil.lookupClass("jdk.internal.jrtfs.SystemImage"));
        } else {
            ServiceCatalogSupport.singleton().removeServicesFromServicesCatalog("java.nio.file.spi.FileSystemProvider", Set.of("jdk.internal.jrtfs.JrtFileSystemProvider"));
        }
    }
}

// region Enable jimage/jrtfs

@TargetClass(className = "jdk.internal.jimage.ImageReader", innerClass = "SharedImageReader", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_jimage_ImageReader_SharedImageReader_JRTEnabled {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = HashMap.class, isFinal = true) //
    // Checkstyle: stop
    static Map<Path, Target_jdk_internal_jimage_ImageReader_SharedImageReader_JRTEnabled> OPEN_FILES;
    // Checkstyle: resume
}

@TargetClass(className = "jdk.internal.module.SystemModuleFinders", innerClass = "SystemImage", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_module_SystemModuleFinders_SystemImage_JRTEnabled {

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static volatile Target_jdk_internal_jimage_ImageReader_JRTEnabled READER;

    @Substitute
    static Target_jdk_internal_jimage_ImageReader_JRTEnabled reader() {
        /*
         * ImageReaderFactory's class initializer dereferences java.home. JDK module readers
         * already treat a null system image reader as "resource not found".
         */
        if (!JRTSupport.hasRuntimeModulesImage()) {
            return null;
        }
        Target_jdk_internal_jimage_ImageReader_JRTEnabled localRef = READER;
        if (localRef == null) {
            synchronized (Target_jdk_internal_module_SystemModuleFinders_SystemImage_JRTEnabled.class) {
                localRef = READER;
                if (localRef == null) {
                    READER = localRef = Target_jdk_internal_jimage_ImageReaderFactory_JRTEnabled.getImageReader();
                }
            }
        }
        return localRef;
    }
}

@TargetClass(className = "jdk.internal.jimage.ImageReader", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_jimage_ImageReader_JRTEnabled {
    @Alias
    native Target_jdk_internal_jimage_ImageLocation_JRTEnabled findLocation(String module, String name);

    @Alias
    native byte[] getResource(Target_jdk_internal_jimage_ImageLocation_JRTEnabled location);
}

@TargetClass(className = "jdk.internal.jimage.ImageReaderFactory", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_jimage_ImageReaderFactory_JRTEnabled {
    @Alias
    static native Target_jdk_internal_jimage_ImageReader_JRTEnabled getImageReader();

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class, isFinal = true) //
    static Map<Path, Target_jdk_internal_jimage_ImageReader_JRTEnabled> readers;
}

@TargetClass(className = "jdk.internal.jimage.ImageLocation", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_jimage_ImageLocation_JRTEnabled {
    @Alias
    native long getUncompressedSize();
}

@TargetClass(className = "sun.net.www.protocol.jrt.Handler")
final class Target_sun_net_www_protocol_jrt_Handler {
    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    protected URLConnection openConnection(URL url) throws IOException {
        if (JRTSupport.Options.AllowJRTFileSystem.getValue()) {
            return new JRTURLConnection(url);
        }
        throw VMError.unsupportedFeature("JavaRuntimeURLConnection not available.");
    }
}

/*
 * Mirrors the JDK JavaRuntimeURLConnection. Native Image cannot use that class directly because
 * its package-private constructor and static ImageReader field bind it to ImageReaderFactory at
 * class initialization. JRTSupport keeps resource lookup conditional and lazy, and also checks
 * embedded resources before opening the runtime modules image.
 */
@BasedOnJDKClass(className = "sun.net.www.protocol.jrt.JavaRuntimeURLConnection")
final class JRTURLConnection extends URLConnection {
    private final String module;
    private final String name;
    private volatile Resource resource;

    JRTURLConnection(URL url) throws MalformedURLException {
        super(url);
        String path = url.getPath();
        if (path.isEmpty() || path.charAt(0) != '/') {
            throw new MalformedURLException(url + " missing path or /");
        }
        if (path.length() == 1) {
            this.module = null;
            this.name = null;
        } else {
            int pos = path.indexOf('/', 1);
            if (pos == -1) {
                this.module = path.substring(1);
                this.name = null;
            } else {
                this.module = path.substring(1, pos);
                this.name = ParseUtil.decode(path.substring(pos + 1));
            }
        }
    }

    @Override
    public synchronized void connect() throws IOException {
        if (!connected) {
            if (name == null) {
                String path = module == null ? "" : module;
                throw new IOException("Cannot connect to jrt:/" + path);
            }
            resource = JRTSupport.findResource(module, name);
            if (resource == null) {
                throw new IOException("Resource not found: " + module + "/" + name);
            }
            connected = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return resource.getInputStream();
    }

    @Override
    public long getContentLengthLong() {
        try {
            connect();
            return resource.getContentLength();
        } catch (IOException e) {
            return -1L;
        }
    }

    @Override
    public int getContentLength() {
        long length = getContentLengthLong();
        return length > Integer.MAX_VALUE ? -1 : (int) length;
    }
}

// endregion Enable jimage/jrtfs

// region Disable jimage/jrtfs

/**
 * This class holds a reference to the jdk.internal.jimage.ImageReader. We don't support JIMAGE so
 * we just cut away the reader code.
 */
@TargetClass(className = "jdk.internal.module.SystemModuleFinders", innerClass = "SystemImage", onlyWith = JRTDisabled.class)
final class Target_jdk_internal_module_SystemModuleFinders_SystemImage_JRTDisabled {
    @Delete //
    static Target_jdk_internal_jimage_ImageReader READER;

    @Substitute
    static Target_jdk_internal_jimage_ImageReader reader() {
        throw VMError.unsupportedFeature("JRT file system is disabled");
    }
}

@TargetClass(className = "jdk.internal.jimage.ImageReader", onlyWith = JRTDisabled.class)
final class Target_jdk_internal_jimage_ImageReader {
}

@TargetClass(className = "jdk.internal.jrtfs.JrtFileSystemProvider", onlyWith = JRTDisabled.class)
@Delete
final class Target_jdk_internal_jrtfs_JrtFileSystemProvider_JRTDisabled {
}

@TargetClass(className = "jdk.internal.jrtfs.JrtFileSystemProvider", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_jrtfs_JrtFileSystemProvider_BuildTime {
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    @Alias//
    volatile FileSystem theFileSystem;
}

// endregion Disable jimage/jrtfs

@TargetClass(className = "jdk.internal.jimage.BasicImageReader")
final class Target_jdk_internal_jimage_BasicImageReader {
    /* Ensure NativeImageBuffer never gets used as part of using BasicImageReader */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true) //
    // Checkstyle: stop
    static boolean USE_JVM_MAP = false;
    // Checkstyle: resume
}

@TargetClass(className = "jdk.internal.jimage.NativeImageBuffer")
@Substitute
final class Target_jdk_internal_jimage_NativeImageBuffer {
    @Substitute
    @SuppressWarnings("unused")
    static ByteBuffer getNativeMap(String imagePath) {
        throw VMError.unsupportedFeature("Using jdk.internal.jimage.NativeImageBuffer is not supported");
    }
}
