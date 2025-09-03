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
package com.oracle.svm.core.handles;

import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.impl.ObjectHandlesSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;
/*
 * Note in some cases object instances are accessed during code initialized at buildtime.
 * However, when this is done, one must be very careful to ensure analysis sees all changes
 * made to these objects.
 */

@AutomaticallyRegisteredImageSingleton(ObjectHandlesSupport.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class ObjectHandlesSupportImpl implements ObjectHandlesSupport {
    final ObjectHandlesImpl globalHandles = new ObjectHandlesImpl();

    @Override
    public ObjectHandles getGlobalHandles() {
        return globalHandles;
    }

    @Override
    public ObjectHandles createHandles() {
        return new ObjectHandlesImpl();
    }
}
