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
import java.math.*;
import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.call.*;
import com.oracle.truffle.ruby.nodes.cast.*;
import com.oracle.truffle.ruby.nodes.control.*;
import com.oracle.truffle.ruby.nodes.literal.*;
import com.oracle.truffle.ruby.nodes.yield.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.objects.*;
import com.oracle.truffle.ruby.runtime.subsystems.*;

@CoreClass(name = "Kernel")
public abstract class KernelNodes {

    @CoreMethod(names = "Array", isModuleMethod = true, needsSelf = false, isSplatted = true)
    public abstract static class ArrayNode extends CoreMethodNode {

        public ArrayNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ArrayNode(ArrayNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray array(Object[] args) {
            if (args.length == 1 && args[0] instanceof RubyArray) {
                return (RubyArray) args[0];
            } else {
                return RubyArray.specializedFromObjects(getContext().getCoreLibrary().getArrayClass(), args);
            }
        }

    }

    @CoreMethod(names = "at_exit", isModuleMethod = true, needsSelf = false, needsBlock = true, maxArgs = 0)
    public abstract static class AtExitNode extends CoreMethodNode {

        public AtExitNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public AtExitNode(AtExitNode prev) {
            super(prev);
        }

        @Specialization
        public Object atExit(RubyProc block) {
            getContext().getAtExitManager().add(block);
            return NilPlaceholder.INSTANCE;
        }
    }

    @CoreMethod(names = "binding", isModuleMethod = true, needsSelf = true, maxArgs = 0)
    public abstract static class BindingNode extends CoreMethodNode {

        public BindingNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BindingNode(BindingNode prev) {
            super(prev);
        }

        @Specialization
        public Object binding(VirtualFrame frame, Object self) {
            return new RubyBinding(getContext().getCoreLibrary().getBindingClass(), self, frame.getCaller().unpack().materialize());
        }
    }

    @CoreMethod(names = "block_given?", isModuleMethod = true, needsSelf = false, maxArgs = 0)
    public abstract static class BlockGivenNode extends CoreMethodNode {

        public BlockGivenNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BlockGivenNode(BlockGivenNode prev) {
            super(prev);
        }

        @Specialization
        public boolean blockGiven(VirtualFrame frame) {
            return frame.getCaller().unpack().getArguments(RubyArguments.class).getBlock() != null;
        }
    }

    // TODO(CS): should hide this in a feature

    @CoreMethod(names = "callcc", isModuleMethod = true, needsSelf = false, needsBlock = true, maxArgs = 0)
    public abstract static class CallccNode extends CoreMethodNode {

        public CallccNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CallccNode(CallccNode prev) {
            super(prev);
        }

        @Specialization
        public Object callcc(RubyProc block) {
            final RubyContext context = getContext();

            if (block == null) {
                // TODO(CS): should really have acceptsBlock and needsBlock to do this automatically
                throw new RaiseException(context.getCoreLibrary().localJumpError("no block given"));
            }

            final RubyContinuation continuation = new RubyContinuation(context.getCoreLibrary().getContinuationClass());
            return continuation.enter(block);
        }
    }

    @CoreMethod(names = "catch", isModuleMethod = true, needsSelf = false, needsBlock = true, minArgs = 1, maxArgs = 1)
    public abstract static class CatchNode extends YieldingCoreMethodNode {

        public CatchNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CatchNode(CatchNode prev) {
            super(prev);
        }

        @Specialization
        public Object doCatch(VirtualFrame frame, Object tag, RubyProc block) {
            try {
                return yield(frame, block);
            } catch (ThrowException e) {
                if (e.getTag().equals(tag)) {
                    // TODO(cs): unset rather than set to Nil?
                    getContext().getCoreLibrary().getGlobalVariablesObject().setInstanceVariable("$!", NilPlaceholder.INSTANCE);
                    return e.getValue();
                } else {
                    throw e;
                }
            }
        }
    }

    @CoreMethod(names = "eval", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 2)
    public abstract static class EvalNode extends CoreMethodNode {

        public EvalNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EvalNode(EvalNode prev) {
            super(prev);
        }

        @Specialization
        public Object eval(RubyString source, @SuppressWarnings("unused") UndefinedPlaceholder binding) {
            return getContext().eval(source.toString());
        }

        @Specialization
        public Object eval(RubyString source, RubyBinding binding) {
            return getContext().eval(source.toString(), binding);
        }

    }

    @CoreMethod(names = "exec", isModuleMethod = true, needsSelf = false, minArgs = 1, isSplatted = true)
    public abstract static class ExecNode extends CoreMethodNode {

        public ExecNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExecNode(ExecNode prev) {
            super(prev);
        }

