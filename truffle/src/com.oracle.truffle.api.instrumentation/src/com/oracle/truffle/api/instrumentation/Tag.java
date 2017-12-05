package com.oracle.truffle.api.instrumentation;

import java.util.function.Consumer;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;

/**
 * Base class for tags. A tag subclass can be used to
 * {@link InstrumentableNode#hasInstrumentationTag(Class) mark} mark AST nodes can be .
 *
 * @see StandardTags For the standard set of tags
 */
public abstract class Tag {

    protected Tag() {
        throw new AssertionError("No tag instances allowed.");
    }

    protected static <T> Attribute<T> createAttribute(Class<? extends Tag> tag, String name, Class<T> type, Function<Node, T> defaultValue, Consumer<T> validator) {
        return new Attribute<>(tag, name, type, defaultValue, validator);
    }

    public static final class Attribute<T> {

        private final Class<? extends Tag> tag;
        private final String name;
        private final Class<T> type;
        private final Function<Node, T> defaultValue;
        private final Consumer<T> validator;

        Attribute(Class<? extends Tag> tag, String name, Class<T> type, Function<Node, T> defaultValue, Consumer<T> validator) {
            this.tag = tag;
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.validator = validator;
        }

        public Class<? extends Tag> getTag() {
            return tag;
        }

        public String getName() {
            return name;
        }

        T getValue(Node node) {
            if (!(node instanceof InstrumentableNode)) {
                throw new IllegalArgumentException("Cannot request attributes from nodes which are not instrumentable.");
            }
            InstrumentableNode instrumentable = ((InstrumentableNode) node);
            if (!instrumentable.isInstrumentable()) {
                throw new IllegalArgumentException("Cannot request attributes from nodes which are not instrumentable.");
            }

            Object result = ((InstrumentableNode) node).getTagAttribute(this);
            if (result != null) {
                if (!type.isInstance(result)) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(String.format("Invalid attribute value '%s' resturned for %s.", result, this));
                }
                T value = type.cast(result);
                try {
                    validator.accept(value);
                } catch (Throwable e) {
                    CompilerDirectives.transferToInterpreter();
                    throw e;
                }
                return value;
            } else {
                return defaultValue.apply(node);
            }
        }

    }

}
