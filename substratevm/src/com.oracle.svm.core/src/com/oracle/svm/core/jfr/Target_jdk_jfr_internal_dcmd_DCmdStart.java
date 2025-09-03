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
package com.oracle.svm.core.jfr;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.jfr.internal.dcmd.DCmdStart")
public final class Target_jdk_jfr_internal_dcmd_DCmdStart {
    @Alias
    public Target_jdk_jfr_internal_dcmd_DCmdStart() {
    }

    @Alias
    private native LinkedHashMap<String, String> configureStandard(String[] settings);

    /**
     * We don't support extended JFR configurations at the moment. However, this method is also
     * called if the user specified an unknown/incorrect JFR option. To avoid confusing error
     * messages, we substitute this method so that it calls {@link #configureStandard} and prints a
     * warning for each unknown option.
     */
    @Substitute
    @SuppressWarnings("unused")
    private LinkedHashMap<String, String> configureExtended(String[] settings, Target_jdk_jfr_internal_dcmd_ArgumentParser parser) {
        LinkedHashMap<String, String> result = configureStandard(settings);
        for (String optionName : parser.getExtendedOptions().keySet()) {
            SubstrateUtil.cast(this, Target_jdk_jfr_internal_dcmd_AbstractDCmd.class).logWarning("The .jfc option/setting '" + optionName + "' doesn't exist or is not supported.");
        }
        return result;
    }

    @Substitute
    @SuppressWarnings("unused")
    private static String jfcOptions() {
        /* Not needed at the moment, as we do not support extended JFR configurations. */
        return "";
    }
}

@TargetClass(className = "jdk.jfr.internal.dcmd.ArgumentParser")
final class Target_jdk_jfr_internal_dcmd_ArgumentParser {
    @Alias
    native Map<String, Object> getExtendedOptions();
}
