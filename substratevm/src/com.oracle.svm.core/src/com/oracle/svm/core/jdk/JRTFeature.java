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
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.jdk.JRTFeature.JRTDisabled;
import com.oracle.svm.core.jdk.JRTFeature.JRTEnabled;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;

@AutomaticFeature
public final class JRTFeature implements Feature {

    static class Options {
        @Option(help = "Enable support for reading Java modules (jimage format) and the jrt filesystem.", type = OptionType.User) //
        public static final HostedOptionKey<Boolean> JRT = new HostedOptionKey<>(false);
    }

    static class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(JRTFeature.class);
        }
    }

    static class JRTDisabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !Options.JRT.getValue();
        }
    }

    static class JRTEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return Options.JRT.getValue();
        }
    }
}

// region Enable jimage/jrtfs

@TargetClass(className = "jdk.internal.module.SystemModuleFinders", innerClass = "SystemImage", onlyWith = {JDK11OrLater.class, JRTEnabled.class})
final class Target_jdk_internal_module_SystemModuleFinders_SystemImage {

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static volatile Target_jdk_internal_jimage_ImageReader READER;

    @Substitute
    static Object reader() {
        Target_jdk_internal_jimage_ImageReader reader = READER;
        if (reader == null) {
            synchronized (Target_jdk_internal_module_SystemModuleFinders_SystemImage.class) {
                reader = Target_jdk_internal_jimage_ImageReaderFactory.getImageReader();
                READER = reader;
            }
        }
        return reader;
    }
}

@TargetClass(className = "jdk.internal.jimage.ImageReader", onlyWith = {JDK11OrLater.class, JRTEnabled.class})
final class Target_jdk_internal_jimage_ImageReader {
}

@TargetClass(className = "jdk.internal.jimage.ImageReaderFactory", onlyWith = {JDK11OrLater.class, JRTEnabled.class})
final class Target_jdk_internal_jimage_ImageReaderFactory {
    @Alias
    static native Target_jdk_internal_jimage_ImageReader getImageReader();

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class, isFinal = true) //
    static Map<Path, Target_jdk_internal_jimage_ImageReader> readers;
}

// endregion Enable jimage/jrtfs

// region Disable jimage/jrtfs

/**
 * This class holds a reference to the jdk.internal.jimage.ImageReader. We don't support JIMAGE so
 * we just cut away the reader code.
 */
@TargetClass(className = "jdk.internal.module.SystemModuleFinders", innerClass = "SystemImage", onlyWith = {JDK11OrLater.class, JRTDisabled.class})
final class Target_jdk_internal_module_SystemModuleFinders_SystemImage_Disable {

    @Delete
    static native Object reader();
}

@TargetClass(className = "sun.net.www.protocol.jrt.Handler", onlyWith = {JDK11OrLater.class, JRTDisabled.class})
final class Target_sun_net_www_protocol_jrt_Handler {
    @Substitute
    @SuppressWarnings("unused")
    protected URLConnection openConnection(URL url) throws IOException {
        throw VMError.unsupportedFeature("JavaRuntimeURLConnection not available. Explicitly enable JRT support with -H:+JRT .");
    }
}

@TargetClass(className = "jdk.internal.jrtfs.JrtFileSystemProvider", onlyWith = {JDK11OrLater.class, JRTDisabled.class})
@Delete
final class Target_jdk_internal_jrtfs_JrtFileSystemProvider {
}

// endregion Disable jimage/jrtfs