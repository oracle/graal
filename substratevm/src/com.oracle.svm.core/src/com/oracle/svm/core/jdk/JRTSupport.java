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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JRTSupport.JRTDisabled;
import com.oracle.svm.core.jdk.JRTSupport.JRTEnabled;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

/**
 * Support to access system Java modules and the <b>jrt://</b> file system.
 * 
 * <p>
 * <b>javac</b> and other tools that access the system modules, depend on the
 * <b>-Djava.home=/path/to/jdk</b> property to be set e.g. required by
 * <code>java.lang.module.ModuleFinder#ofSystem()</code>.
 */
public final class JRTSupport {

    static class Options {
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
}

@AutomaticallyRegisteredFeature
class JRTDisableFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !JRTSupport.Options.AllowJRTFileSystem.getValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ServiceCatalogSupport.singleton().removeServicesFromServicesCatalog("java.nio.file.spi.FileSystemProvider", new HashSet<>(Arrays.asList("jdk.internal.jrtfs.JrtFileSystemProvider")));
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
}

@TargetClass(className = "jdk.internal.jimage.ImageReaderFactory", onlyWith = JRTEnabled.class)
final class Target_jdk_internal_jimage_ImageReaderFactory_JRTEnabled {
    @Alias
    static native Target_jdk_internal_jimage_ImageReader_JRTEnabled getImageReader();

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class, isFinal = true) //
    static Map<Path, Target_jdk_internal_jimage_ImageReader_JRTEnabled> readers;
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

@TargetClass(className = "sun.net.www.protocol.jrt.Handler", onlyWith = JRTDisabled.class)
final class Target_sun_net_www_protocol_jrt_Handler_JRTDisabled {
    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    protected URLConnection openConnection(URL url) throws IOException {
        throw VMError.unsupportedFeature("JavaRuntimeURLConnection not available.");
    }
}

@TargetClass(className = "jdk.internal.jrtfs.JrtFileSystemProvider", onlyWith = JRTDisabled.class)
@Delete
final class Target_jdk_internal_jrtfs_JrtFileSystemProvider_JRTDisabled {
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
