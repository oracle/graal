/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.parser;

import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import org.netbeans.api.progress.ProgressHandle;

class Token {
	public int kind;    // token kind
	public long pos;     // token position in bytes in the source text (starting at 0)
	public long charPos; // token position in characters in the source text (starting at 0)
	public int col;     // token column (starting at 1)
	public int line;    // token line (starting at 1)
	public String val;  // token value
	public Token next;  // ML 2005-03-11 Peek tokens are kept in linked list
}

//-----------------------------------------------------------------------------------
// Buffer
//-----------------------------------------------------------------------------------
class Buffer {
	// This Buffer supports the following cases:
	// 1) seekable stream (file)
	//    a) whole stream in buffer
	//    b) part of stream in buffer
	// 2) non seekable stream (network, console)

	public static final int EOF = Character.MAX_VALUE + 1;
	private static final int MIN_BUFFER_LENGTH = 1024; // 1KB
	private static final int MAX_BUFFER_LENGTH = MIN_BUFFER_LENGTH * 64; // 64KB
	private byte[] buf;   // input buffer
	private long bufStart; // position of first byte in buffer relative to input stream
	private int bufLen;   // length of buffer
	private long fileLen;  // length of input stream (may change if stream is no file)
	private int bufPos;      // current position in buffer
	private RandomAccessFile file; // input stream (seekable)
	private InputStream stream; // growing input stream (e.g.: console, network)
        ProgressHandle progressHandle;
        int maxSetPosValue;

	public Buffer(InputStream s) {
		stream = s;
		fileLen = 0;
                bufLen = 0;
                bufStart = 0;
                bufPos = 0;
		buf = new byte[MIN_BUFFER_LENGTH];
	}

        public Buffer(String fileName, ProgressHandle progressHandle) {
                this.progressHandle = progressHandle;
		try {
			file = new RandomAccessFile(fileName, "r");
			fileLen = file.length();
                        if (progressHandle != null) {
                                progressHandle.start((int)(fileLen / MAX_BUFFER_LENGTH));
                        }
			bufLen = (int) Math.min(fileLen, MAX_BUFFER_LENGTH);
			buf = new byte[bufLen];
			bufStart = Long.MAX_VALUE; // nothing in buffer so far
			if (fileLen > 0) setPos(0); // setup buffer to position 0 (start)
			else bufPos = 0; // index 0 is already after the file, thus setPos(0) is invalid
			if (bufLen == fileLen) Close();
		} catch (IOException e) {
			throw new FatalError("Could not open file " + fileName);
		}
	}

	// don't use b after this call anymore
	// called in UTF8Buffer constructor
	protected Buffer(Buffer b) {
		buf = b.buf;
		bufStart = b.bufStart;
		bufLen = b.bufLen;
		fileLen = b.fileLen;
		bufPos = b.bufPos;
		file = b.file;
		stream = b.stream;
		// keep finalize from closing the file
		b.file = null;
	}

	protected void finalize() throws Throwable {
		super.finalize();
		Close();
	}

	protected void Close() {
		if (file != null) {
			try {
				file.close();
				file = null;
			} catch (IOException e) {
				throw new FatalError(e.getMessage());
			}
		}
	}

	public int Read() {
		if (bufPos < bufLen) {
			return buf[bufPos++] & 0xff;  // mask out sign bits
		} else if (getPos() < fileLen) {
			setPos(getPos());         // shift buffer start to pos
			return buf[bufPos++] & 0xff; // mask out sign bits
		} else if (stream != null && ReadNextStreamChunk() > 0) {
			return buf[bufPos++] & 0xff;  // mask out sign bits
		} else {
			return EOF;
		}
	}

	public int Peek() {
		long curPos = getPos();
		int ch = Read();
		setPos(curPos);
		return ch;
	}

	// beg .. begin, zero-based, inclusive, in byte
	// end .. end, zero-based, exclusive, in byte
	public String GetString(long beg, long end) {
		int len = 0;
		char[] buf = new char[(int) (end - beg)];
		long oldPos = getPos();
		setPos(beg);
		while (getPos() < end) buf[len++] = (char) Read();
		setPos(oldPos);
		return new String(buf, 0, len);
	}

	public long getPos() {
		return bufPos + bufStart;
	}

