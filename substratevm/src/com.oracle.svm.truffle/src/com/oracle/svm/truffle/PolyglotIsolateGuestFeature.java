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
package com.oracle.svm.truffle;

import java.lang.reflect.Method;

import org.graalvm.jniutils.NativeBridgeSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.SubstrateGuestOptions;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.truffle.polyglot.isolate.PolyglotIsolateBridgeSupport;
import com.oracle.truffle.polyglot.isolate.PolyglotIsolateGuestFeatureEnabled;
import com.oracle.truffle.polyglot.isolate.ProcessIsolateEntryPoint;

/**
 * A feature enabling the guest part of the Truffle isolate support. The Truffle isolate support
 * consists of the host and guest part. The host part contains polyglot API entry points and support
 * for host types. The guest part is an isolate with guest languages. This feature enables Truffle
 * isolate entry points. It also registers the {@link NativeBridgeSupport} instance into
 * {@link ImageSingletons} to enable {@link org.graalvm.jniutils.JNI JNI support}. The polyglot
 * isolate code can use {@link ImageSingletons#contains(Class)
 * ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)} to prevent substratevm from
 * including methods that should not be reachable on the guest side.
 *
 * @see NativeBridgeSupport
 * @see PolyglotIsolateGuestFeatureEnabled
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class PolyglotIsolateGuestFeature implements Feature {

    @Override
    public String getDescription() {
        return "Provides polyglot guest isolate embedding support";
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        UserError.guarantee(OptionalTrufflePolyglotGuestFeatureEnabled.isAvailable(),
                        "The %s feature requires the truffle.jar, accessible via the maven coordinates org.graalvm.truffle:truffle-api, to be included on the module-path. " +
                                        "To resolve this, either remove the %s from the --features command line option or ensure the truffle.jar is added to the module-path " +
                                        "by including the language or tool maven artifact as a dependency.",
                        PolyglotIsolateGuestFeature.class.getSimpleName(), PolyglotIsolateGuestFeature.class.getName());
        // Fix symbol name clashes in the native-to-native mode with external library using the API
        // function prefix.
        ImageSingletons.add(PolyglotIsolateTearDownSupport.class, new PolyglotIsolateTearDownSupport(SubstrateGuestOptions.APIFunctionPrefix.getValue()));
        ImageSingletons.add(PolyglotIsolateGuestFeatureEnabled.class, new PolyglotIsolateGuestFeatureEnabled());
        ImageSingletons.add(NativeBridgeSupport.class, new PolyglotIsolateBridgeSupport());
        registerProcessIsolateEntryPoint();
    }

    private static void registerProcessIsolateEntryPoint() {
        RuntimeJNIAccess.register(ProcessIsolateEntryPoint.class);
        for (Method method : ProcessIsolateEntryPoint.class.getDeclaredMethods()) {
            RuntimeJNIAccess.register(method);
        }
    }
}

@TargetClass(className = "com.oracle.truffle.polyglot.isolate.StackPointerRetriever", onlyWith = OptionalTrufflePolyglotGuestFeatureEnabled.class)
final class Target_com_oracle_truffle_polyglot_isolate_StackPointerRetriever {

    @Substitute
    private static long getStackPointer() {
        return KnownIntrinsics.readStackPointer().rawValue();
    }
}
