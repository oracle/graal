/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

@CoreClass(name = "Dir")
public abstract class DirNodes {

    @CoreMethod(names = "[]", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class GlobNode extends CoreMethodNode {

        public GlobNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GlobNode(GlobNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray glob(RubyString glob) {
            return glob(getContext(), glob.toString());
        }

        @SlowPath
        private static RubyArray glob(final RubyContext context, String glob) {
            /*
             * Globbing is quite complicated. We've implemented a subset of the functionality that
             * satisfies MSpec, but it will likely break for anyone else.
             */

            context.implementationMessage("globbing %s", glob);

            String absoluteGlob;

            if (!glob.startsWith("/")) {
                absoluteGlob = new File(".", glob).getAbsolutePath().toString();
            } else {
                absoluteGlob = glob;
            }

            // Get the first star

            final int firstStar = absoluteGlob.indexOf('*');
            assert firstStar >= 0;

            // Walk back from that to the first / before that star

            int prefixLength = firstStar;

            while (prefixLength > 0 && absoluteGlob.charAt(prefixLength) == File.separatorChar) {
                prefixLength--;
            }

            final String prefix = absoluteGlob.substring(0, prefixLength - 1);

            final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + absoluteGlob.substring(prefixLength));

            final RubyArray array = new RubyArray(context.getCoreLibrary().getArrayClass());

            try {
                Files.walkFileTree(FileSystems.getDefault().getPath(prefix), new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (matcher.matches(file)) {
                            array.push(context.makeString(file.toString()));
                        }

                        return FileVisitResult.CONTINUE;
                    }

                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return array;
        }

    }

    @CoreMethod(names = "chdir", isModuleMethod = true, needsSelf = false, needsBlock = true, minArgs = 1, maxArgs = 1)
    public abstract static class ChdirNode extends YieldingCoreMethodNode {

        public ChdirNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ChdirNode(ChdirNode prev) {
            super(prev);
        }

        @Specialization
        public Object chdir(VirtualFrame frame, RubyString path, RubyProc block) {
            final RubyContext context = getContext();

            final String previous = context.getCurrentDirectory();
            context.setCurrentDirectory(path.toString());

            if (block != null) {
                try {
                    return yield(frame, block, path);
                } finally {
                    context.setCurrentDirectory(previous);
                }
            } else {
                return 0;
            }
        }

    }

    @CoreMethod(names = {"exist?", "exists?"}, isModuleMethod = true, needsSelf = false, maxArgs = 1)
    public abstract static class ExistsNode extends CoreMethodNode {

        public ExistsNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExistsNode(ExistsNode prev) {
            super(prev);
        }

        @Specialization
        public boolean exists(RubyString path) {
            return new File(path.toString()).isDirectory();
        }

    }

    @CoreMethod(names = "pwd", isModuleMethod = true, needsSelf = false, maxArgs = 0)
    public abstract static class PwdNode extends CoreMethodNode {

        public PwdNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PwdNode(PwdNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString pwd() {
            return getContext().makeString(getContext().getCurrentDirectory());
        }

    }

}