	public void setPos(long value) {
		if (value >= fileLen && stream != null) {
			// Wanted position is after buffer and the stream
			// is not seek-able e.g. network or console,
			// thus we have to read the stream manually till
			// the wanted position is in sight.
			while (value >= fileLen && ReadNextStreamChunk() > 0);
		}

		if (value < 0 || value > fileLen) {
			throw new FatalError("buffer out of bounds access, position: " + value);
		}

		if (value >= bufStart && value < bufStart + bufLen) { // already in buffer
                        bufPos = (int) (value - bufStart);
		} else if (file != null) { // must be swapped in
			try {
				file.seek(value);
				bufLen = file.read(buf);
				bufStart = value; bufPos = 0;
			} catch(IOException e) {
				throw new FatalError(e.getMessage());
			}
                        int newPosValue = (int)(value / MAX_BUFFER_LENGTH);
                        if (progressHandle != null && newPosValue > maxSetPosValue) {
                                progressHandle.progress(newPosValue);
                                maxSetPosValue = newPosValue;
                        }
		} else {
			// set the position to the end of the file, Pos will return fileLen.
                    throw new InternalError();
		}
	}
	
	// Read the next chunk of bytes from the stream, increases the buffer
	// if needed and updates the fields fileLen and bufLen.
	// Returns the number of bytes read.
	private int ReadNextStreamChunk() {
		int free = buf.length - bufLen;
		if (free == 0) {
			// in the case of a growing input stream
			// we can neither seek in the stream, nor can we
			// foresee the maximum length, thus we must adapt
			// the buffer size on demand.
			byte[] newBuf = new byte[bufLen * 2];
			System.arraycopy(buf, 0, newBuf, 0, bufLen);
			buf = newBuf;
			free = bufLen;
		}
		
		int read;
		try { read = stream.read(buf, bufLen, free); }
		catch (IOException ioex) { throw new FatalError(ioex.getMessage()); }
		
		if (read > 0) {
			fileLen = bufLen = (bufLen + read);
			return read;
		}
		// end of stream reached
		return 0;
	}
}

//-----------------------------------------------------------------------------------
// UTF8Buffer
//-----------------------------------------------------------------------------------
class UTF8Buffer extends Buffer {
	UTF8Buffer(Buffer b) { super(b); }

	public int Read() {
		int ch;
		do {
			ch = super.Read();
			// until we find a utf8 start (0xxxxxxx or 11xxxxxx)
		} while ((ch >= 128) && ((ch & 0xC0) != 0xC0) && (ch != EOF));
		if (ch < 128 || ch == EOF) {
			// nothing to do, first 127 chars are the same in ascii and utf8
			// 0xxxxxxx or end of file character
		} else if ((ch & 0xF0) == 0xF0) {
			// 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
			int c1 = ch & 0x07; ch = super.Read();
			int c2 = ch & 0x3F; ch = super.Read();
			int c3 = ch & 0x3F; ch = super.Read();
			int c4 = ch & 0x3F;
			ch = (((((c1 << 6) | c2) << 6) | c3) << 6) | c4;
		} else if ((ch & 0xE0) == 0xE0) {
			// 1110xxxx 10xxxxxx 10xxxxxx
			int c1 = ch & 0x0F; ch = super.Read();
			int c2 = ch & 0x3F; ch = super.Read();
			int c3 = ch & 0x3F;
			ch = (((c1 << 6) | c2) << 6) | c3;
		} else if ((ch & 0xC0) == 0xC0) {
			// 110xxxxx 10xxxxxx
			int c1 = ch & 0x1F; ch = super.Read();
			int c2 = ch & 0x3F;
			ch = (c1 << 6) | c2;
		}
		return ch;
	}
}

//-----------------------------------------------------------------------------------
// StartStates  -- maps characters to start states of tokens
//-----------------------------------------------------------------------------------
class StartStates {
	private static class Elem {
		public int key, val;
		public Elem next;
		public Elem(int key, int val) { this.key = key; this.val = val; }
	}

	private Elem[] tab = new Elem[128];

	public void set(int key, int val) {
		Elem e = new Elem(key, val);
		int k = key % 128;
		e.next = tab[k]; tab[k] = e;
	}

	public int state(int key) {
		Elem e = tab[key % 128];
		while (e != null && e.key != key) e = e.next;
		return e == null ? 0: e.val;
	}
}

