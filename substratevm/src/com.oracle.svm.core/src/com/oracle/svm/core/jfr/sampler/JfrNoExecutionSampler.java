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
package com.oracle.svm.core.jfr.sampler;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.JfrExecutionSamplerSupported;
import com.oracle.svm.core.jfr.JfrFeature;

public class JfrNoExecutionSampler extends JfrExecutionSampler {
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrNoExecutionSampler() {
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify execution sampling.", callerMustBe = true)
    public boolean isSampling() {
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void preventSamplingInCurrentThread() {
        /* Nothing to do. */
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void allowSamplingInCurrentThread() {
        /* Nothing to do. */
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void disallowThreadsInSamplerCode() {
        /* Nothing to do. */
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void allowThreadsInSamplerCode() {
        /* Nothing to do. */
    }

    @Override
    public void setIntervalMillis(long intervalMillis) {
        /* Nothing to do. */
    }

    @Override
    public void update() {
        /* Nothing to do. */
    }
}

@AutomaticallyRegisteredFeature
class JfrNoExecutionSamplerFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(JfrFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!JfrExecutionSamplerSupported.isSupported()) {
            ImageSingletons.add(JfrExecutionSampler.class, new JfrNoExecutionSampler());
        }
    }
}