        @Specialization
        public Object require(Object[] args) {
            final String[] commandLine = new String[args.length];

            for (int n = 0; n < args.length; n++) {
                commandLine[n] = args[n].toString();
            }

            exec(getContext(), commandLine);

            return null;
        }

        @SlowPath
        private static void exec(RubyContext context, String[] commandLine) {
            context.implementationMessage("starting child process to simulate exec: ");

            for (int n = 0; n < commandLine.length; n++) {
                if (n > 0) {
                    System.err.print(" ");
                }

                System.err.print(commandLine[n]);
            }

            final ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.inheritIO();

            final RubyHash env = (RubyHash) context.getCoreLibrary().getObjectClass().lookupConstant("ENV");

            for (Map.Entry<Object, Object> entry : env.getMap().entrySet()) {
                builder.environment().put(entry.getKey().toString(), entry.getValue().toString());
            }

            Process process;

            try {
                process = builder.start();
            } catch (IOException e) {
                // TODO(cs): proper Ruby exception
                throw new RuntimeException(e);
            }

            int exitCode;

            while (true) {
                try {
                    exitCode = process.waitFor();
                    break;
                } catch (InterruptedException e) {
                    continue;
                }
            }

            context.implementationMessage("child process simulating exec finished");

            System.exit(exitCode);
        }

    }

    @CoreMethod(names = "exit", isModuleMethod = true, needsSelf = false, minArgs = 0, maxArgs = 1)
    public abstract static class ExitNode extends CoreMethodNode {

        public ExitNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExitNode(ExitNode prev) {
            super(prev);
        }

        @Specialization
        public Object exit(@SuppressWarnings("unused") UndefinedPlaceholder exitCode) {
            getContext().shutdown();
            System.exit(0);
            return null;
        }

        @Specialization
        public Object exit(int exitCode) {
            getContext().shutdown();
            System.exit(exitCode);
            return null;
        }

    }

    @CoreMethod(names = "gets", isModuleMethod = true, needsSelf = false, maxArgs = 0)
    public abstract static class GetsNode extends CoreMethodNode {

