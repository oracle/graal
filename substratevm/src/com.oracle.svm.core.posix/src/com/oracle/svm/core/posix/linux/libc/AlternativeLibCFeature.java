/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux.libc;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;
import com.oracle.svm.core.option.HostedOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
public class AlternativeLibCFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Platform.includedIn(Platform.LINUX.class);
    }

    public static class LibCOptions {
        @Option(help = "file:doc-files/UseMuslCHelp.txt", type = OptionType.Expert)//
        public static final HostedOptionKey<String> UseMuslC = new HostedOptionKey<>(null);

        @Option(help = "When set to true, sets the internally used libc to Bionic. Note that this does not currently download and link against Bionic libc, but serves as a workaround that makes it possible externally", type = OptionType.Expert)//
        public static final HostedOptionKey<Boolean> UseBionicC = new HostedOptionKey<>(false);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        TemporaryBuildDirectoryProvider tempDirectoryProvider = ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class);
        LibCBase libc;
        if (LibCOptions.UseMuslC.hasBeenSet()) {
            libc = new MuslLibC();
        } else if (LibCOptions.UseBionicC.hasBeenSet()) {
            libc = new BionicLibC();
        } else {
            libc = new GLibC();
        }
        libc.prepare(tempDirectoryProvider.getTemporaryBuildDirectory());
        ImageSingletons.add(LibCBase.class, libc);
    }
}
