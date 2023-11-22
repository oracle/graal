/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import com.oracle.svm.core.c.CIsolateDataStorage;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.ConcurrentIdentityHashMap;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

@AutomaticallyRegisteredFeature
public class CIsolateDataFeature implements InternalFeature {

    private final Map<String, CIsolateData<?>> usedEntries = new ConcurrentIdentityHashMap<>();

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::replaceObject);
    }

    private Object replaceObject(Object obj) {
        if (obj instanceof CIsolateData<?>) {
            CIsolateData<?> entry = (CIsolateData<?>) obj;
            usedEntries.compute(entry.getName(), (key, old) -> {
                VMError.guarantee(old == null || old == entry, "The isolate data section already contains an entry for %s", key);
                return entry;
            });
        }
        return obj;
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        UnsignedWord offset = WordFactory.zero();
        CIsolateData<?>[] entries = usedEntries.values().toArray(new CIsolateData<?>[0]);
        Arrays.sort(entries, Comparator.comparing(CIsolateData<?>::getSize).thenComparing(CIsolateData<?>::getName));
        for (CIsolateData<?> entry : entries) {
            offset = UnsignedUtils.roundUp(offset, WordFactory.unsigned(CIsolateDataStorage.ALIGNMENT));
            entry.setOffset(offset);
            offset = offset.add(WordFactory.unsigned(entry.getSize()));
        }

        CIsolateDataStorage.singleton().setSize(offset);
    }
}