//-----------------------------------------------------------------------------------
// Scanner
//-----------------------------------------------------------------------------------
public class Scanner {
	static final char EOL = '\n';
	static final int  eofSym = 0;
	static final int maxT = 54;
	static final int noSym = 54;


	public Buffer buffer; // scanner buffer

	Token t;           // current token
	int ch;            // current input character
	long pos;           // byte position of current character
	long charPos;       // position by unicode characters starting with 0
	int col;           // column number of current character
	int line;          // line number of current character
	int oldEols;       // EOLs that appeared in a comment;
	static final StartStates start; // maps initial token character to start state
        static final Map<String, Integer> literals;      // maps literal strings to literal kinds
        static int maxLiteral;
        static boolean[] literalFirstChar;

	Token tokens;      // list of tokens already peeked (first token is a dummy)
	Token pt;          // current peek token
	
	char[] tval = new char[16]; // token text used in NextToken(), dynamically enlarged
	int tlen;          // length of current token


	static {
		start = new StartStates();
                literals = new HashMap<String, Integer>();
		for (int i = 42; i <= 42; ++i) start.set(i, 1);
		for (int i = 45; i <= 45; ++i) start.set(i, 1);
		for (int i = 48; i <= 58; ++i) start.set(i, 1);
		for (int i = 65; i <= 90; ++i) start.set(i, 1);
		for (int i = 95; i <= 95; ++i) start.set(i, 1);
		for (int i = 97; i <= 122; ++i) start.set(i, 1);
		for (int i = 124; i <= 124; ++i) start.set(i, 1);
		start.set(91, 2); 
		start.set(93, 3); 
		start.set(46, 4); 
		start.set(60, 5); 
		start.set(44, 8); 
		start.set(34, 9); 
		start.set(Buffer.EOF, -1);
		literals.put("begin_compilation", 2);
		literals.put("name", 3);
		literals.put("method", 4);
		literals.put("date", 5);
		literals.put("end_compilation", 6);
		literals.put("begin_cfg", 7);
		literals.put("id", 8);
		literals.put("caller_id", 9);
		literals.put("end_cfg", 10);
		literals.put("begin_block", 11);
		literals.put("from_bci", 12);
		literals.put("to_bci", 13);
		literals.put("predecessors", 14);
		literals.put("successors", 15);
		literals.put("xhandlers", 16);
		literals.put("flags", 17);
		literals.put("dominator", 18);
		literals.put("loop_index", 19);
		literals.put("loop_depth", 20);
		literals.put("first_lir_id", 21);
		literals.put("last_lir_id", 22);
		literals.put("probability", 23);
		literals.put("end_block", 24);
		literals.put("begin_states", 25);
		literals.put("begin_stack", 26);
		literals.put("end_stack", 27);
		literals.put("begin_locks", 28);
		literals.put("end_locks", 29);
		literals.put("begin_locals", 30);
		literals.put("end_locals", 31);
		literals.put("end_states", 32);
		literals.put("size", 33);
		literals.put("begin_HIR", 36);
		literals.put("end_HIR", 37);
		literals.put("begin_LIR", 39);
		literals.put("end_LIR", 40);
		literals.put("begin_IR", 41);
		literals.put("HIR", 42);
		literals.put("LIR", 43);
		literals.put("end_IR", 44);
		literals.put("begin_intervals", 46);
		literals.put("end_intervals", 47);
		literals.put("begin_nmethod", 49);
		literals.put("end_nmethod", 50);
		literals.put("begin_bytecodes", 51);
		literals.put("end_bytecodes", 52);

                literalFirstChar = new boolean[0];
                for (String literal : literals.keySet()) {
                    maxLiteral = Math.max(literal.length(), maxLiteral);
                    char c = literal.charAt(0);
                    if (c >= literalFirstChar.length) {
                        literalFirstChar = Arrays.copyOf(literalFirstChar, c + 1);
                    }
                    literalFirstChar[c] = true;
                }
                maxLiteral = 0;
                literalFirstChar = null;
        }
	
        public Scanner (String fileName, ProgressHandle progressHandle) {
                buffer = new Buffer(fileName, progressHandle);
		Init();
	}
	
	public Scanner(InputStream s) {
                buffer = new Buffer(s);
		Init();
	}
	
