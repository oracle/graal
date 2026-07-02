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
package com.oracle.svm.test.preserve.incomplete;

import static org.junit.Assert.assertEquals;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import org.junit.Test;
import org.objectweb.asm.Type;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:Preserve=package=com.oracle.svm.test.preserve.incomplete",
                "-H:-UnlockExperimentalVMOptions",
})
public class PreserveIncompleteExternalizableTest {

    public static final class IncompleteExternalizable implements Externalizable {
        private static final long serialVersionUID = 1L;

        public IncompleteExternalizable() {
        }

        public IncompleteExternalizable(Type missing) {
            Objects.requireNonNull(missing);
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        }
    }

    @Test
    public void preserveIncompleteExternalizable() {
        assertEquals("com.oracle.svm.test.preserve.incomplete.PreserveIncompleteExternalizableTest$IncompleteExternalizable", IncompleteExternalizable.class.getName());
    }
}
