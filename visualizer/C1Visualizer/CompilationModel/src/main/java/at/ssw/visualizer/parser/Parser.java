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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.List;

import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.modelimpl.CompilationImpl;
import at.ssw.visualizer.modelimpl.CompilationModelImpl;
import at.ssw.visualizer.modelimpl.cfg.ControlFlowGraphImpl;
import at.ssw.visualizer.modelimpl.cfg.IRInstructionImpl;
import at.ssw.visualizer.modelimpl.cfg.StateImpl;
import at.ssw.visualizer.modelimpl.cfg.StateEntryImpl;
import at.ssw.visualizer.modelimpl.interval.IntervalListImpl;
import at.ssw.visualizer.modelimpl.interval.RangeImpl;
import at.ssw.visualizer.modelimpl.interval.UsePositionImpl;
import at.ssw.visualizer.modelimpl.nc.NativeMethodImpl;
import at.ssw.visualizer.modelimpl.bc.BytecodesImpl;



public class Parser {
	public static final int _EOF = 0;
	public static final int _ident = 1;
	public static final int maxT = 54;

        static final boolean _T = true;
        static final boolean _x = false;
	static final int minErrDist = 2;

	public Token t;    // last recognized token
	public Token la;   // lookahead token
	int errDist = minErrDist;
	
	public Scanner scanner;
	public Errors errors;
	
	public boolean hasErrors() {
		return errors.errors.size() > 0;
	}

	public String getErrors() {
		StringBuilder sb = new StringBuilder();
		for (String s : errors.errors) {
			sb.append(s);
			sb.append('\n');
		}
		return sb.toString();
	}
	
	
	private CompilationModelImpl compilationModel;

public void setCompilationModel(CompilationModelImpl compilationModel) {
    this.compilationModel = compilationModel;
}

private String simpleName(String className) {
    int index = className.lastIndexOf('.');
    if (index < 0) {
        return className;
    }
    return className.substring(index + 1);
}

private String shortName(String name) {
    name = longName(name);
    String params = "";

    int openParam = name.indexOf('(');
    if (openParam >= 0) {
        int closeParam = name.indexOf(')', openParam);
        if (closeParam >= 0) {
            String[] parts = name.substring(openParam + 1, closeParam).split(", *");
            for (int i = 0; i < parts.length; i++) {
                if (!params.isEmpty()) {
                    params += ",";
                }
                params += simpleName(parts[i]);
            }
            params = "(" + params + ")";
        }
        name = name.substring(0, openParam);
    }

    int methodPoint = name.lastIndexOf(".");
    if (methodPoint < 0) {
        return name + params;
    }
    int classPoint = name.lastIndexOf(".", methodPoint - 1);
    if (classPoint < 0) {
        return name + params;
    }
    return name.substring(classPoint + 1) + params;
}

private String longName(String name) {
    return name.replace("::", ".");
}



	public Parser(Scanner scanner) {
		this.scanner = scanner;
		errors = new Errors();
	}

	void SynErr (int n) {
		if (errDist >= minErrDist) errors.SynErr(la.line, la.col, n);
		errDist = 0;
	}

	public void SemErr (String msg) {
		if (errDist >= minErrDist) errors.SemErr(t.line, t.col, msg);
		errDist = 0;
	}
	
	void Get () {
		for (;;) {
			t = la;
			la = scanner.Scan();
			if (la.kind <= maxT) { ++errDist; break; }

			la = t;
		}
	}
	
	void Expect (int n) {
		if (la.kind==n) Get(); else { SynErr(n); }
	}
	
	boolean StartOf (int s) {
		return set[s][la.kind];
	}
	
	void ExpectWeak (int n, int follow) {
		if (la.kind == n) Get();
		else {
			SynErr(n);
			while (!StartOf(follow)) Get();
		}
	}
	
	boolean WeakSeparator (int n, int syFol, int repFol) {
		int kind = la.kind;
		if (kind == n) { Get(); return true; }
		else if (StartOf(repFol)) return false;
		else {
			SynErr(n);
			while (!(set[syFol][kind] || set[repFol][kind] || set[0][kind])) {
				Get();
				kind = la.kind;
			}
			return StartOf(syFol);
		}
	}
	
