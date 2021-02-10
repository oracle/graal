/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import com.oracle.svm.core.util.VMError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.Configuration;
import jdk.jfr.internal.jfc.JFC;
import static jdk.jfr.internal.jfc.JFC.nameFromPath;

public final class PredefinedJFCSubstitition {

    static Map<String, Configuration> knownKonfigurations;
    static final String DEFAULT_JFC = "com/oracle/svm/jfr/jdk11/jfc/default.jfc";
    static final String PROFILE_JFC = "com/oracle/svm/jfr/jdk11/jfc/profile.jfc";

    static {
        knownKonfigurations = new HashMap<>();
        try {
            registerConf(DEFAULT_JFC);
            registerConf(PROFILE_JFC);
        } catch (IOException | ParseException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static void registerConf(String confPath) throws IOException, ParseException {
        String name = nameFromPath(Paths.get(confPath));
        ClassLoader cl = PredefinedJFCSubstitition.class.getClassLoader();
        InputStream is = cl.getResourceAsStream(confPath);
        Configuration conf = JFC.create(name, new InputStreamReader(is));

        knownKonfigurations.put(name, conf);
    }
}
