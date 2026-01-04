/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Defines a callback for printing a JSON representation of array elements used by
 * {@link #printCollection(JsonWriter, Collection, Comparator, JsonPrinter)}.
 *
 * @param <T> the type of elements that will be printed.
 */
public interface JsonPrinter<T> {
    /**
     * Implements custom JSON formatting of {@code t} to {@code writer}.
     */
    void print(T t, JsonWriter writer) throws IOException;

    /**
     * Converts a collection of items to a JSON array, optionally sorted according to
     * {@code comparator}.
     *
     * @param writer the JSON writer to append characters to.
     * @param collection the collection to be printed.
     * @param comparator if non-null, {@code collection} will be sorted in ascending order using
     *            this comparator before printing.
     * @param elementPrinter will be used to print each element of the collection, allowing for
     *            custom printing.
     * @see JsonWriter#print(Object)
     */
    static <T> void printCollection(JsonWriter writer, Collection<T> collection, Comparator<T> comparator, JsonPrinter<T> elementPrinter) throws IOException {
        printCollection(writer, collection, comparator, elementPrinter, true, true);
    }

    /* Utility method to allow printing multiple collections into the same array */
    static <T> void printCollection(JsonWriter writer, Collection<T> collection, Comparator<T> comparator, JsonPrinter<T> elementPrinter, boolean arrayStart, boolean arrayEnd) throws IOException {
        if (collection.isEmpty() && arrayStart && arrayEnd) {
            writer.append("[]");
            return;
        }

        Collection<T> ordered = collection;
        if (comparator != null) {
            ordered = new ArrayList<>(collection);
            ((List<T>) ordered).sort(comparator);
        }

        if (arrayStart) {
            writer.appendArrayStart();
        }
        boolean separator = false;
        for (T t : ordered) {
            if (separator) {
                writer.appendSeparator();
            }
            elementPrinter.print(t, writer);
            separator = true;
        }
        if (arrayEnd) {
            writer.appendArrayEnd();
        }
    }
}