	void Init () {
		pos = -1; line = 1; col = 0; charPos = -1;
		oldEols = 0;
		NextCh();
		if (ch == 0xEF) { // check optional byte order mark for UTF-8
			NextCh(); int ch1 = ch;
			NextCh(); int ch2 = ch;
			if (ch1 != 0xBB || ch2 != 0xBF) {
				throw new FatalError("Illegal byte order mark at start of file");
			}
			buffer = new UTF8Buffer(buffer); col = 0; charPos = -1;
			NextCh();
		}
		pt = tokens = new Token();  // first token is a dummy
	}
	
	void NextCh() {
		if (oldEols > 0) { ch = EOL; oldEols--; }
		else {
			pos = buffer.getPos();
			// buffer reads unicode chars, if UTF8 has been detected
			ch = buffer.Read(); col++; charPos++;
			// replace isolated '\r' by '\n' in order to make
			// eol handling uniform across Windows, Unix and Mac
			if (ch == '\r' && buffer.Peek() != '\n') ch = EOL;
			if (ch == EOL) { line++; col = 0; }
		}

	}
	
	void AddCh() {
		if (tlen >= tval.length) {
			char[] newBuf = new char[2 * tval.length];
			System.arraycopy(tval, 0, newBuf, 0, tval.length);
			tval = newBuf;
		}
		if (ch != Buffer.EOF) {
			tval[tlen++] = (char)ch; 

			NextCh();
		}

	}
	


	void CheckLiteral() {
		String val = t.val;

//                if (maxLiteral == 0 || val.length() <= maxLiteral && val.length() > 0) {
//                        char c = val.charAt(0);
//                        if (literalFirstChar == null || c < literalFirstChar.length && literalFirstChar[c]) {
                                Object kind = literals.get(val);
                                if (kind != null) {
                                        t.kind = ((Integer) kind).intValue();
                                }
//                        }
//                }
        }

	Token NextToken() {
		while (ch == ' ' ||
			ch == 10 || ch == 13
		) NextCh();

		int recKind = noSym;
		long recEnd = pos;
		t = new Token();
		t.pos = pos; t.col = col; t.line = line; t.charPos = charPos;
		int state = start.state(ch);
		tlen = 0; AddCh();

		loop: for (;;) {
			switch (state) {
				case -1: { t.kind = eofSym; break loop; } // NextCh already done 
				case 0: {
					if (recKind != noSym) {
                                                tlen = Math.toIntExact(recEnd - t.pos);
						SetScannerBehindT();
					}
					t.kind = recKind; break loop;
				} // NextCh already done
				case 1:
					recEnd = pos; recKind = 1;
					if (ch == '*' || ch == '-' || ch >= '0' && ch <= ':' || ch >= 'A' && ch <= 'Z' || ch == '_' || ch >= 'a' && ch <= 'z' || ch == '|') {AddCh(); state = 1; break;}
					else {t.kind = 1; t.val = new String(tval, 0, tlen); CheckLiteral(); return t;}
				case 2:
					{t.kind = 34; break loop;}
				case 3:
					{t.kind = 35; break loop;}
				case 4:
					{t.kind = 38; break loop;}
				case 5:
					if (ch == '|') {AddCh(); state = 6; break;}
					else {state = 0; break;}
				case 6:
					if (ch == '@') {AddCh(); state = 7; break;}
					else {state = 0; break;}
				case 7:
					{t.kind = 45; break loop;}
				case 8:
					{t.kind = 48; break loop;}
				case 9:
					{t.kind = 53; break loop;}

			}
		}
		t.val = new String(tval, 0, tlen);
		return t;
	}
	
	private void SetScannerBehindT() {
		buffer.setPos(t.pos);
		NextCh();
		line = t.line; col = t.col; charPos = t.charPos;
		for (int i = 0; i < tlen; i++) NextCh();
	}
	
	// get the next token (possibly a token already seen during peeking)
	public Token Scan () {
		if (tokens.next == null) {
			return NextToken();
		} else {
			pt = tokens = tokens.next;
			return tokens;
		}
	}

	// get the next token, ignore pragmas
	public Token Peek () {
		do {
			if (pt.next == null) {
				pt.next = NextToken();
			}
			pt = pt.next;
		} while (pt.kind > maxT); // skip pragmas

		return pt;
	}

	// make sure that peeking starts at current scan position
	public void ResetPeek () { pt = tokens; }

} // end Scanner