	void InputFile() {
		CompilationHelper lastComp = null;
		ControlFlowGraphImpl lastCFG = null;
		IntervalListImpl lastIL = null; 
		while (StartOf(1)) {
			if (la.kind == 2) {
				CompilationHelper curComp = Compilation();
				if (lastComp != null) compilationModel.addCompilation(lastComp.resolve());
				lastComp = curComp; 
			} else if (la.kind == 7) {
				lastCFG = CFG(lastComp);
			} else if (la.kind == 46) {
				lastIL = IntervalList(lastCFG);
				lastComp.elements.add(lastIL); 
			} else if (la.kind == 49) {
				NativeMethod(lastCFG);
			} else {
				Bytecodes(lastCFG);
			}
		}
		if (lastComp != null) compilationModel.addCompilation(lastComp.resolve()); 
	}

	CompilationHelper  Compilation() {
		CompilationHelper  helper;
		helper = new CompilationHelper(); 
		Expect(2);
		Expect(3);
		String name = StringValue();
		helper.name = longName(name); helper.shortName = shortName(name); 
		Expect(4);
		String method = StringValue();
		helper.method = longName(method); 
		Expect(5);
		helper.date = DateValue();
		Expect(6);
		return helper;
	}

	ControlFlowGraphImpl  CFG(CompilationHelper lastComp) {
		ControlFlowGraphImpl  res;
		CFGHelper helper = new CFGHelper(); 
		Expect(7);
		Expect(3);
		String name = StringValue();
		helper.name = longName(name); helper.shortName = shortName(name); 
		if (la.kind == 8) {
			Get();
			helper.id = IntegerValue();
			Expect(9);
			helper.parentId = IntegerValue();
		}
		while (la.kind == 11) {
			BBHelper basicBlock = BasicBlock();
			helper.add(basicBlock); 
		}
		res = helper.resolve(lastComp, this); 
		Expect(10);
		return res;
	}

	IntervalListImpl  IntervalList(ControlFlowGraph controlFlowGraph) {
		IntervalListImpl  res;
		IntervalListHelper helper = new IntervalListHelper(); IntervalHelper interval; 
		if (controlFlowGraph == null) SemErr("must have CFG before intervals"); 
		Expect(46);
		Expect(3);
		String name = StringValue();
		helper.name = longName(name); helper.shortName = shortName(name); 
		while (la.kind == 1) {
			interval = Interval();
			helper.add(interval); 
		}
		res = helper.resolve(this, controlFlowGraph); 
		Expect(47);
		return res;
	}

	void NativeMethod(ControlFlowGraphImpl cfg) {
		Expect(49);
		String res = NoTrimFreeValue();
		cfg.setNativeMethod(new NativeMethodImpl(cfg, res)); 
		Expect(50);
	}

	void Bytecodes(ControlFlowGraphImpl cfg) {
		Expect(51);
		String res = FreeValue();
		cfg.setBytecodes(new BytecodesImpl(cfg, res)); 
		Expect(52);
	}

	String  StringValue() {
		String  res;
		Expect(53);
		long beg = la.pos;
		while (StartOf(2)) {
			Get();
		}
		res = scanner.buffer.GetString(beg, la.pos).trim().intern(); 
		Expect(53);
		return res;
	}

	Date  DateValue() {
		Date  res;
		res = null; 
		Expect(1);
		try { res = new Date(Long.parseLong(t.val)); } catch (NumberFormatException ex) { SemErr(t.val); } 
		return res;
	}

	int  IntegerValue() {
		int  res;
		res = 0; 
		Expect(1);
		try { res = Integer.parseInt(t.val); } catch (NumberFormatException ex) { SemErr(t.val); } 
		return res;
	}