        public GetsNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GetsNode(GetsNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString gets(VirtualFrame frame) {
            final RubyContext context = getContext();

            final ThreadManager threadManager = context.getThreadManager();

            RubyString line;

            try {
                final RubyThread runningThread = threadManager.leaveGlobalLock();

                try {
                    line = context.makeString(context.getConfiguration().getInputReader().readLine(""));
                } finally {
                    threadManager.enterGlobalLock(runningThread);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Set the local variable $_ in the caller

            final Frame unpacked = frame.getCaller().unpack();
            final FrameSlot slot = unpacked.getFrameDescriptor().findFrameSlot("$_");

            if (slot != null) {
                unpacked.setObject(slot, line);
            }

            return line;
        }
    }

    @CoreMethod(names = "Integer", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class IntegerNode extends CoreMethodNode {

        @Child protected DispatchHeadNode toInt;

        public IntegerNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            toInt = adoptChild(new DispatchHeadNode(context, getSourceSection(), "to_int", false));
        }

        public IntegerNode(IntegerNode prev) {
            super(prev);
            toInt = adoptChild(prev.toInt);
        }

        @Specialization
        public int integer(int value) {
            return value;
        }

        @Specialization
        public BigInteger integer(BigInteger value) {
            return value;
        }

        @Specialization
        public int integer(double value) {
            return (int) value;
        }

        @Specialization
        public Object integer(RubyString value) {
            return value.toInteger();
        }

        @Specialization
        public Object integer(VirtualFrame frame, Object value) {
            return toInt.dispatch(frame, value, null);
        }

    }

    @CoreMethod(names = "lambda", isModuleMethod = true, needsBlock = true, maxArgs = 0)
    public abstract static class LambdaNode extends CoreMethodNode {

        public LambdaNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LambdaNode(LambdaNode prev) {
            super(prev);
        }

        @Specialization
        public RubyProc proc(Object self, RubyProc block) {
            return new RubyProc(getContext().getCoreLibrary().getProcClass(), RubyProc.Type.LAMBDA, self, block, block.getMethod());

        }
    }

    @CoreMethod(names = "load", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class LoadNode extends CoreMethodNode {

        public LoadNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LoadNode(LoadNode prev) {
            super(prev);
        }

        @Specialization
        public boolean load(RubyString file) {
            getContext().loadFile(file.toString());
            return true;
        }
    }

    @CoreMethod(names = "loop", isModuleMethod = true, needsSelf = false, maxArgs = 0)
    public abstract static class LoopNode extends CoreMethodNode {

        @Child protected WhileNode whileNode;

        public LoopNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            whileNode = adoptChild(new WhileNode(context, sourceSection, BooleanCastNodeFactory.create(context, sourceSection, new BooleanLiteralNode(context, sourceSection, true)), new YieldNode(
                            context, getSourceSection(), new RubyNode[]{})));
        }

        public LoopNode(LoopNode prev) {
            super(prev);
            whileNode = adoptChild(prev.whileNode);
        }

        @Specialization
        public Object loop(VirtualFrame frame) {
            return whileNode.execute(frame);
        }
    }

    @CoreMethod(names = "print", isModuleMethod = true, needsSelf = false, isSplatted = true)
    public abstract static class PrintNode extends CoreMethodNode {

        public PrintNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PrintNode(PrintNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder print(Object[] args) {
            final RubyContext context = getContext();
            final ThreadManager threadManager = context.getThreadManager();

            final RubyThread runningThread = threadManager.leaveGlobalLock();

            try {
                for (Object arg : args) {
                    /*
                     * TODO(cs): If it's a RubyString and made up of bytes, just write the bytes out
                     * - using toString will mess up the encoding. We need to stop using toString
                     * everywhere, and write our own bytes, possibly using JRuby's library for this.
                     */

                    if (arg instanceof RubyString && !((RubyString) arg).isFromJavaString()) {
                        try {
                            context.getConfiguration().getStandardOut().write(((RubyString) arg).getBytes());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        context.getConfiguration().getStandardOut().print(arg);
                    }
                }
            } finally {
                threadManager.enterGlobalLock(runningThread);
            }

            return NilPlaceholder.INSTANCE;
        }
    }

    @CoreMethod(names = "printf", isModuleMethod = true, needsSelf = false, isSplatted = true)
    public abstract static class PrintfNode extends CoreMethodNode {

        public PrintfNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PrintfNode(PrintfNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder printf(Object[] args) {
            final RubyContext context = getContext();
            final ThreadManager threadManager = context.getThreadManager();

            if (args.length > 0) {
                final String format = ((RubyString) args[0]).toString();
                final List<Object> values = Arrays.asList(args).subList(1, args.length);

                final RubyThread runningThread = threadManager.leaveGlobalLock();

                try {
                    StringFormatter.format(context.getConfiguration().getStandardOut(), format, values);
                } finally {
                    threadManager.enterGlobalLock(runningThread);
                }
            }

            return NilPlaceholder.INSTANCE;
        }
    }

    /*
     * Kernel#pretty_inspect is normally part of stdlib, in pp.rb, but we aren't able to execute
     * that file yet. Instead we implement a very simple version here, which is the solution
     * suggested by RubySpec.
     */

    @CoreMethod(names = "pretty_inspect", maxArgs = 0)
    public abstract static class PrettyInspectNode extends CoreMethodNode {

        @Child protected DispatchHeadNode toS;

        public PrettyInspectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            toS = adoptChild(new DispatchHeadNode(context, getSourceSection(), "to_s", false));
        }

        public PrettyInspectNode(PrettyInspectNode prev) {
            super(prev);
            toS = adoptChild(prev.toS);
        }

        @Specialization
        public Object prettyInspect(VirtualFrame frame, Object self) {
            return toS.dispatch(frame, self, null);

        }
    }

    @CoreMethod(names = "proc", isModuleMethod = true, needsBlock = true, maxArgs = 0)
    public abstract static class ProcNode extends CoreMethodNode {

        public ProcNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ProcNode(ProcNode prev) {
            super(prev);
        }

        @Specialization
        public RubyProc proc(Object self, RubyProc block) {
            return new RubyProc(getContext().getCoreLibrary().getProcClass(), RubyProc.Type.PROC, self, block, block.getMethod());

        }
    }

    @CoreMethod(names = "puts", isModuleMethod = true, needsSelf = false, isSplatted = true)
    public abstract static class PutsNode extends CoreMethodNode {

        public PutsNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PutsNode(PutsNode prev) {
            super(prev);
        }

        @ExplodeLoop
        @Specialization
        public NilPlaceholder puts(Object[] args) {
            final RubyContext context = getContext();
            final ThreadManager threadManager = context.getThreadManager();
            final PrintStream standardOut = context.getConfiguration().getStandardOut();

            final RubyThread runningThread = threadManager.leaveGlobalLock();

            try {
                if (args.length == 0) {
                    standardOut.println();
                } else {
                    for (int n = 0; n < args.length; n++) {
                        puts(context, standardOut, args[n]);
                    }
                }
            } finally {
                threadManager.enterGlobalLock(runningThread);
            }

            return NilPlaceholder.INSTANCE;
        }

        @SlowPath
        private void puts(RubyContext context, PrintStream standardOut, Object value) {
            if (value instanceof RubyArray) {
                final RubyArray array = (RubyArray) value;

                for (int n = 0; n < array.size(); n++) {
                    puts(context, standardOut, array.get(n));
                }
            } else {
                // TODO(CS): slow path send
                standardOut.println(context.getCoreLibrary().box(value).send("to_s", null));
            }
        }

    }

    @CoreMethod(names = "raise", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 2)
    public abstract static class RaiseNode extends CoreMethodNode {

        @Child protected DispatchHeadNode initialize;

        public RaiseNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            initialize = adoptChild(new DispatchHeadNode(context, getSourceSection(), "initialize", false));
        }

        public RaiseNode(RaiseNode prev) {
            super(prev);
            initialize = adoptChild(prev.initialize);
        }

        @Specialization(order = 1)
        public Object raise(VirtualFrame frame, RubyString message, @SuppressWarnings("unused") UndefinedPlaceholder undefined) {
            return raise(frame, getContext().getCoreLibrary().getRuntimeErrorClass(), message);
        }

        @Specialization(order = 2)
        public Object raise(VirtualFrame frame, RubyClass exceptionClass, @SuppressWarnings("unused") UndefinedPlaceholder undefined) {
            return raise(frame, exceptionClass, getContext().makeString(""));
        }

        @Specialization(order = 3)
        public Object raise(VirtualFrame frame, RubyClass exceptionClass, RubyString message) {
            final RubyBasicObject exception = exceptionClass.newInstance();
            initialize.dispatch(frame, exception, null, message);
            throw new RaiseException(exception);
        }

    }

    @CoreMethod(names = "require", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class RequireNode extends CoreMethodNode {

        public RequireNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RequireNode(RequireNode prev) {
            super(prev);
        }

        @Specialization
        public boolean require(RubyString feature) {
            try {
                getContext().getFeatureManager().require(feature.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return true;
        }
    }

    @CoreMethod(names = "set_trace_func", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class SetTraceFuncNode extends CoreMethodNode {

        public SetTraceFuncNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SetTraceFuncNode(SetTraceFuncNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder setTraceFunc(NilPlaceholder proc) {
            getContext().getTraceManager().setTraceProc(null);
            return proc;
        }

        @Specialization
        public RubyProc setTraceFunc(RubyProc proc) {
            getContext().getTraceManager().setTraceProc(proc);
            return proc;
        }

    }

    @CoreMethod(names = "String", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class StringNode extends CoreMethodNode {

        @Child protected DispatchHeadNode toS;

        public StringNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            toS = adoptChild(new DispatchHeadNode(context, getSourceSection(), "to_s", false));
        }

        public StringNode(StringNode prev) {
            super(prev);
            toS = adoptChild(prev.toS);
        }

        @Specialization
        public RubyString string(int value) {
            return getContext().makeString(Integer.toString(value));
        }

        @Specialization
        public RubyString string(BigInteger value) {
            return getContext().makeString(value.toString());
        }

        @Specialization
        public RubyString string(double value) {
            return getContext().makeString(Double.toString(value));
        }

        @Specialization
        public RubyString string(RubyString value) {
            return value;
        }

        @Specialization
        public Object string(VirtualFrame frame, Object value) {
            return toS.dispatch(frame, value, null);
        }

    }

    @CoreMethod(names = "sleep", isModuleMethod = true, needsSelf = false, maxArgs = 1)
    public abstract static class SleepNode extends CoreMethodNode {

        public SleepNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SleepNode(SleepNode prev) {
            super(prev);
        }

        @Specialization
        public double sleep(double duration) {
            final RubyContext context = getContext();

            final RubyThread runningThread = context.getThreadManager().leaveGlobalLock();

            try {
                final long start = System.nanoTime();

                try {
                    Thread.sleep((long) (duration * 1000));
                } catch (InterruptedException e) {
                    // Ignore interruption
                }

                final long end = System.nanoTime();

                return (end - start) / 1e9;
            } finally {
                context.getThreadManager().enterGlobalLock(runningThread);
            }
        }

        @Specialization
        public double sleep(int duration) {
            return sleep((double) duration);
        }

    }

    @CoreMethod(names = "throw", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 2)
    public abstract static class ThrowNode extends CoreMethodNode {

        public ThrowNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ThrowNode(ThrowNode prev) {
            super(prev);
        }

        @Specialization
        public Object doThrow(Object tag, UndefinedPlaceholder value) {
            return doThrow(tag, (Object) value);
        }

        @Specialization
        public Object doThrow(Object tag, Object value) {
            if (value instanceof UndefinedPlaceholder) {
                throw new ThrowException(tag, NilPlaceholder.INSTANCE);
            } else {
                throw new ThrowException(tag, value);
            }
        }

    }

}
