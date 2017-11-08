/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.util.Queue;

import com.oracle.svm.driver.MacroOption.InvalidMacroException;
import com.oracle.svm.driver.MacroOption.MacroOptionKind;

class MacroOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    MacroOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    public boolean consume(Queue<String> args) {
        String headArg = args.peek();

        String polyglotPrefix = "--polyglot=";
        if (headArg.startsWith(polyglotPrefix)) {
            String languagesRaw = headArg.substring(polyglotPrefix.length());
            try {
                nativeImage.optionRegistry.enableOptions(languagesRaw.replace(',', ' '), MacroOptionKind.Language);
            } catch (InvalidMacroException e) {
                NativeImage.showError(e.getMessage());
            }
            args.poll();
            return true;
        }

        String toolsPrefix = "--tool.";
        if (headArg.startsWith(toolsPrefix)) {
            String toolString = headArg.substring(toolsPrefix.length());
            try {
                nativeImage.optionRegistry.enableOptions(toolString, MacroOptionKind.Tool);
            } catch (InvalidMacroException e) {
                NativeImage.showError(e.getMessage());
            }
            args.poll();
            return true;
        }

        String langPrefix = "--";
        if (headArg.startsWith("--")) {
            String langString = headArg.substring(langPrefix.length());
            try {
                nativeImage.optionRegistry.enableOptions(langString, MacroOptionKind.Language);
                args.poll();
                return true;
            } catch (InvalidMacroException e) {
                NativeImage.showError(e.getMessage());
            }
        }

        return false;
    }
}
