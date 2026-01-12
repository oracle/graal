/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.parser;

import at.ssw.visualizer.modelimpl.CompilationModelImpl;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;

/**
 *
 * @author Christian Wimmer
 */
public class CompilationParser {
    public static String parseInputFile(String fileName, CompilationModelImpl compilationData) {
        ProgressHandle progressHandle = ProgressHandleFactory.createHandle("Parsing input file \"" + fileName + "\"", new CancelParsing());
        Scanner scanner = null;
        try {
            scanner = new Scanner(fileName, progressHandle);
            Parser parser = new Parser(scanner);
            parser.setCompilationModel(compilationData);
            parser.Parse();

            if (parser.hasErrors()) {
                return parser.getErrors();
            } else {
                return null;
            }
        } catch (UserCanceledError ex) {
            // user canceled parsing, so report no error
            return null;
        } catch (Error ex) {
            return ex.getMessage();
        } catch (Throwable ex) {
            // catch everything else that might happen
            return ex.getClass().getName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        } finally {
            progressHandle.finish();
        }
    }

    private static class CancelParsing implements Cancellable {
        public boolean cancel() {
            throw new UserCanceledError();
        }
    }

    private static class UserCanceledError extends Error {
    }
}
