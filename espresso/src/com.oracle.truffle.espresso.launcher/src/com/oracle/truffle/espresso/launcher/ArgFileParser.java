/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.launcher;

import static com.oracle.truffle.espresso.launcher.ArgFileParser.State.FIND_NEXT;
import static com.oracle.truffle.espresso.launcher.ArgFileParser.State.IN_COMMENT;
import static com.oracle.truffle.espresso.launcher.ArgFileParser.State.IN_ESCAPE;
import static com.oracle.truffle.espresso.launcher.ArgFileParser.State.IN_QUOTE;
import static com.oracle.truffle.espresso.launcher.ArgFileParser.State.IN_TOKEN;
import static com.oracle.truffle.espresso.launcher.ArgFileParser.State.SKIP_LEAD_WS;

import java.io.IOException;
import java.io.Reader;
import java.util.function.Consumer;

/**
 * Parse {@code @arg-files} as handled by libjli. See libjli/args.c.
 */
public final class ArgFileParser {
    private final Reader reader;

    protected enum State {
        FIND_NEXT,
        IN_COMMENT,
        IN_QUOTE,
        IN_ESCAPE,
        SKIP_LEAD_WS,
        IN_TOKEN
    }

    public ArgFileParser(Reader reader) {
        this.reader = reader;
    }

    public void parse(Consumer<String> argConsumer) throws IOException {
        String token;
        while ((token = nextToken()) != null) {
            argConsumer.accept(token);
        }
    }

    @SuppressWarnings("fallthrough")
    private String nextToken() throws IOException {
        State state = FIND_NEXT;
        int currentQuoteChar = -1;
        int ch;
        StringBuilder sb = new StringBuilder();
        charloop: while ((ch = reader.read()) >= 0) {
            // Skip white space characters
            if (state == FIND_NEXT || state == SKIP_LEAD_WS) {
                while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f') {
                    ch = reader.read();
                    if (ch < 0) {
                        break charloop;
                    }
                }
                state = (state == FIND_NEXT) ? IN_TOKEN : IN_QUOTE;
                // Deal with escape sequences
            } else if (state == IN_ESCAPE) {
                // concatenation directive
                if (ch == '\n' || ch == '\r') {
                    state = SKIP_LEAD_WS;
                } else {
                    // escaped character
                    switch (ch) {
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        default:
                            sb.append((char) ch);
                            break;
                    }
                    state = IN_QUOTE;
                }
                continue;
                // ignore comment to EOL
            } else if (state == IN_COMMENT) {
                while (ch != '\n' && ch != '\r') {
                    ch = reader.read();
                    if (ch < 0) {
                        break charloop;
                    }
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
                state = FIND_NEXT;
                continue;
            }

            assert (state != IN_ESCAPE);
            assert (state != FIND_NEXT);
            assert (state != SKIP_LEAD_WS);
            assert (state != IN_COMMENT);

            switch (ch) {
                case ' ':
                case '\t':
                case '\f':
                    if (state == IN_QUOTE) {
                        sb.append((char) ch);
                        continue;
                    }
                    // fallthrough
                case '\n':
                case '\r':
                    return sb.toString();
                case '#':
                    if (state == IN_QUOTE) {
                        sb.append((char) ch);
                        continue;
                    }
                    state = IN_COMMENT;
                    break;
                case '\\':
                    if (state != IN_QUOTE) {
                        sb.append((char) ch);
                        continue;
                    }
                    state = IN_ESCAPE;
                    break;
                case '\'':
                case '"':
                    if (state == IN_QUOTE && currentQuoteChar != ch) {
                        // not matching quote
                        sb.append((char) ch);
                        continue;
                    }
                    // anchor after quote character
                    if (state == IN_TOKEN) {
                        currentQuoteChar = ch;
                        state = IN_QUOTE;
                    } else {
                        state = IN_TOKEN;
                    }
                    break;
                default:
                    sb.append((char) ch);
                    break;
            }
        }
        if (sb.isEmpty()) {
            return null;
        }
        return sb.toString();
    }
}
