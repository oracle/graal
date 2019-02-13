/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile.macho;

import java.util.HashSet;
import java.util.Map;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.macho.MachOObjectFile.LoadCommandKind;

class DyLdInfoCommand extends MachOObjectFile.LoadCommand {

    @SuppressWarnings("unused") private final MachOObjectFile owner;

    ExportTrieElement export;

    DyLdInfoCommand(String name, MachOObjectFile owner, boolean reqDyld) {
        owner.super(name, reqDyld ? LoadCommandKind.DYLD_INFO_ONLY : LoadCommandKind.DYLD_INFO);
        this.owner = owner;
        export = new ExportTrieElement(name, owner);
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
        // our size is fixed;
        // our content depends on the offset and size of the ExportTrieElement
        LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
        deps.add(BuildDependency.createOrGet(ourContent, decisions.get(export).getDecision(LayoutDecision.Kind.OFFSET)));
        deps.add(BuildDependency.createOrGet(ourContent, decisions.get(export).getDecision(LayoutDecision.Kind.SIZE)));
        return deps;
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return getWrittenSize(10 * 4); // content is a fixed 10 32-bit elements
    }

    @Override
    protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
        out.write4Byte(0); // rebase off
        out.write4Byte(0); // rebase size
        out.write4Byte(0); // bind off
        out.write4Byte(0); // bind size
        out.write4Byte(0); // weak_bind off
        out.write4Byte(0); // weak_bind size
        out.write4Byte(0); // lazy_bind off
        out.write4Byte(0); // lazy_bind size
        out.write4Byte((int) alreadyDecided.get(export).getDecidedValue(LayoutDecision.Kind.OFFSET));
        out.write4Byte((int) alreadyDecided.get(export).getDecidedValue(LayoutDecision.Kind.SIZE));
    }

}
