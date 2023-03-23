package com.oracle.svm.core.configure;

import com.oracle.svm.core.panama.Target_jdk_internal_foreign_abi_NativeEntrypoint;
import com.oracle.svm.core.panama.downcalls.PanamaDowncallsSupport;
import org.graalvm.util.json.JSONParserException;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PanamaForeignConfigurationParser extends ConfigurationParser {
    public PanamaForeignConfigurationParser() {
        super(true);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) throws IOException {
        parseSignatures(asList(json, "first level of document must be an array of method signatures"));
    }

    private void parseSignatures(List<Object> signatures) {
        for (Object signature: signatures) {
            parseSignature(asString(signature, "second level of document must be function descriptors"));
        }
    }

    private void parseSignature(String signature) {
        PanamaDowncallsSupport.singleton().register(Target_jdk_internal_foreign_abi_NativeEntrypoint.make(LAYOUT_PARSER.parse(signature)));
    }

    static class LayoutParser {
        private String layout = "";
        private int at = 0;

        private char peek() {
            if (this.at == layout.length()) {
                return '\0';
            }
            return layout.charAt(at);
        }

        private char consume() {
            if (this.at == layout.length()) {
                return '\0';
            }
            return layout.charAt(at++);
        }

        private char consumeChecked(char expected) {
            char v = consume();
            if (v != expected) {
                handleError("Expected " + expected + " but got " + v + "in " + layout);
            }
            return v;
        }

        public FunctionDescriptor parse(String layout) {
            this.layout = layout;
            this.at = 0;
            FunctionDescriptor res = parseDescriptor();
            if (this.at < layout.length()) {
                handleError("Layout parsing ended (at " + this.at + ")before its end: " + layout);
            }
            return res;
        }

        private <T> List<T> parseSequence(char start, char end, Supplier<T> parser) {
            consumeChecked(start);
            ArrayList<T> elements = new ArrayList<>();
            while (peek() != end) {
                elements.add(parser.get());
            }
            consumeChecked(end);
            return elements;
        }

        private FunctionDescriptor parseDescriptor() {
            MemoryLayout[] arguments = parseSequence('(', ')', this::parseLayout).toArray(new MemoryLayout[0]);
            if (peek() == 'v') {
                consume();
                return FunctionDescriptor.ofVoid(arguments);
            }
            else {
                return FunctionDescriptor.of(parseLayout(), arguments);
            }
        }

        private int parseInt() {
            int start = at;
            boolean atLeastOneDigit = Character.isDigit(peek());

            if (!atLeastOneDigit) {
                handleError("Expected a number at position " + at + " of layout " + layout);
            }

            while (Character.isDigit(peek())) {
                consume();
            }
            return Integer.parseInt(layout.substring(start, at));
        }

        private MemoryLayout parseLayout() {
            return switch (Character.toUpperCase(peek())) {
                case 'Z' -> parseValueLayout(boolean.class);
                case 'B' -> parseValueLayout(byte.class);
                case 'S' -> parseValueLayout(short.class);
                case 'C' -> parseValueLayout(char.class);
                case 'I' -> parseValueLayout(int.class);
                case 'J' -> parseValueLayout(long.class);
                case 'F' -> parseValueLayout(float.class);
                case 'D' -> parseValueLayout(double.class);
                case 'A' -> parseValueLayout(MemorySegment.class);
                case '[' -> parseSequenceLayout();
                case '{' -> parseStructLayout();
                case '<' -> parseUnionLayout();
                case 'X' -> parsePaddingLayout();
                default -> handleError("Unknown carrier: " + peek());
            };

        }

        private MemoryLayout parseUnionLayout() {
            return MemoryLayout.unionLayout(parseSequence('<', '>', this::parseLayout).toArray(new MemoryLayout[0]));
        }

        private MemoryLayout parseStructLayout() {
            return MemoryLayout.structLayout(parseSequence('{', '}', this::parseLayout).toArray(new MemoryLayout[0]));
        }

        private MemoryLayout parsePaddingLayout() {
            consumeChecked('x');
            return MemoryLayout.paddingLayout(parseInt());
        }

        private MemoryLayout parseSequenceLayout() {
            consumeChecked('[');
            int size = parseInt();
            consumeChecked(':');
            MemoryLayout element = parseLayout();
            consumeChecked(']');
            return MemoryLayout.sequenceLayout(size, element);
        }

        private ValueLayout parseValueLayout(Class<?> carrier) {ByteOrder order = ByteOrder.BIG_ENDIAN;
            if (Character.isLowerCase(peek())) {
                order = ByteOrder.LITTLE_ENDIAN;
            }
            consume();
            ValueLayout layout = MemoryLayout.valueLayout(carrier, order);
            int size = parseInt();
            assert layout.bitSize() == size;

            // Modifiers
            switch (peek()) {
                // Name
                case '(' -> handleError("Layout should not carry names: " + layout);
                // Alignment
                case '%' -> {
                    consume();
                    layout = layout.withBitAlignment(parseInt());
                }
            };

            return layout;
        }
    }
    static final LayoutParser LAYOUT_PARSER = new LayoutParser();

    private static <T> T handleError(String msg) {
        throw new JSONParserException(msg);
    }
}
