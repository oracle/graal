/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

/**
 * Java enums are insufficient in that their ordinals have to be successive.
 * An Enumerable has an additional arbitrary int "value",
 * which may incur gaps between ordinal-successive Enumerables.
 * <p>
 * An Enumerator can be called upon to provide the respective Enumerable matching a given value.
 * <p>
 * See <a href="http://www.ejournal.unam.mx/cys/vol07-02/CYS07205.pdf">"Inheritance, Generics and Binary Methods in Java"</a>
 * for an explanation of how to interpret a recursive generic type.
 * <p>
 *
 * @see Enumerator
 */
public interface Enumerable<E extends Enum<E> & Enumerable<E>> extends Symbol {

    // We are merely declaring this method to lock in the same parameter type for the corresponding enumerator,
    // not for any actual use
    Enumerator<E> enumerator();

}
