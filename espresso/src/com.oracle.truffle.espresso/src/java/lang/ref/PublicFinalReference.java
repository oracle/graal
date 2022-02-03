/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.ref;

import com.oracle.truffle.espresso.substitutions.EspressoReference;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_ref_Reference;

/**
 * Open {@link FinalReference}; implementation detail for a meta-circular implementation of
 * finalization. If the host supports FinalReference(s), so does the guest.
 *
 * <p>
 * This class is just a placeholder, not usable as-is. A modified version without the throwing
 * static initializer is injected in the boot class loader. The injected version subclasses
 * {@link FinalReference}.
 *
 * @see Target_java_lang_ref_Reference
 * @see com.oracle.truffle.espresso.FinalizationSupport
 * @see EspressoReference
 */
public abstract class PublicFinalReference<T> {

    // BEGIN CUT
    static {
        if (true /* ignore warning */) {
            throw new AssertionError("Forbidden class");
        }
    }
    // END CUT

    @SuppressWarnings("unused")
    public PublicFinalReference(T referent, ReferenceQueue<? super T> queue) {
    }

    public T get() {
        throw new AssertionError("Forbidden class");
    }

    public void clear() {
        throw new AssertionError("Forbidden class");
    }
}
