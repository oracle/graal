/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.hsail.replacements;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.word.*;
import com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.hsail.nodes.*;

//JaCoCo Exclude

/**
 * A collection of methods used in HSAIL-specific snippets and substitutions.
 */
public class HSAILHotSpotReplacementsUtil extends HotSpotReplacementsUtil {

    private static HotSpotRegistersProvider hsailRegisters;

    public static void initialize(HotSpotRegistersProvider registers) {
        hsailRegisters = registers;
    }

    /**
     * Gets the value of the thread register as a Word.
     */
    public static Word thread() {
        return registerAsWord(threadRegister(), true, false);
    }

    @Fold
    public static Register threadRegister() {
        return hsailRegisters.getThreadRegister();
    }

    public static Word atomicGetAndAddTlabTop(Word thread, int size) {
        return Word.unsigned(AtomicGetAndAddNode.atomicGetAndAdd(thread.rawValue(), threadTlabTopOffset(), TLAB_TOP_LOCATION, size));
    }

    public static final LocationIdentity TLAB_PFTOP_LOCATION = new NamedLocationIdentity("TlabPfTop");

    @Fold
    public static int threadTlabPfTopOffset() {
        return config().threadTlabPfTopOffset();
    }

    public static void writeTlabPfTop(Word thread, Word val) {
        thread.writeWord(threadTlabPfTopOffset(), val, TLAB_PFTOP_LOCATION);
    }

}
