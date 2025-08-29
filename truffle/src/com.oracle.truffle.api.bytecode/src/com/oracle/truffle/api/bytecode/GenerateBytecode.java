/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.debug.BytecodeDebugListener;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Generates a bytecode interpreter using the Bytecode DSL. The Bytecode DSL automatically produces
 * an optimizing bytecode interpreter from a set of Node-like "operations". The following is an
 * example of a Bytecode DSL interpreter with a single {@code Add} operation.
 *
 * <pre>
 * &#64;GenerateBytecode(languageClass = MyLanguage.class)
 * public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode {
 *     &#64;Operation
 *     public static final class Add {
 *         &#64;Specialization
 *         public static int doInts(int lhs, int rhs) {
 *             return lhs + rhs;
 *         }
 *
 *         &#64;Specialization
 *         &#64;TruffleBoundary
 *         public static String doStrings(String lhs, String rhs) {
 *             return lhs + rhs;
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * The Bytecode DSL generates a node suffixed with {@code Gen} (e.g., {@code MyBytecodeRootNodeGen})
 * that contains (among other things) a full bytecode encoding, an optimizing interpreter, and a
 * {@code Builder} class to generate and validate bytecode automatically.
 * <p>
 * A node can opt in to additional features, like an {@link #enableUncachedInterpreter uncached
 * interpreter}, {@link #boxingEliminationTypes boxing elimination}, {@link #enableQuickening
 * quickened instructions}, and more. The fields of this annotation control which features are
 * included in the generated interpreter.
 * <p>
 * For information about using the Bytecode DSL, please consult the <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/BytecodeDSL.md">tutorial</a>.
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
@SuppressWarnings("dangling-doc-comments")
public @interface GenerateBytecode {
    /**
     * The {@link TruffleLanguage} class associated with this node.
     *
     * @since 24.2
     */
    Class<? extends TruffleLanguage<?>> languageClass();

    /**
     * Whether to generate an uncached interpreter.
     * <p>
     * The uncached interpreter improves start-up performance by executing
     * {@link com.oracle.truffle.api.dsl.GenerateUncached uncached} nodes instead of allocating and
     * executing cached (specializing) nodes. The node will transition to a specializing interpreter
     * after enough invocations/back-edges (as determined by {@link #defaultUncachedThreshold}).
     * <p>
     * To generate an uncached interpreter, all operations need to support uncached execution. If an
     * operation cannot easily support uncached execution, it can instead
     * {@link Operation#forceCached force a transition to cached} before the operation is executed
     * (this may limit the utility of the uncached interpreter).
     *
     * @since 24.2
     */
    boolean enableUncachedInterpreter() default false;

    /**
     * Sets the default number of times an uncached interpreter must be invoked/resumed or branch
     * backwards before transitioning to cached.
     * <p>
     * The default uncached threshold expression supports a subset of Java (see the
     * {@link com.oracle.truffle.api.dsl.Cached Cached} documentation). It should evaluate to an
     * int. It should be a positive value, {@code 0}, or {@code Integer.MIN_VALUE}. A threshold of
     * {@code 0} will cause each bytecode node to immediately transition to cached on first
     * invocation. A threshold of {@code Integer.MIN_VALUE} forces a bytecode node to stay uncached
     * (i.e., it will not transition to cached).
     * <p>
     * The default local value expression can be a constant literal (e.g., {@code "42"}), in which
     * case the value will be validated at build time. However, the expression can also refer to
     * static members of the bytecode root node (and validation is deferred to run time). The
     * following example declares a default threshold of 32 that can be overridden with a system
     * property:
     *
     * <pre>
     * &#64;GenerateBytecode(..., defaultUncachedThreshold = "DEFAULT_UNCACHED_THRESHOLD")
     * abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode {
     *
     *     static final int DEFAULT_UNCACHED_THRESHOLD = Integer.parseInt(System.getProperty("defaultUncachedThreshold", "32"));
     *
     *     // ...
     * }
     * </pre>
     *
     * Other expressions like static method calls are also possible. Note that instance members of
     * the root node cannot be bound with the default uncached threshold expression for efficiency
     * reasons.
     * <p>
     * To override this default threshold for a given bytecode node, an explicit threshold can be
     * set using {@link BytecodeNode#setUncachedThreshold}.
     * <p>
     * This field has no effect unless the uncached interpreter is
     * {@link #enableUncachedInterpreter() enabled}.
     *
     * @since 24.2
     */
    String defaultUncachedThreshold() default "16";

    /**
     * Whether the generated interpreter should support serialization and deserialization.
     * <p>
     * When serialization is enabled, the Bytecode DSL generates code to convert bytecode nodes to
     * and from a serialized byte array representation. The code effectively serializes the node's
     * execution data (bytecode, constants, etc.) and all of its non-transient fields.
     * <p>
     * The serialization logic is defined in static {@code serialize} and {@code deserialize}
     * methods of the generated root class. The generated {@link BytecodeRootNodes} class also
     * overrides {@link BytecodeRootNodes#serialize}.
     * <p>
     * This feature can be used to avoid the overhead of parsing source code on start up. Note that
     * serialization still incurs some overhead, as it does not trivially copy bytecode directly: in
     * order to validate the bytecode (balanced stack pointers, valid branches, etc.), serialization
     * encodes builder method calls and deserialization replays those calls.
     * <p>
     * Note that the generated {@code deserialize} method takes a {@link java.util.function.Supplier
     * Supplier<DataInput>} rather than a {@link java.io.DataInput} directly. The supplier should
     * produce a fresh {@link java.io.DataInput} each time because the input may be processed
     * multiple times (due to {@link BytecodeRootNodes#update(BytecodeConfig) reparsing}).
     *
     * @see com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer
     * @see com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer
     * @see <a href=
     *      "https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/SerializationTutorial.java">Serialization
     *      tutorial</a>
     * @since 24.2
     */
    boolean enableSerialization() default false;

    /**
     * Whether the generated interpreter should support Truffle tag instrumentation. When
     * instrumentation is enabled, the generated builder will define <code>beginTag(...)</code> and
     * <code>endTag(...)</code> methods that can be used to annotate the bytecode with
     * {@link com.oracle.truffle.api.instrumentation.Tag tags}. Truffle tag instrumentation also
     * allows you to specify implicit tagging using {@link Operation#tags()}. If tag instrumentation
     * is enabled all tagged operations will automatically handle and insert {@link ProbeNode
     * probes} from the Truffle instrumentation framework.
     * <p>
     * Only tags that are {@link ProvidedTags provided} by the specified {@link #languageClass()
     * Truffle language} can be used.
     *
     * @see #enableRootTagging()
     * @see #enableRootBodyTagging()
     * @since 24.2
     */
    boolean enableTagInstrumentation() default false;

    /**
     * Enables automatic root tagging if {@link #enableTagInstrumentation() instrumentation} is
     * enabled. Automatic root tagging automatically tags each root with {@link RootTag} if the
     * language {@link ProvidedTags provides} it.
     * <p>
     * Root tagging requires the probe to be notified before the {@link Prolog prolog} is executed.
     * Implementing this behavior manually is not trivial and not recommended. It is recommended to
     * use automatic root tagging. For inlining performed by the parser it may be useful to emit
     * custom {@link RootTag root} tag using the builder methods for inlined methods. This ensures
     * that tools can still work correctly for inlined calls.
     *
     * @since 24.2
     * @see #enableRootBodyTagging()
     */
    boolean enableRootTagging() default true;

    /**
     * Enables automatic root body tagging if {@link #enableTagInstrumentation() instrumentation} is
     * enabled. Automatic root body tagging automatically tags each root with {@link RootBodyTag} if
     * the language {@link ProvidedTags provides} it.
     *
     * @since 24.2
     * @see #enableRootTagging()
     */
    boolean enableRootBodyTagging() default true;

    /**
     * Allows to customize the {@link NodeLibrary} implementation that is used for tag
     * instrumentation. This option only makes sense if {@link #enableTagInstrumentation()} is set
     * to <code>true</code>.
     * <p>
     * Common use-cases when implementing a custom tag tree node library is required:
     * <ul>
     * <li>Allowing instruments to access the current receiver or function object.
     * <li>Implementing custom scopes for local variables instead of the default scope.
     * <li>Hiding certain local local variables or arguments from instruments.
     * </ul>
     * <p>
     * Minimal example of a tag node library:
     *
     * <pre>
     * &#64;ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
     * final class MyTagTreeNodeExports {
     *
     *     &#64;ExportMessage
     *     static boolean hasScope(TagTreeNode node, Frame frame) {
     *         return true;
     *     }
     *
     *     &#64;ExportMessage
     *     &#64;SuppressWarnings("unused")
     *     static Object getScope(TagTreeNode node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
     *         return new MyScope(node, frame);
     *     }
     * }
     * </pre>
     *
     * See the {@link NodeLibrary} javadoc for more details.
     *
     * @see TagTreeNode
     * @since 24.2
     */
    Class<?> tagTreeNodeLibrary() default TagTreeNodeExports.class;

    /**
     * Whether to use unsafe array accesses.
     * <p>
     * Unsafe accesses are faster, but they do not perform array bounds checks. This means it is
     * possible (though unlikely) for unsafe accesses to cause undefined behaviour. Undefined
     * behavior may only happen due to a bug in the Bytecode DSL implementation and not language
     * implementation code.
     *
     * @since 24.2
     */
    boolean allowUnsafe() default true;

    /**
     * Whether the generated interpreter should support coroutines via a {@code yield} operation.
     * <p>
     * The yield operation returns a {@link ContinuationResult} from the current point in execution.
     * The {@link ContinuationResult} saves the current state of the interpreter so that it can be
     * resumed at a later time. The yield and resume actions pass values, enabling communication
     * between the caller and callee.
     * <p>
     * Technical note: in theoretical terms, a {@link ContinuationResult} implements an asymmetric
     * stack-less coroutine.
     *
     * @see com.oracle.truffle.api.bytecode.ContinuationResult
     * @since 24.2
     */
    boolean enableYield() default false;

    /**
     * Enables materialized local accesses. Materialized local accesses allow a root node to access
     * the locals of any outer root nodes (root nodes created by enclosing {@code Root} operations)
     * in addition to its own locals. These accesses take the
     * {@link com.oracle.truffle.api.frame.MaterializedFrame materialized frame} containing the
     * local as an operand. Materialized local accesses can be used to implement closures and nested
     * functions with lexical scoping.
     * <p>
     * When materialized local accesses are enabled, the interpreter defines two additional
     * operations, {@code LoadLocalMaterialized} and {@code StoreLocalMaterialized}, which implement
     * the local accesses. Implementations can also use {@link MaterializedLocalAccessor}s to access
     * locals from user-defined operations.
     * <p>
     * Materialized local accesses can <i>only</i> be used where the local is
     * {@link #enableBlockScoping() in scope}. The bytecode generator guarantees that each
     * materialized access's local is in scope at the static location of the access, but since root
     * nodes can be called at any time, it is still possible to execute the root node (and thus
     * perform the access) when the local is out of scope, leading to unexpected behaviour (e.g.,
     * reading an incorrect local value). When the bytecode index is
     * {@link #storeBytecodeIndexInFrame() stored in the frame}, the interpreter will dynamically
     * validate each materialized access, throwing a runtime exception when the local is not in
     * scope. Thus, to diagnose issues with invalid materialized accesses, it is recommended to
     * enable storing the bytecode index in the frame.
     *
     * @since 24.2
     */
    boolean enableMaterializedLocalAccesses() default false;

    /**
     * Enables block scoping, which limits a local's lifetime to the lifetime of the enclosing
     * Block/Root operation. Block scoping is enabled by default. If this flag is set to
     * <code>false</code>, locals use root scoping, which keeps locals alive for the lifetime of the
     * root node (i.e., the entire invocation).
     * <p>
     * The value of this flag significantly changes the behaviour of local variables, so the value
     * of this flag should be decided relatively early in the development of a language.
     * <p>
     * When block scoping is enabled, all local variables are scoped to the closest enclosing
     * Block/Root operation. When a local variable's enclosing Block ends, it falls out of scope and
     * its value is automatically {@link Frame#clear(int) cleared} (or reset to a
     * {@link #defaultLocalValue() default value}, if provided). Locals scoped to the Root operation
     * are not cleared on exit. Block scoping allows the interpreter to reuse a frame index for
     * multiple locals that have disjoint lifetimes, which can reduce the frame size.
     * <p>
     * With block scoping, a different set of locals can be live at different bytecode indices. The
     * interpreter retains extra metadata to track the lifetimes of each local. The local accessor
     * methods of {@link BytecodeNode} (e.g., {@link BytecodeNode#getLocalValues(int, Frame)}) take
     * the current bytecode index as a parameter so that they can correctly compute the locals in
     * scope. These liveness computations can require extra computation, so accessing locals using
     * bytecode instructions or {@link LocalAccessor LocalAccessors} (which validate liveness at
     * parse time) is encouraged when possible. The bytecode index should be a
     * {@link CompilerAsserts#partialEvaluationConstant(boolean) partial evaluation constant} for
     * performance reasons. The lifetime of local variables can also be accessed through
     * introspection using {@link LocalVariable#getStartIndex()} and
     * {@link LocalVariable#getEndIndex()}.
     * <p>
     * When root scoping is enabled, all local variables are assigned a unique index in the frame
     * regardless of the current source location. They are never cleared, and frame indexes are not
     * reused. Consequently, the bytecode index parameter on the local accessor methods of
     * {@link BytecodeNode} has no effect. Root scoping does not retain additional liveness metadata
     * (which may be a useful footprint optimization); this also means
     * {@link LocalVariable#getStartIndex()} and {@link LocalVariable#getEndIndex()} methods do not
     * return lifetime data.
     * <p>
     * Root scoping is primarily intended for cases where the implemented language does not use
     * block scoping. It can also be useful if the default block scoping is not flexible enough and
     * custom scoping rules are needed.
     *
     * @since 24.2
     */
    boolean enableBlockScoping() default true;

    /**
     * Whether to generate quickened bytecodes for user-provided operations.
     * <p>
     * Quickened versions of instructions support a subset of the
     * {@link com.oracle.truffle.api.dsl.Specialization specializations} defined by an operation.
     * They can improve interpreted performance by reducing footprint and requiring fewer guards.
     * <p>
     * Quickened versions of operations can be specified using
     * {@link com.oracle.truffle.api.bytecode.ForceQuickening}. When an instruction re-specializes
     * itself, the interpreter attempts to automatically replace it with a quickened instruction.
     *
     * @since 24.2
     */
    boolean enableQuickening() default true;

    /**
     * Whether the generated interpreter should store the bytecode index (bci) in the frame.
     * <p>
     * By default, methods that compute location-dependent information (like
     * {@link BytecodeNode#getBytecodeLocation(com.oracle.truffle.api.frame.Frame, Node)}) must
     * follow {@link Node#getParent() Node parent} pointers and scan the bytecode to compute the
     * current bci, which is not suitable for the fast path. When this feature is enabled, an
     * implementation can use
     * {@link BytecodeNode#getBytecodeIndex(com.oracle.truffle.api.frame.Frame)} to obtain the bci
     * efficiently on the fast path and use it for location-dependent computations (e.g.,
     * {@link BytecodeNode#getBytecodeLocation(int)}).
     * <p>
     * Note that operations always have fast-path access to the bci using a bind parameter (e.g.,
     * {@code @Bind("$bytecodeIndex") int bci}); this feature should only be enabled for fast-path
     * bci access outside of the current operation (e.g., for closures or frame introspection).
     * Storing the bci in the frame increases frame size and requires additional frame writes, so it
     * can negatively affect performance.
     *
     * @since 24.2
     */
    boolean storeBytecodeIndexInFrame() default false;

    /**
     * Path to a file containing optimization decisions. This file is generated using tracing on a
     * representative corpus of code.
     * <p>
     * This feature is not yet supported.
     *
     * @see #forceTracing()
     * @since 24.2
     */
    // TODO GR-57220
    // String decisionsFile() default "";

    /**
     * Path to files with manually-provided optimization decisions. These files can be used to
     * encode optimizations that are not generated automatically via tracing.
     * <p>
     * This feature is not yet supported.
     *
     * @since 24.2
     */
    // TODO GR-57220
    // String[] decisionOverrideFiles() default {};

    /**
     * Whether to build the interpreter with tracing. Can also be configured using the
     * {@code truffle.dsl.OperationsEnableTracing} option during compilation.
     * <p>
     * Note that this is a debug option that should not be used in production. Also note that this
     * field only affects code generation: whether tracing is actually performed at run time is
     * still controlled by the aforementioned option.
     * <p>
     * This feature is not yet supported.
     *
     * @since 24.2
     */
    // TODO GR-57220
    // boolean forceTracing() default false;

    /**
     * Primitive types the interpreter should attempt to avoid boxing up. Each type should be
     * primitive class literal (e.g., {@code int.class}).
     * <p>
     * If boxing elimination types are provided, the cached interpreter will generate instruction
     * variants that load/store primitive values when possible. It will automatically use these
     * instructions in a best-effort manner (falling back on boxed representations when necessary).
     *
     * @since 24.2
     */
    Class<?>[] boxingEliminationTypes() default {};

    /**
     * Whether to generate introspection data for specializations. The data is accessible using
     * {@link com.oracle.truffle.api.bytecode.Instruction.Argument#getSpecializationInfo()}.
     *
     * @since 24.2
     */
    boolean enableSpecializationIntrospection() default false;

    /**
     * Sets the default value that {@link BytecodeLocal locals} return when they are read without
     * ever being written. Unless a default local value is specified, loading from a
     * {@link BytecodeLocal local} that was never stored into throws a
     * {@link FrameSlotTypeException}.
     * <p>
     * It is recommended for the default local value expression to refer to a static and final
     * constant in the bytecode root node. For example:
     *
     * <pre>
     * &#64;GenerateBytecode(..., defaultLocalValue = "DEFAULT_VALUE")
     * abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode {
     *
     *     static final DefaultValue DEFAULT_VALUE = DefaultValue.INSTANCE;
     *
     *     // ...
     * }
     * </pre>
     *
     * The expression supports a subset of Java (see the {@link com.oracle.truffle.api.dsl.Cached
     * Cached} documentation), including other expressions like <code>null</code> or a static method
     * call. Note that instance members of the root node cannot be bound with the default local
     * value expression for efficiency reasons.
     *
     * @since 24.2
     */
    String defaultLocalValue() default "";

    /**
     * Whether the {@link BytecodeDebugListener} methods should be notified by generated code. By
     * default the debug bytecode listener is enabled if the root node implements
     * {@link BytecodeDebugListener}. If this attribute is set to <code>false</code> then the debug
     * bytecode listener won't be notified. This attribute may be useful to keep a default debug
     * listener implementation permanently in the source code but only enable it temporarily during
     * debug sessions.
     *
     * @since 24.2
     */
    boolean enableBytecodeDebugListener() default true;

    /**
     * Sets the maximum number of stack slots that will be used for parameters annotated with
     * {@link Variadic}. The value must represent a power of 2 greater than 1 and must not exceed
     * {@link Short#MAX_VALUE}. The default value is "32". As with {@link #defaultLocalValue()}, it
     * is possible to specify this limit via an expression. If a constant value is provided, the
     * limit is validated at compile time; otherwise, it is validated at class initialization.
     * <p>
     * The expression supports a subset of Java (see {@link com.oracle.truffle.api.dsl.Cached
     * Cached}), and may include simple constants (for example, <code>0</code>) or static method
     * calls. It must evaluate to an <code>int</code>. Note that only static members of the root
     * node can be bound with the expression.
     *
     * @since 25.0
     */
    String variadicStackLimit() default "32";

    /**
     * Enables additional assertions, that would be otherwise too costly outside testing. The
     * additional assertions can also be enabled dynamically at build time by passing
     * <code>-Atruffle.dsl.AdditionalAssertions=true</code> to the Java compiler.
     *
     * @since 25.0
     */
    boolean additionalAssertions() default false;

}