	BBHelper  BasicBlock() {
		BBHelper  helper;
		helper = new BBHelper(); 
		Expect(11);
		Expect(3);
		helper.name = StringValue();
		Expect(12);
		helper.fromBci = IntegerValue();
		Expect(13);
		helper.toBci = IntegerValue();
		Expect(14);
		helper.predecessors = StringList();
		Expect(15);
		helper.successors = StringList();
		Expect(16);
		helper.xhandlers = StringList();
		Expect(17);
		helper.flags = StringList();
		if (la.kind == 18) {
			Get();
			helper.dominator = StringValue();
		}
		if (la.kind == 19) {
			Get();
			helper.loopIndex = IntegerValue();
		}
		if (la.kind == 20) {
			Get();
			helper.loopDepth = IntegerValue();
		}
		if (la.kind == 21) {
			Get();
			helper.firstLirId = IntegerValue();
		}
		if (la.kind == 22) {
			Get();
			helper.lastLirId = IntegerValue();
		}
		if (la.kind == 23) {
			Get();
			helper.probability = DoubleValue();
		}
		if (la.kind == 25) {
			StateList(helper);
		}
		if (la.kind == 36) {
			HIR(helper);
		}
		if (la.kind == 39) {
			LIR(helper);
		}
		while (la.kind == 41) {
			IR(helper);
		}
		Expect(24);
		return helper;
	}

	String[]  StringList() {
		String[]  res;
		ArrayList<String> list = new ArrayList<String>(); String item; 
		while (la.kind == 53) {
			item = StringValue();
			list.add(item); 
		}
		res = list.toArray(new String[list.size()]); 
		return res;
	}

	double  DoubleValue() {
		double  res;
		res = Double.NaN; 
		Expect(1);
		try { res = Double.longBitsToDouble(Long.parseLong(t.val)); } catch (NumberFormatException ex) { SemErr(t.val); } 
		return res;
	}

	void StateList(BBHelper helper) {
		Expect(25);
		while (la.kind == 26 || la.kind == 28 || la.kind == 30) {
			if (la.kind == 26) {
				Get();
				StateImpl state = State("Operands");
				helper.states.add(state); 
				Expect(27);
			} else if (la.kind == 28) {
				Get();
				StateImpl state = State("Locks");
				helper.states.add(state); 
				Expect(29);
			} else {
				Get();
				StateImpl state = State("Locals");
				helper.states.add(state); 
				Expect(31);
			}
		}
		Expect(32);
	}

	void HIR(BBHelper helper) {
		Expect(36);
		while (la.kind == 1 || la.kind == 38) {
			IRInstructionImpl ins = HIRInstruction();
			helper.hirInstructions.add(ins); 
		}
		Expect(37);
	}

	void LIR(BBHelper helper) {
		Expect(39);
		while (la.kind == 1) {
			IRInstructionImpl op = LIROperation();
			helper.lirOperations.add(op); 
		}
		Expect(40);
	}

	void IR(BBHelper helper) {
		Expect(41);
		if (la.kind == 42) {
			Get();
			while (la.kind == 1 || la.kind == 45) {
				IRInstructionImpl op = IRInstruction();
				helper.hirInstructions.add(op); 
			}
		} else if (la.kind == 43) {
			Get();
			while (la.kind == 1 || la.kind == 45) {
				IRInstructionImpl op = IRInstruction();
				helper.lirOperations.add(op); 
			}
		} else SynErr(55);
		Expect(44);
	}

	StateImpl  State(String kind) {
		StateImpl  res;
		String method = ""; ArrayList<StateEntryImpl> entries = new ArrayList<StateEntryImpl>(); 
		Expect(33);
		int size = IntegerValue();
		if (la.kind == 4) {
			Get();
			method = StringValue();
		}
		while (la.kind == 1) {
			StateEntryImpl entry = StateEntry();
			entries.add(entry); 
		}
		res = new StateImpl(kind, size, longName(method), entries.toArray(new StateEntryImpl[entries.size()])); 
		return res;
	}

	StateEntryImpl  StateEntry() {
		StateEntryImpl  res;
		String[] operands = null; String operand = null; 
		int index = IntegerValue();
		String name = HIRName();
		if (la.kind == 34) {
			Get();
			ArrayList<String> operandsList = new ArrayList<String>(); 
			while (la.kind == 1) {
				String opd = HIRName();
				operandsList.add(opd); 
			}
			Expect(35);
			operands = operandsList.toArray(new String[operandsList.size()]); 
		}
		if (la.kind == 53) {
			operand = StringValue();
		}
		res = new StateEntryImpl(index, name, operands, operand); 
		return res;
	}

