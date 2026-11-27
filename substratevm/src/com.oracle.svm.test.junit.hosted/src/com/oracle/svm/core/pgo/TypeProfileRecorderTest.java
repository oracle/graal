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
package com.oracle.svm.core.pgo;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TypeProfileRecorderTest {

    @Test
    public void aggregatesPhysicalSitesAndReceiverTypesByContext() {
        ProfileFrame[] context = {new ProfileFrame("Lexample/ReceiverRecorderTest;.call()V", 11)};
        TypeProfileKey first = TypeProfileRecorder.createReceiverKey(context, new int[]{7, 9}, new String[]{"Ljava/lang/String;", "Ljava/lang/Integer;"});
        TypeProfileKey second = TypeProfileRecorder.createReceiverKey(context, new int[]{7, 9}, new String[]{"Ljava/lang/String;", "Ljava/lang/Integer;"});

        TypeProfileRecorder.recordType(first.siteIndex(), 7);
        TypeProfileRecorder.recordType(first.siteIndex(), 7);
        TypeProfileRecorder.recordType(first.siteIndex(), 9);
        TypeProfileRecorder.recordType(second.siteIndex(), 7);

        TypeProfileRecorder.prepareSnapshot();
        List<TypeProfileRecorder.DecodedReceiverProfile> profiles = TypeProfileRecorder.decodeProfiles().receiverProfiles();
        TypeProfileRecorder.DecodedReceiverProfile profile = profiles.stream()
                        .filter(candidate -> candidate.context()[0].methodDescriptor().contains("ReceiverRecorderTest"))
                        .findFirst().orElseThrow();
        Assert.assertEquals(Long.valueOf(3), profile.countsByTypeDescriptor().get("Ljava/lang/String;"));
        Assert.assertEquals(Long.valueOf(1), profile.countsByTypeDescriptor().get("Ljava/lang/Integer;"));
    }

    @Test
    public void namesReceiversOutsideTheStaticProfileThroughTheTypeTable() {
        ProfileFrame[] context = {new ProfileFrame("Lexample/ReceiverRecorderTest;.saturated()V", 23)};
        /* A site whose static type profile was absent or saturated carries no pre-named receivers. */
        TypeProfileKey key = TypeProfileRecorder.createReceiverKey(context, new int[0], new String[0]);
        TypeProfileRecorder.registerTypeDescriptor(4201, "Lexample/Impl;");

        TypeProfileRecorder.recordType(key.siteIndex(), 4201);
        TypeProfileRecorder.recordType(key.siteIndex(), 4201);
        TypeProfileRecorder.recordType(key.siteIndex(), 4202);

        TypeProfileRecorder.prepareSnapshot();
        TypeProfileRecorder.DecodedReceiverProfile profile = TypeProfileRecorder.decodeProfiles().receiverProfiles().stream()
                        .filter(candidate -> candidate.context()[0].methodDescriptor().contains("saturated"))
                        .findFirst().orElseThrow();
        Assert.assertEquals(Long.valueOf(2), profile.countsByTypeDescriptor().get("Lexample/Impl;"));
        Assert.assertEquals("the unregistered type id stays unknown", 1, profile.countsByTypeDescriptor().size());
    }
}
