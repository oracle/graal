/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.jni.JNILibraryLoadFeature;
import com.oracle.svm.jni.functions.JNIFunctionTablesFeature;

/**
 * Support for the Java Native Interface (JNI). Read more in JNI.md in the project's root directory.
 *
 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/jni/">Java Native Interface
 *      Specification</a>
 */
public class JNIFeature implements Feature {
    public static class Options {
        @Option(help = "Enable Java Native Interface (JNI) support.")//
        public static final HostedOptionKey<Boolean> JNI = new HostedOptionKey<>(false);

        @Option(help = "Files describing program elements to be made accessible via JNI (for syntax, see ReflectionConfigurationFiles)", type = OptionType.User)//
        public static final HostedOptionKey<String> JNIConfigurationFiles = new HostedOptionKey<>("");

        @Option(help = "Resources describing program elements to be made accessible via JNI (see JNIConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<String> JNIConfigurationResources = new HostedOptionKey<>("");
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(JNIFunctionTablesFeature.class, JNICallWrapperFeature.class, JNILibraryLoadFeature.class);
    }
}
