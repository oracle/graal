/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.localization;

import java.nio.charset.Charset;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = ApplicationLayerOnly.class)
public class DefaultCharsetSingleton {
    private final Charset defaultCharset;

    public DefaultCharsetSingleton(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    public static DefaultCharsetSingleton singleton() {
        return ImageSingletons.lookup(DefaultCharsetSingleton.class);
    }

    /** The application chooses the default charset; registrations are combined across layers. */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }
}
