/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.espresso.classfile.descriptors.NameSymbols;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbols;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Utf8Symbols;

import jdk.graal.compiler.api.replacements.Fold;

public final class SymbolsSupport {
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final SymbolsSupport TEST_SINGLETON = ImageInfo.inImageCode() ? null : new SymbolsSupport();

    private final Utf8Symbols utf8;
    private final NameSymbols names;
    private final TypeSymbols types;
    private final SignatureSymbols signatures;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SymbolsSupport() {
        int initialSymbolTableCapacity = 4 * 1024;
        Symbols symbols = Symbols.fromExisting(SVMSymbols.SYMBOLS.freeze(), initialSymbolTableCapacity, 0);
        // let this resize when first used at runtime
        utf8 = new Utf8Symbols(symbols);
        names = new NameSymbols(symbols);
        types = new TypeSymbols(symbols);
        signatures = new SignatureSymbols(symbols, types);
    }

    public static TypeSymbols getTypes() {
        return singleton().types;
    }

    public static SignatureSymbols getSignatures() {
        return singleton().signatures;
    }

    public static NameSymbols getNames() {
        return singleton().names;
    }

    public static Utf8Symbols getUtf8() {
        return singleton().utf8;
    }

    @Fold
    public static SymbolsSupport singleton() {
        if (TEST_SINGLETON != null) {
            /*
             * Some unit tests use com.oracle.svm.interpreter.metadata outside the context of
             * native-image.
             */
            assert !ImageInfo.inImageCode();
            return TEST_SINGLETON;
        }
        return ImageSingletons.lookup(SymbolsSupport.class);
    }
}
