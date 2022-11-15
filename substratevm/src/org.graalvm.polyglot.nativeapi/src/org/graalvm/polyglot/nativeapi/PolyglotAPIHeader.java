/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.c.CHeader.Header;

public class PolyglotAPIHeader implements Header {
    @Override
    public String name() {
        return "polyglot_api";
    }

    @Override
    public List<Class<? extends Header>> dependsOn() {
        return Collections.singletonList(PolyglotIsolateHeader.class);
    }

    @Override
    public void writePreamble(PrintWriter writer) {
        String[] preambleText = {
                        "#include <polyglot_types.h>",
                        "/*",
                        " * The Polyglot Native API provides a way to interact with GraalVM SDK polyglot API via a shared library.",
                        " *",
                        " * Much of the functionality provided here is wrappers for either:",
                        " * (1) The corresponding Java calls within the SDK",
                        " * (2) The calls needed for Isolate management from a shared library.",
                        " *",
                        " * The Javadoc for the GraalVM SDK polyglot API can be found at https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html",
                        " * Likewise, information about the Native Image C API for isolate management can be found at https://www.graalvm.org/22.3/reference-manual/native-image/native-code-interoperability/C-API/",
                        " *",
                        " * Please note that the Polyglot Native API is currently still experimental should not be used in production environments.",
                        " * Future versions will introduce modifications to the API in backward incompatible ways.",
                        " * Feel free to use the API for examples and experiments and keep us posted about the features that you need or you feel are awkward.",
                        " */",
        };
        Arrays.stream(preambleText).forEach(writer::println);
    }
}
