/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.IsolateEnterStub;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.None;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

@TargetClass(java.lang.StackWalker.class)
@Substitute
public final class Target_java_lang_StackWalker {

    @Alias @TargetElement(name="options") @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = HashSet.class)
    private final Set<StackWalker.Option> options;

    @Substitute
    Target_java_lang_StackWalker(EnumSet<StackWalker.Option> options) {
        this.options = options;
    }

    @Substitute
    @AlwaysInline("Avoid virtual call to consumer")
    public void forEach(Consumer<? super StackFrame> action) {
        boolean includeHidden = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
        boolean includeReflect = includeHidden || options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        Pointer sp = KnownIntrinsics.readCallerStackPointer();
        CodePointer ip = KnownIntrinsics.readReturnAddress();
        JavaStackWalker.initWalk(walk, sp, ip);
        if (JavaStackWalker.startWalk(walk)) do {
            DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkDeoptimized(walk.getSP());
            if (deoptimizedFrame != null) {
                for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                    FrameInfoQueryResult frameInfo = frame.getFrameInfo();
                    String className = frameInfo.getSourceClassName();
                    if (includeHidden || ! StackWalker_Util.isHidden(className) || includeReflect || ! StackWalker_Util.isReflect(className)) {
                        action.accept(new StackFrameImpl(frameInfo));
                    }
                }
            } else {
                CodeInfoQueryResult codeInfo = CodeInfoTable.lookupCodeInfoQueryResult(ip);
                for (FrameInfoQueryResult frameInfo = codeInfo.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
                    String className = frameInfo.getSourceClassName();
                    if (includeHidden || ! StackWalker_Util.isHidden(className) || includeReflect || ! StackWalker_Util.isReflect(className)) {
                        action.accept(new StackFrameImpl(frameInfo));
                    }
                }
            }
        } while (JavaStackWalker.continueWalk(walk));
    }

    @Substitute
    @AlwaysInline("Avoid virtual call to function") // though streams are so heavy, does it matter?
    public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        final Pointer sp = KnownIntrinsics.readCallerStackPointer();
        final CodePointer ip = KnownIntrinsics.readReturnAddress();
        JavaStackWalker.initWalk(walk, sp, ip);
        final StackIterator iter = new StackIterator(walk, options);
        try {
            return function.apply(StreamSupport.stream(Spliterators.spliterator(iter, 0L, Spliterator.NONNULL), false));
        } finally {
            iter.invalidate();
        }
    }

    @Substitute
    public Class<?> getCallerClass() {
        throw new UnsupportedOperationException();
    }

    @Substitute
    public static Target_java_lang_StackWalker getInstance() {
        return StackWalkerHolder.PLAIN;
    }

    @Substitute
    public static Target_java_lang_StackWalker getInstance(StackWalker.Option option) {
        if (option == StackWalker.Option.RETAIN_CLASS_REFERENCE) {
            throw new UnsupportedOperationException(option.toString());
        } else if (option == StackWalker.Option.SHOW_HIDDEN_FRAMES) {
            return StackWalkerHolder.SHF;
        } else if (option == StackWalker.Option.SHOW_REFLECT_FRAMES) {
            return StackWalkerHolder.SRF;
        } else {
            return StackWalkerHolder.PLAIN;
        }
    }

    @Substitute
    public static Target_java_lang_StackWalker getInstance(Set<StackWalker.Option> option) {
        if (option.contains(StackWalker.Option.RETAIN_CLASS_REFERENCE)) {
            throw new UnsupportedOperationException(option.toString());
        } else if (option.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES)) {
            return StackWalkerHolder.SHF;
        } else if (option.contains(StackWalker.Option.SHOW_REFLECT_FRAMES)) {
            return StackWalkerHolder.SRF;
        } else {
            return StackWalkerHolder.PLAIN;
        }
    }

    @Substitute
    public static Target_java_lang_StackWalker getInstance(Set<StackWalker.Option> option, int estDepth) {
        return getInstance(option);
    }
}

final class StackWalker_Util {
    private StackWalker_Util() {}

    static boolean isReflect(final String nextClassName) {
        return Method.class.getName().equals(nextClassName) || Constructor.class.getName().equals(nextClassName);
        // TODO: MethodAccessor.class.isAssignableFrom(c)
        // TODO: ConstructorAccessor.class.isAssignableFrom(c)
        // TODO: c.getName().startsWith("java.lang.invoke.LambdaForm")
    }

    static boolean isHidden(final String nextClassName) {
        return IsolateEnterStub.class.getName().equals(nextClassName) || ImplicitExceptions.class.getName().equals(nextClassName);
    }
}

final class StackIterator implements Iterator<StackFrame> {
    final Thread thread = Thread.currentThread();
    final boolean includeReflect;
    final boolean includeHidden;
    boolean valid = true;
    JavaStackWalk walk;
    StackFrame next;
    Iterator<StackFrame> nested;

