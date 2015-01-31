/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.stackslotalloc;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.stackslotalloc.StackInterval.*;

public class StackUsePosList {

    LinkedList<Integer> usePosList;
    LinkedList<UseType> typeList;

    StackUsePosList() {
        this.usePosList = new LinkedList<>();
        this.typeList = new LinkedList<>();
    }

    public int size() {
        return usePosList.size();
    }

    public int usePos(int i) {
        return usePosList.get(i);
    }

    public void add(int opId, UseType type) {
        if (usePosList.isEmpty() || opId <= usePosList.getLast()) {
            usePosList.add(opId);
            typeList.add(type);
        } else if (opId >= usePosList.getFirst()) {
            usePosList.addFirst(opId);
            typeList.addFirst(type);
        } else {
            int size = usePosList.size();

            assert size >= 2 : "Should be handled by the cases above";
            assert size == typeList.size() : "types and use pos out of sync";

            ListIterator<Integer> posIt = usePosList.listIterator(size - 1);
            ListIterator<UseType> typeIt = typeList.listIterator(size - 1);

            // we start with size-2 because we know it will not inserted at the end
            while (posIt.hasPrevious()) {
                assert posIt.nextIndex() == typeIt.nextIndex();
                int current = posIt.previous();

                if (current >= opId) {
                    posIt.next();
                    posIt.add(opId);
                    typeIt.add(type);
                    assert verify();
                    return;
                }
                typeIt.previous();
            }
            GraalInternalError.shouldNotReachHere(String.format("Unable to insert %d into %s", opId, usePosList));
        }
    }

    public UseType getType(int i) {
        return typeList.get(i);
    }

    @Override
    public String toString() {
        return usePosList.toString() + System.lineSeparator() + typeList.toString();
    }

    public boolean verify() {
        int prev = -1;
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int current = usePosList.get(i);
            assert prev <= current : String.format("use positions not sorted: %d after %d", current, prev);
            prev = current;
        }
        return true;
    }

}
