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

/**
 * The names of the iprof file format, which {@link IprofWriter} writes and {@code --pgo} reads. The
 * format is described in {@code docs/reference-manual/native-image/PGO-IprofFileFormat.md}. A file
 * is one JSON object, for example:
 *
 * <pre>
 * {"version": "1.1.0",
 *  "types": [{"id": 0, "name": "java.lang.String"}, {"id": 1, "name": "int"}, {"id": 2, "name": "char"}],
 *  "methods": [{"id": 7, "name": "charAt", "signature": [0, 2, 1]}],
 *  "conditionalProfiles": [{"ctx": "7:12", "records": [14, 0, 90, 17, 1, 10]}]}
 * </pre>
 *
 * A method's {@code signature} lists the type ids of its declaring class, its return type and its
 * parameters. A {@code ctx} is a calling context, innermost frame first: {@code methodId:bci}
 * frames joined by {@code <}, as in {@code 7:12<3:5} for method 7 at bci 12 inlined into method 3
 * at bci 5.
 */
public final class IprofFormat {
    public static final String VERSION_1_0_0 = "1.0.0";
    public static final String VERSION_1_1_0 = "1.1.0";

    /** Keys of the top-level object, apart from the profile sections. */
    public static final String VERSION = "version";
    public static final String TYPES = "types";
    public static final String METHODS = "methods";

    /** Profile sections: arrays of entries, each with a {@link #CTX} and {@link #RECORDS}. */
    public static final String CONDITIONAL_PROFILES = "conditionalProfiles";
    public static final String CALL_COUNT_PROFILES = "callCountProfiles";
    public static final String VIRTUAL_INVOKE_PROFILES = "virtualInvokeProfiles";
    public static final String MONITOR_PROFILES = "monitorProfiles";
    public static final String SAMPLING_PROFILES = "samplingProfiles";

    /** Keys of an entry of the {@link #TYPES} and {@link #METHODS} tables. */
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String SIGNATURE = "signature";

    /** Keys of an entry of a profile section. */
    public static final String CTX = "ctx";
    public static final String RECORDS = "records";

    /** Separators of a {@link #CTX} string. */
    public static final char FRAME_SEPARATOR = '<';
    public static final char METHOD_BCI_SEPARATOR = ':';

    private IprofFormat() {
    }
}
