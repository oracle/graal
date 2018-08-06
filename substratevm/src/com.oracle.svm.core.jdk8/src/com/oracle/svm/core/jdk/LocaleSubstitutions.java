/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

import sun.text.normalizer.UBiDiProps;
import sun.text.normalizer.UCharacterProperty;

@TargetClass(sun.text.normalizer.UCharacterProperty.class)
final class Target_sun_text_normalizer_UCharacterProperty {

    @Substitute
    private static UCharacterProperty getInstance() {
        return Util_sun_text_normalizer_UCharacterProperty.instance;
    }
}

final class Util_sun_text_normalizer_UCharacterProperty {
    static final UCharacterProperty instance = UCharacterProperty.getInstance();
}

@TargetClass(sun.text.normalizer.UBiDiProps.class)
final class Target_sun_text_normalizer_UBiDiProps {

    @Substitute
    private static UBiDiProps getSingleton() {
        return Util_sun_text_normalizer_UBiDiProps.singleton;
    }
}

final class Util_sun_text_normalizer_UBiDiProps {

    static final UBiDiProps singleton;

    static {
        UnsafeAccess.UNSAFE.ensureClassInitialized(sun.text.normalizer.NormalizerImpl.class);

        try {
            singleton = UBiDiProps.getSingleton();
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
