/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.base.facade;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * This class implements a chain of responsibility pattern by delegating node creation to another
 * factory, and return a default implementation if the factory cannot create the object.
 */

public abstract class NodeFactoryFacadeComposite implements NodeFactoryFacade {

    /**
     * Returns a factory that will use the <code>first</code> factory to create a node, and only use
     * the <code>second</code> factory if the node creation call to the <code>first</code> factory
     * returns <code>null</code>.
     *
     * This method implements this pattern for every call:
     *
     * <pre>
     * &#064;Override
     * public LLVMExpressionNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index) {
     *     LLVMExpressionNode firstNode = first.createInsertElement(resultType, vector, vectorType, element, index);
     *     if (firstNode == null) {
     *         return second.createInsertElement(resultType, vector, vectorType, element, index);
     *     } else {
     *         return firstNode;
     *     }
     * }
     * </pre>
     *
     * @param first the first factory with higher priority
     * @param second the second factory with lower priority
     * @return a new factory that implements a chain of responsibility pattern
     */
    public static NodeFactoryFacade create(NodeFactoryFacade first, NodeFactoryFacade second) {
        ClassLoader classLoader = NodeFactoryFacadeComposite.class.getClassLoader();
        Class<?>[] nodeFactoryInterface = new Class[]{NodeFactoryFacade.class};
        CompositeNodeFactoryProxy callHandler = new CompositeNodeFactoryProxy(first, second);
        Object proxy = Proxy.newProxyInstance(classLoader,
                        nodeFactoryInterface,
                        callHandler);
        return (NodeFactoryFacade) proxy;
    }

    static class CompositeNodeFactoryProxy implements InvocationHandler {

        private final NodeFactoryFacade first;
        private final NodeFactoryFacade second;

        CompositeNodeFactoryProxy(NodeFactoryFacade first, NodeFactoryFacade second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("setUpFacade")) {
                method.invoke(first, args);
                method.invoke(second, args);
            }
            Object factoryResult = method.invoke(first, args);
            if (factoryResult == null || isEmptyOptional(factoryResult)) {
                return method.invoke(second, args);
            } else {
                return factoryResult;
            }
        }

        private static boolean isEmptyOptional(Object factoryResult) {
            return factoryResult instanceof Optional<?> && !((Optional<?>) factoryResult).isPresent();
        }
    }

}
