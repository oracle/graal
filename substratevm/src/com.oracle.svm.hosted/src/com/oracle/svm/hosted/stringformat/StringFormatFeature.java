/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.stringformat;

import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.stringformat.StringFormat;
import com.oracle.svm.core.stringformat.StringFormatPhase;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * See {@link StringFormat} for a description of the overall approach. This class collects the
 * "zero" character for all reachable {@link Locale} objects in the image heap.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
final class StringFormatFeature implements InternalFeature {

    private final Map<Locale, Character> seenLocales = new ConcurrentHashMap<>();

    @Override
    public void duringSetup(DuringSetupAccess a) {
        if (!StringFormatPhase.Options.IntrinsifyStringFormat.getValue()) {
            return;
        }
        ImageSingletons.add(StringFormat.class, new StringFormat());
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        access.registerObjectReachableCallback(Locale.class, this::collectZeroDigits);
    }

    @SuppressWarnings("unused")
    private void collectZeroDigits(DuringAnalysisAccess access, Locale locale, ObjectScanner.ScanReason reason) {
        /*
         * DecimalFormatSymbols is not thread-safe, so we protect the zero digit computation with a
         * concurrent map.
         */
        char zeroDigit = seenLocales.computeIfAbsent(locale, l -> DecimalFormatSymbols.getInstance(l).getZeroDigit());
        EconomicMap<Locale, Character> zeroChars = StringFormat.singleton().zeroChars;
        if (zeroDigit != '0' && !zeroChars.containsKey(locale)) {
            zeroChars.put(locale, Character.valueOf(zeroDigit));
        }
    }
}
