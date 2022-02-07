/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.model;

import java.util.Locale;
import org.graalvm.component.installer.Feedback;

/**
 * Values for the stability level of a component.
 * 
 * @author sdedic
 */
public enum StabilityLevel {
    Undefined("undefined"),
    Supported("supported"),
    EarlyAdopter("earlyadopter"),
    Experimental("experimental"),
    Experimental_Earlyadopter("experimental-earlyadopter");

    private final String val;

    StabilityLevel(String aVal) {
        val = aVal;
    }

    public String displayName(Feedback fb) {
        return fb.withBundle(StabilityLevel.class).l10n("ComponentStabilityLevel_" + name().toLowerCase());
    }

    @Override
    public String toString() {
        if (this == Undefined) {
            return "";
        } else {
            return val;
        }
    }

    public static StabilityLevel valueOfMixedCase(String mcs) {
        if (mcs.isEmpty()) {
            return Undefined;
        }
        String s = mcs.replaceAll("[^\\p{Alnum}]", "_").toLowerCase(Locale.ENGLISH);
        for (StabilityLevel l : StabilityLevel.values()) {
            if (l.name().toLowerCase(Locale.ENGLISH).equalsIgnoreCase(s)) {
                return l;
            }
        }
        return Undefined;
    }

    public static StabilityLevel fromName(String name) {
        for (StabilityLevel level : values()) {
            if (level.val.equals(name)) {
                return level;
            }
        }
        return StabilityLevel.Undefined;
    }
}
