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
import java.net.URI;
import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.test.api.AddExports;

import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonParserException;

@AddExports({"jdk.graal.compiler/jdk.graal.compiler.util.json"})
public class ConfigurationParserTest {

    @Test
    public void lambdaDeclaringMethodRequiresParameterTypes() {
        for (String method : new String[]{"{\"name\":\"make\"}", "{\"name\":\"make\",\"parameterType\":[\"java.lang.String\"]}"}) {
            JsonParserException exception = Assert.assertThrows(JsonParserException.class, () -> ConfigurationParserTestHelper.parseLambdaDescriptor(method));
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("Missing attribute(s) [parameterTypes] in method descriptor"));
        }
    }

    @Test
    public void lambdaDeclaringMethodAcceptsParameterTypes() throws IOException {
        Assert.assertNotNull(ConfigurationParserTestHelper.parseLambdaDescriptor("{\"name\":\"make\",\"parameterTypes\":[]}"));
        Assert.assertNotNull(ConfigurationParserTestHelper.parseLambdaDescriptor("{\"name\":\"make\",\"parameterTypes\":[\"java.lang.String\"]}"));
    }
}

final class ConfigurationParserTestHelper extends ConfigurationParser {
    private ConfigurationParserTestHelper() {
        super(EnumSet.noneOf(ConfigurationParserOption.class));
    }

    static Object parseLambdaDescriptor(String method) throws IOException {
        String json = """
                        {"lambda": {"declaringClass": "Example", "declaringMethod": %s, "interfaces": ["java.lang.Runnable"]}}
                        """.formatted(method);
        Object type = new JsonParser(new StringReader(json)).parse();
        return new ConfigurationParserTestHelper().parseTypeContents(type).orElseThrow();
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        throw new UnsupportedOperationException();
    }
}
