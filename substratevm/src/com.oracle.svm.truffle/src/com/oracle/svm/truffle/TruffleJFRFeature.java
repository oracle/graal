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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.truffle.jfr.EventFactory;
import org.graalvm.compiler.truffle.jfr.EventFactory.Provider;
import org.graalvm.compiler.truffle.runtime.serviceprovider.TruffleRuntimeServices;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.UserError;

@AutomaticallyRegisteredFeature
public class TruffleJFRFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (isEnabled()) {
            Iterator<Provider> providers = TruffleRuntimeServices.load(Provider.class).iterator();
            if (!providers.hasNext()) {
                throw UserError.abort("No EventFactory.Provider is registered in Truffle runtime services.");
            }
            Provider provider = providers.next();
            EventFactory factory = provider.getEventFactory();
            ImageSingletons.add(EventFactory.class, factory);
        }
    }

    private static boolean isEnabled() {
        // The substratevm JFR implementation is not yet stable on the JDK 17, see GR-38866.
        return ImageSingletons.contains(TruffleFeature.class) && ImageSingletons.contains(JfrFeature.class) && JavaVersionUtil.JAVA_SPEC < 17;
    }
}