	String  HIRName() {
		String  res;
		res = IdentValue();
		if (res.charAt(0) >= '0' && res.charAt(0) <= '9') { res = "v" + res; res = res.intern(); } 
		return res;
	}

	IRInstructionImpl  HIRInstruction() {
		IRInstructionImpl  res;
		String pinned = ""; String operand = null; 
		if (la.kind == 38) {
			Get();
			pinned = "."; 
		}
		int bci = IntegerValue();
		int useCount = IntegerValue();
		if (la.kind == 53) {
			operand = StringValue();
		}
		String name = HIRName();
		String text = FreeValue();
		res = new IRInstructionImpl(pinned, bci, useCount, name, text, operand); 
		return res;
	}

	String  FreeValue() {
		String  res;
		long beg = la.pos;
		while (StartOf(3)) {
			Get();
		}
		res = scanner.buffer.GetString(beg, la.pos).trim(); if (res.indexOf('\r') != -1) { res = res.replace("\r\n", "\n"); } res = res.intern(); 
		Expect(45);
		return res;
	}

	String  IdentValue() {
		String  res;
		Expect(1);
		res = t.val.trim().intern(); 
		return res;
	}

	IRInstructionImpl  LIROperation() {
		IRInstructionImpl  res;
		int number = IntegerValue();
		String text = FreeValue();
		res = new IRInstructionImpl(number, text); 
		return res;
	}

	IRInstructionImpl  IRInstruction() {
		IRInstructionImpl  res;
		LinkedHashMap<String, String> data = new LinkedHashMap<String, String>(); 
		while (la.kind == 1) {
			String name = IdentValue();
			String value = FreeValue();
			data.put(name.intern(), value.intern()); 
		}
		Expect(45);
		res = new IRInstructionImpl(data); 
		return res;
	}

	IntervalHelper  Interval() {
		IntervalHelper  helper;
		helper = new IntervalHelper(); RangeImpl range; UsePositionImpl usePosition; 
		helper.regNum = IdentValue();
		helper.type = IdentValue();
		if (la.kind == 53) {
			helper.operand = StringValue();
		}
		helper.splitParent = IdentValue();
		helper.registerHint = IdentValue();
		range = Range();
		helper.ranges.add(range); 
		while (la.kind == 34) {
			range = Range();
			helper.ranges.add(range); 
		}
		while (la.kind == 1) {
			usePosition = UsePosition();
			helper.usePositions.add(usePosition); 
		}
		helper.spillState = StringValue();
		return helper;
	}

	RangeImpl  Range() {
		RangeImpl  res;
		Expect(34);
		int from = IntegerValue();
		Expect(48);
		int to = IntegerValue();
		Expect(34);
		res = new RangeImpl(from, to); 
		return res;
	}

	UsePositionImpl  UsePosition() {
		UsePositionImpl  res;
		int position = IntegerValue();
		String kindStr = IdentValue();
		res = new UsePositionImpl(position, kindStr.charAt(0)); 
		return res;
	}

	String  NoTrimFreeValue() {
		String  res;
		long beg = la.pos;
		while (StartOf(3)) {
			Get();
		}
		res = scanner.buffer.GetString(beg, la.pos); if (res.indexOf('\r') != -1) { res = res.replace("\r\n", "\n"); } res = res.intern(); 
		Expect(45);
		return res;
	}



	public void Parse() {
		la = new Token();
		la.val = "";		
		Get();
		InputFile();
		Expect(0);

		Expect(0);
	}

	private boolean[][] set = {
		{_T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x},
		{_x,_x,_T,_x, _x,_x,_x,_T, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_T,_x, _x,_T,_x,_T, _x,_x,_x,_x},
		{_x,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_x,_T,_x},
		{_x,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_x,_T,_T, _T,_T,_T,_T, _T,_T,_T,_x}

	};
} // end Parser


class Errors {
	public String errMsgFormat = "-- line {0} col {1}: {2}"; // 0=line, 1=column, 2=text
        public ArrayList<String> errors = new ArrayList<String>();
	
