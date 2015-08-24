/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.source.Source;
import static com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.L1;
import com.oracle.truffle.api.vm.TruffleVM;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

public class ExceptionDuringParsingTest {
    public static Accessor API;

    @Test
    public void canGetAccessToOwnLanguageInstance() throws Exception {
        TruffleVM vm = TruffleVM.newVM().build();
        TruffleVM.Language language = vm.getLanguages().get(L1);
        assertNotNull("L1 language is defined", language);

        try {
            vm.eval(Source.fromText("parse=No, no, no!", "Fail on parsing").withMimeType(L1));
            fail("Exception thrown");
        } catch (IOException ex) {
            assertEquals(ex.getMessage(), "No, no, no!");
        }
    }
}
