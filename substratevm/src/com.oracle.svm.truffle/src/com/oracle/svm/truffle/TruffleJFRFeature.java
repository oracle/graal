/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.util.Iterator;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.truffle.runtime.jfr.EventFactory;
import com.oracle.truffle.runtime.jfr.EventFactory.Provider;
import com.oracle.truffle.runtime.serviceprovider.TruffleRuntimeServices;

public class TruffleJFRFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        Iterator<Provider> providers = TruffleRuntimeServices.load(Provider.class).iterator();
        if (!providers.hasNext()) {
            throw UserError.abort("No EventFactory.Provider is registered in Truffle runtime services.");
        }
        Provider provider = providers.next();
        EventFactory factory = provider.getEventFactory();
        ImageSingletons.add(EventFactory.class, factory);
    }

    @Override
    public String getDescription() {
        return "Provides JFR flight recording for Truffle events.";
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isEnabled();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(TruffleFeature.class, JfrFeature.class);
    }

    private static boolean isEnabled() {
        return ImageSingletons.contains(TruffleFeature.class) && JfrFeature.isInConfiguration(true);
    }
}
