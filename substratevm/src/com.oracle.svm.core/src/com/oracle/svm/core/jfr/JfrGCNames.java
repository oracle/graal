/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.util.Arrays;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class JfrGCNames {
    private JfrGCName[] names;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrGCNames() {
        names = new JfrGCName[0];
    }

    @Fold
    public static JfrGCNames singleton() {
        return ImageSingletons.lookup(JfrGCNames.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized JfrGCName addGCName(String name) {
        int id = names.length;
        JfrGCName result = new JfrGCName(id, name);

        // We only expect a very small number of GC names (usually just 1).
        JfrGCName[] newArr = Arrays.copyOf(names, id + 1);
        newArr[id] = result;
        names = newArr;
        return result;
    }

    public JfrGCName[] getNames() {
        return names;
    }
}