	protected void printMsg(int line, int column, String msg) {
		StringBuffer b = new StringBuffer(errMsgFormat);
		int pos = b.indexOf("{0}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, line); }
		pos = b.indexOf("{1}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, column); }
		pos = b.indexOf("{2}");
		if (pos >= 0) b.replace(pos, pos+3, msg);
                printMsg(b.toString());
        }

        protected void printMsg(String msg) {
                if (errors.size() < 10) {
                    errors.add(msg);
                }
	}
	
	public void SynErr (int line, int col, int n) {
		String s;
		switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "ident expected"; break;
			case 2: s = "\"begin_compilation\" expected"; break;
			case 3: s = "\"name\" expected"; break;
			case 4: s = "\"method\" expected"; break;
			case 5: s = "\"date\" expected"; break;
			case 6: s = "\"end_compilation\" expected"; break;
			case 7: s = "\"begin_cfg\" expected"; break;
			case 8: s = "\"id\" expected"; break;
			case 9: s = "\"caller_id\" expected"; break;
			case 10: s = "\"end_cfg\" expected"; break;
			case 11: s = "\"begin_block\" expected"; break;
			case 12: s = "\"from_bci\" expected"; break;
			case 13: s = "\"to_bci\" expected"; break;
			case 14: s = "\"predecessors\" expected"; break;
			case 15: s = "\"successors\" expected"; break;
			case 16: s = "\"xhandlers\" expected"; break;
			case 17: s = "\"flags\" expected"; break;
			case 18: s = "\"dominator\" expected"; break;
			case 19: s = "\"loop_index\" expected"; break;
			case 20: s = "\"loop_depth\" expected"; break;
			case 21: s = "\"first_lir_id\" expected"; break;
			case 22: s = "\"last_lir_id\" expected"; break;
			case 23: s = "\"probability\" expected"; break;
			case 24: s = "\"end_block\" expected"; break;
			case 25: s = "\"begin_states\" expected"; break;
			case 26: s = "\"begin_stack\" expected"; break;
			case 27: s = "\"end_stack\" expected"; break;
			case 28: s = "\"begin_locks\" expected"; break;
			case 29: s = "\"end_locks\" expected"; break;
			case 30: s = "\"begin_locals\" expected"; break;
			case 31: s = "\"end_locals\" expected"; break;
			case 32: s = "\"end_states\" expected"; break;
			case 33: s = "\"size\" expected"; break;
			case 34: s = "\"[\" expected"; break;
			case 35: s = "\"]\" expected"; break;
			case 36: s = "\"begin_HIR\" expected"; break;
			case 37: s = "\"end_HIR\" expected"; break;
			case 38: s = "\".\" expected"; break;
			case 39: s = "\"begin_LIR\" expected"; break;
			case 40: s = "\"end_LIR\" expected"; break;
			case 41: s = "\"begin_IR\" expected"; break;
			case 42: s = "\"HIR\" expected"; break;
			case 43: s = "\"LIR\" expected"; break;
			case 44: s = "\"end_IR\" expected"; break;
			case 45: s = "\"<|@\" expected"; break;
			case 46: s = "\"begin_intervals\" expected"; break;
			case 47: s = "\"end_intervals\" expected"; break;
			case 48: s = "\",\" expected"; break;
			case 49: s = "\"begin_nmethod\" expected"; break;
			case 50: s = "\"end_nmethod\" expected"; break;
			case 51: s = "\"begin_bytecodes\" expected"; break;
			case 52: s = "\"end_bytecodes\" expected"; break;
			case 53: s = "\"\\\"\" expected"; break;
			case 54: s = "??? expected"; break;
			case 55: s = "invalid IR"; break;
			default: s = "error " + n; break;
		}
		printMsg(line, col, s);
	}

	public void SemErr (int line, int col, String s) {	
		printMsg(line, col, s);
	}
	
	public void SemErr (String s) {
		printMsg(s);
	}
	
	public void Warning (int line, int col, String s) {	
		printMsg(line, col, s);
	}
	
	public void Warning (String s) {
		printMsg(s);
	}
} // Errors


class FatalError extends RuntimeException {
	public FatalError(String s) { super(s); }
}
