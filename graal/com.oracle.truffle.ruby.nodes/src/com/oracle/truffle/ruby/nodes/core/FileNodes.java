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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

@CoreClass(name = "File")
public abstract class FileNodes {

    @CoreMethod(names = "absolute_path", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class AbsolutePathNode extends CoreMethodNode {

        public AbsolutePathNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public AbsolutePathNode(AbsolutePathNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString absolutePath(RubyString path) {
            return getContext().makeString(new File(path.toString()).getAbsolutePath());
        }

    }

    @CoreMethod(names = "close", maxArgs = 0)
    public abstract static class CloseNode extends CoreMethodNode {

        public CloseNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CloseNode(CloseNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder close(RubyFile file) {
            file.close();
            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "directory?", isModuleMethod = true, needsSelf = false, maxArgs = 1)
    public abstract static class DirectoryNode extends CoreMethodNode {

        public DirectoryNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DirectoryNode(DirectoryNode prev) {
            super(prev);
        }

        @Specialization
        public boolean directory(RubyString path) {
            return new File(path.toString()).isDirectory();
        }

    }

    @CoreMethod(names = "dirname", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class DirnameNode extends CoreMethodNode {

        public DirnameNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DirnameNode(DirnameNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString dirname(RubyString path) {
            final String parent = new File(path.toString()).getParent();

            if (parent == null) {
                return getContext().makeString(".");
            } else {
                return getContext().makeString(parent);
            }
        }

    }

    @CoreMethod(names = "each_line", needsBlock = true, maxArgs = 0)
    public abstract static class EachLineNode extends YieldingCoreMethodNode {

        public EachLineNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EachLineNode(EachLineNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder eachLine(VirtualFrame frame, RubyFile file, RubyProc block) {
            final RubyContext context = getContext();

            // TODO(cs): this buffered reader may consume too much

            final BufferedReader lineReader = new BufferedReader(file.getReader());

            while (true) {
                String line;

                try {
                    line = lineReader.readLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (line == null) {
                    break;
                }

                yield(frame, block, context.makeString(line));
            }

            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = {"exist?", "exists?"}, isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class ExistsNode extends CoreMethodNode {

        public ExistsNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExistsNode(ExistsNode prev) {
            super(prev);
        }

        @Specialization
        public boolean exists(RubyString path) {
            return new File(path.toString()).isFile();
        }

    }

    @CoreMethod(names = "executable?", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class ExecutableNode extends CoreMethodNode {

        public ExecutableNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExecutableNode(ExecutableNode prev) {
            super(prev);
        }

        @Specialization
        public boolean executable(RubyString path) {
            return new File(path.toString()).canExecute();
        }

    }

    @CoreMethod(names = "expand_path", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 2)
    public abstract static class ExpandPathNode extends CoreMethodNode {

        public ExpandPathNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExpandPathNode(ExpandPathNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString expandPath(RubyString path, @SuppressWarnings("unused") UndefinedPlaceholder dir) {
            return getContext().makeString(RubyFile.expandPath(path.toString()));
        }

        @Specialization
        public RubyString expandPath(RubyString path, RubyString dir) {
            return getContext().makeString(RubyFile.expandPath(path.toString(), dir.toString()));
        }

    }

    @CoreMethod(names = "file?", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class FileNode extends CoreMethodNode {

        public FileNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FileNode(FileNode prev) {
            super(prev);
        }

        @Specialization
        public boolean file(RubyString path) {
            return new File(path.toString()).isFile();
        }

    }

    @CoreMethod(names = "join", isModuleMethod = true, needsSelf = false, isSplatted = true)
    public abstract static class JoinNode extends CoreMethodNode {

        public JoinNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public JoinNode(JoinNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString join(Object[] parts) {
            return getContext().makeString(RubyArray.join(parts, File.separator));
        }
    }

    @CoreMethod(names = "open", isModuleMethod = true, needsSelf = false, needsBlock = true, minArgs = 2, maxArgs = 2)
    public abstract static class OpenNode extends YieldingCoreMethodNode {

        public OpenNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public OpenNode(OpenNode prev) {
            super(prev);
        }

        @Specialization
        public Object open(VirtualFrame frame, RubyString fileName, RubyString mode, RubyProc block) {
            final RubyFile file = RubyFile.open(getContext(), fileName.toString(), mode.toString());

            if (block != null) {
                try {
                    yield(frame, block, file);
                } finally {
                    file.close();
                }
            }

            return file;
        }

    }

    @CoreMethod(names = "write", maxArgs = 0)
    public abstract static class WriteNode extends CoreMethodNode {

        public WriteNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public WriteNode(WriteNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder write(RubyFile file, RubyString string) {
            try {
                final Writer writer = file.getWriter();
                writer.write(string.toString());
                writer.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return NilPlaceholder.INSTANCE;
        }

    }

}
