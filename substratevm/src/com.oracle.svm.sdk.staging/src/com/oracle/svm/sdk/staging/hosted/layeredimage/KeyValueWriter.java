/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.sdk.staging.hosted.layeredimage;

import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacks;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacks.LayeredSingletonInstantiator;

/**
 * When building layered images, sometimes it is necessary to pass some metadata from one layer to
 * the next. The mechanism we expose to accomplish this is a key-value map.
 *
 * To make this concrete, when layer X wants to relay information to layer Y, then in layer X a
 * {@link KeyValueWriter} will be provided which will then be exposed to layer Y via a
 * {@link KeyValueLoader}.
 *
 * Currently this mechanism is only exposed through {@link SingletonLayeredCallbacks} to allow
 * Singletons to pass information across layers. Note that each Singleton implementation has its own
 * unique key-value map, so each call of {@link SingletonLayeredCallbacks#doPersist} (and the
 * corresponding {@link SingletonLayeredCallbacks#onSingletonRegistration} or
 * {@link LayeredSingletonInstantiator} in the subsequent layer) to be tied to a unique map and one
 * does not need to worry about key name conflicts across different singleton implementations.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface KeyValueWriter {

    void writeBoolList(String keyName, List<Boolean> value);

    void writeInt(String keyName, int value);

    void writeIntList(String keyName, List<Integer> value);

    void writeLong(String keyName, long value);

    void writeString(String keyName, String value);

    void writeStringList(String keyName, List<String> value);
}
