/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <h1>Vector API support in Graal</h1>
 *
 * This package contains intrinsic nodes, a transformation phase, and utilities for representing
 * Java Vector API computations and translating them to efficient SIMD code.
 *
 * <h2>The Vector API</h2>
 *
 * The <a href="https://openjdk.org/jeps/338">Java Vector API</a> allows one to specify computations
 * intended for SIMD execution. Consider the following example, assuming that an {@code int} array
 * {@code source} contains the elements {@code 1, 2, 3, 4} starting at index 0, and that the
 * variable {@code y} has the value 7. Comments show the vector values produced by each operation:
 *
 * <pre>
 * IntVector.fromArray(IntVector.SPECIES_128, source, 0)  // ==> [1, 2, 3, 4]
 *                 .add(y)                                // ==> [8, 9, 10, 11]
 *                 .intoArray(dest, 0);                   // ==> [8, 9, 10, 11] stored in dest array
 * </pre>
 *
 * The API has a pure Java implementation, but it is designed to be intrinsified by the compiler and
 * optimized directly to SIMD instructions. The example above will compile to about four
 * instructions (read, broadcast y, add, write; the add might be folded into the read or the write
 * on some targets).
 * <p/>
 *
 * Vectors are value-based types. They are never mutated in place, instead each operation produces a
 * new value. Therefore, without intrinsification each Vector API operation produces a new heap
 * object representing the vector's values. Each object is associated with a specific
 * <em>species</em>, describing the vector's element type and number of elements.
 * <p/>
 *
 * See the documentation for the {@code jdk.incubator.vector.Vector} class for much more background
 * information on the Vector API.
 *
 * <h2>Intrinsification of the Vector API</h2>
 *
 * The API contains many classes with a large set of methods. However, all user-facing methods are
 * {@code @ForceInline} methods that, after inlining through several layers, eventually arrive at
 * one of about twenty base {@code @IntrinsicCandidate} operations in
 * {@code jdk.internal.vm.vector.VectorSupport}. The compiler only needs to intrinsify this small
 * core set of operations. Information about the exact operation and the types involved are passed
 * to these intrinsics as arguments. For example, the {@code binaryOp} method representing the
 * {@code add} operation above receives as arguments a constant {@code int} opcode encoding the
 * "add" operation, as well as a class constant for the {@code Int128Vector} species on which it
 * should operate.
 * <p/>
 *
 * The above example annotated with the names of the {@code VectorSupport} intrinsic methods:
 *
 * <pre>
 * IntVector.fromArray(IntVector.SPECIES_128, source, 0)  // load
 *                 .add(                                  // binaryOp
 *                                 y)                     // fromBitsCoerced
 *                 .intoArray(dest, 0);                   // store
 * </pre>
 *
 * <p/>
 * We represent these intrinsic operations using
 * {@linkplain jdk.graal.compiler.replacements.nodes.MacroNode macro nodes}. See
 * {@link jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIMacroNode} for the common
 * superclass of the macro nodes specific to our intrinsification of the Vector API. The graph
 * builder plugins building these nodes during bytecode parsing are in
 * {@link jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIIntrinsics}.
 *
 * <h2>Expansion to SIMD code</h2>
 *
 * The {@link jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIExpansionPhase} examines
 * connected groups of Vector API macro nodes and decides if and how to replace them by an
 * equivalent SIMD version. In the running example, it will perform the following replacement:
 *
 * <pre>
 *                        y                                           y
 *                        |                                           |
 * VectorAPILoad     VectorAPIFromBitsCoerced     ===>    Read   SimdBroadcast
 *            \          /                                    \ /
 *         VectorAPIBinaryOp +                                 +
 *                  |                                          |
 *           VectorAPIStore                                  Write
 * </pre>
 *
 * where the new read, add, and broadcast nodes have a {@code <i32, i32, i32, i32>}
 * {@link jdk.graal.compiler.vector.nodes.simd.SimdStamp}. See the documentation of the phase for
 * more details.
 *
 * <h2>API subset intrinsified by Graal</h2>
 *
 * The current version of our intrinsics supports:
 *
 * <ul>
 * <li>load and store of vectors without masks</li>
 * <li>most unary and binary arithmetic operations; binary operations may use masks</li>
 * <li>reductions of vectors to scalars with a binary operation</li>
 * <li>comparisons</li>
 * <li>type conversions</li>
 * <li>some property tests on vector elements (e.g., {@code IS_NAN})</li>
 * <li>tests on masks ({@code anyTrue, allTrue})</li>
 * <li>blends</li>
 * <li>{@code iotaShuffle(init, step)} operation to build vectors like
 * {@code <init, init+step, init+step+step, ...>}</li>
 * </ul>
 *
 * Currently unsupported:
 *
 * <ul>
 * <li>insert/extract/rearrange vector elements</li>
 * <li>most operations with masks</li>
 * <li>gather/scatter memory accesses</li>
 * <li>transcendental math operations on floating-point vectors</li>
 * <li>some arithmetic operations that don't map cleanly to existing Graal infrastructure</li>
 * </ul>
 *
 * Unsupported intrinsics are listed in
 * {@link jdk.graal.compiler.hotspot.meta.UnimplementedGraalIntrinsics}.
 *
 * Non-intrinsified operations will run the pure Java implementation and will be very slow.
 *
 * <h2>Note on mask representation</h2>
 *
 * Vector masks are vectors of "truth values" associated with a vector species. For example, the
 * {@code Float256Vector} species for a vector of 8 {@code float} values is associated with a
 * {@code Float256Mask} type containing 8 {@code true/false} values, one per vector element. Such
 * masks can be represented in different ways:
 *
 * <ul>
 * <li>in memory, the {@code Float256Mask} type uses an array {@code boolean[8]}, so each element is
 * an 8-bit integer with value 0 or 1</li>
 * <li>in the graph, we use an abstract representation using an 8-element
 * {@link jdk.graal.compiler.vector.nodes.simd.SimdStamp}, each element of which is a
 * {@link jdk.graal.compiler.vector.nodes.simd.LogicValueStamp} referring to the {@code float} stamp
 * (written as {@code logic(f32)}</li>
 * <li>vector architectures like AVX1/AVX2 and Neon use a "bitmask" representation where a mask is a
 * vector of the same size as the associated "value" type, thus the mask will contain 256 bits, with
 * each element being 0 (32 0 bits) for {@code false} or -1 (32 1 bits) for {@code true}</li>
 * <li>vector architectures like AVX-512 and SVE use mask registers with one bit per vector element,
 * i.e., 8 bits for the mask in the example</li>
 * </ul>
 *
 * There are many places where we need to convert between these representations, either to transfer
 * masks to/from memory, to perform binary operations on masks ({@code and, not}), or for other
 * operations like type conversions. These representation conversions add complexity throughout any
 * implementation of the Vector API.
 */
package jdk.graal.compiler.vector.replacements.vectorapi;