    StackIterator(final JavaStackWalk walk, final Set<StackWalker.Option> options) {
        includeHidden = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
        includeReflect = includeHidden || options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);
        this.walk = walk;
        if (JavaStackWalker.startWalk(walk)) {
            nested = getNested();
        }
    }

    public boolean hasNext() {
        checkThread();
        while (next == null) {
            while (nested == null) {
                Iterator<StackFrame> nested = getNested();
                if (nested == null) return false;
                this.nested = nested;
            }
            while (nested.hasNext()) {
                final StackWalker.StackFrame possibleNext = nested.next();
                final String nextClassName = possibleNext.getClassName();
                if ((includeHidden || ! StackWalker_Util.isHidden(nextClassName)) && (includeReflect || ! StackWalker_Util.isReflect(nextClassName))) {
                   this.next = possibleNext;
                   return true;
               } else {
                   // skip frame
               }
            }
            nested = null;
        }
        return true;
    }

    private Iterator<StackFrame> getNested() {
        if (! JavaStackWalker.continueWalk(walk)) {
            return null;
        }
        DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkDeoptimized(walk.getSP());
        if (deoptimizedFrame != null) {
            return new VirtualFrameIterator(deoptimizedFrame.getTopFrame());
        } else {
            return new FrameInfoIterator(CodeInfoTable.lookupCodeInfoQueryResult(walk.getIP()).getFrameInfo());
        }
    }

    public StackWalker.StackFrame next() {
        checkThread();
        if (! hasNext()) throw new NoSuchElementException();
        try {
            return next;
        } finally {
            next = null;
        }
    }

    void checkThread() {
        if (thread != Thread.currentThread()) throw new IllegalStateException("Invalid thread");
        if (! valid) throw new IllegalStateException("Stack traversal no longer valid");
    }

    void invalidate() {
        valid = false;
    }
}

final class VirtualFrameIterator implements Iterator<StackWalker.StackFrame> {

    DeoptimizedFrame.VirtualFrame frame;
    StackWalker.StackFrame next;

    VirtualFrameIterator(final DeoptimizedFrame.VirtualFrame frame) {
        this.frame = frame;
    }

    public boolean hasNext() {
        if (next == null) {
            if (frame == null) return false;
            next = new StackFrameImpl(frame.getFrameInfo());
            frame = frame.getCaller();
        }
        return true;
    }

    public StackWalker.StackFrame next() {
        if (! hasNext()) throw new NoSuchElementException();
        try {
            return next;
        } finally {
            next = null;
        }
    }
}

final class FrameInfoIterator implements Iterator<StackWalker.StackFrame> {

    FrameInfoQueryResult frameInfo;
    StackWalker.StackFrame next;

    FrameInfoIterator(final FrameInfoQueryResult frameInfo) {
        this.frameInfo = frameInfo;
    }

    public boolean hasNext() {
        if (next == null) {
            if (frameInfo == null) return false;
            next = new StackFrameImpl(frameInfo);
            frameInfo = frameInfo.getCaller();
        }
        return true;
    }

    public StackWalker.StackFrame next() {
        if (! hasNext()) throw new NoSuchElementException();
        try {
            return next;
        } finally {
            next = null;
        }
    }
}

final class StackWalkerHolder {
    static final Target_java_lang_StackWalker PLAIN = new Target_java_lang_StackWalker(EnumSet.noneOf(StackWalker.Option.class));
    static final Target_java_lang_StackWalker SRF = new Target_java_lang_StackWalker(EnumSet.of(StackWalker.Option.SHOW_REFLECT_FRAMES));
    static final Target_java_lang_StackWalker SHF = new Target_java_lang_StackWalker(EnumSet.of(StackWalker.Option.SHOW_HIDDEN_FRAMES));
}

final class StackFrameImpl implements StackWalker.StackFrame {
    private final FrameInfoQueryResult frameInfo;
    private StackTraceElement ste;

    StackFrameImpl(final FrameInfoQueryResult frameInfo) {
        this.frameInfo = frameInfo;
    }

    public String getClassName() {
        final String scn = frameInfo.getSourceClassName();
        return scn == null ? "" : scn;
    }

    public String getMethodName() {
        return frameInfo.getSourceMethodName();
    }

    public Class<?> getDeclaringClass() {
        return frameInfo.getSourceClass();
    }

    public int getByteCodeIndex() {
        return frameInfo.getBci();
    }

    public String getFileName() {
        return frameInfo.getSourceFileName();
    }

    public int getLineNumber() {
        return frameInfo.getSourceLineNumber();
    }

    public boolean isNativeMethod() {
        return frameInfo.isNativeMethod();
    }

    public StackTraceElement toStackTraceElement() {
        StackTraceElement ste = this.ste;
        if (ste == null) {
            this.ste = ste = frameInfo.getSourceReference();
        }
        return ste;
    }
}

@TargetClass(className = "java.lang.StackFrameInfo", onlyWith = JDK9OrLater.class)
@Delete
final class Target_java_lang_StackFrameInfo {}

@TargetClass(className = "java.lang.StackStreamFactory", onlyWith = JDK9OrLater.class)
@Delete
final class Target_java_lang_StackStreamFactory {}