/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

import org.junit.After;
import org.junit.Before;

public class IsMimeTypeSupportedTest {

    private static final String MIME_TYPE = "application/x-test-mime-type-supported";
    private PolyglotEngine vm;

    @Before
    public void create() {
        vm = PolyglotEngine.newBuilder().build();
    }

    @After
    public void dispose() {
        vm.dispose();
    }

    @Test
    public void isMimeSupported() {
        assertEquals(true, vm.eval(Source.newBuilder(MIME_TYPE).name("supported").mimeType(MIME_TYPE).build()).as(Boolean.class));
        assertEquals(false, vm.eval(Source.newBuilder("application/x-this-language-does-not-exist").name("unsupported").mimeType(MIME_TYPE).build()).as(Boolean.class));
    }

}
