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
package com.oracle.svm.test.junit.shared.configure;

import java.io.IOException;
import java.io.StringReader;
import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.config.ForeignConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.test.api.AddExports;

@AddExports({"org.graalvm.nativeimage/org.graalvm.nativeimage.impl", "jdk.graal.compiler/jdk.graal.compiler.util", "jdk.graal.compiler/jdk.graal.compiler.util.json"})
public class ReachabilityMetadataReasonTest {
    @Test
    public void reasonIsAcceptedByStrictMetadataParsers() throws IOException {
        String metadata = """
                        {
                          "reflection": [{"type": "java.lang.String", "reason": "reflection needed by the application"}],
                          "resources": [
                            {"glob": "hello.txt", "reason": ["resource needed by the application"]},
                            {"bundle": "messages", "reason": "bundle needed by the application"}
                          ],
                          "foreign": {
                            "downcalls": [{"returnType": "void", "parameterTypes": [], "reason": "native call"}],
                            "upcalls": [{"returnType": "void", "parameterTypes": [], "reason": ["callback"]}],
                            "directUpcalls": [{"class": "java.lang.String", "method": "toString", "reason": "direct callback"}]
                          }
                        }
                        """;
        EnumSet<ConfigurationParserOption> strict = EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION);

        TypeConfiguration reflection = new TypeConfiguration();
        reflection.createParser(true, strict).parseAndRegister(new StringReader(metadata));
        Assert.assertFalse(reflection.isEmpty());

        ResourceConfiguration resources = new ResourceConfiguration();
        resources.createParser(true, strict).parseAndRegister(new StringReader(metadata));
        Assert.assertFalse(resources.isEmpty());

        ForeignConfiguration foreign = new ForeignConfiguration();
        foreign.createParser(true, strict).parseAndRegister(new StringReader(metadata));
        Assert.assertFalse(foreign.isEmpty());
    }
}
