/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This benchmark is derived from https://github.com/newspeaklanguage/benchmarks
 * Originally ported to Java by Mario Wolczko, see http://www.wolczko.com/java_benchmarking.html
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

// Strengths are used to measure the relative importance of constraints. New
// strengths may be inserted in the strength hierarchy without disrupting
// current constraints. Strengths cannot be created outside this class, so
// pointer comparison can be used for value comparison.
class Strength {
  protected String name;
  protected int value;

  public Strength(String name, int value) {
    this.name = name;
    this.value = value;
  }

  public boolean strongerThan(Strength other) {
    return this.value < other.value;
  }

  public boolean weakerThan(Strength other) {
    return this.value > other.value;
  }

  public Strength strongest(Strength other) {
    return this.strongerThan(other) ? this : other;
  }

  public Strength weakest(Strength other) {
    return this.weakerThan(other) ? this : other;
  }

  static final Strength required = new Strength("required", 0);
  static final Strength strongPreferred = new Strength("strongPreferred", 1);
  static final Strength preferred = new Strength("preferred", 2);
  static final Strength strongDefault = new Strength("strongDefault", 3);
  static final Strength normal = new Strength("normal", 4);
  static final Strength weakDefault = new Strength("weakDefault", 5);
  static final Strength weakest = new Strength("weakest", 6);

  static final List<Strength> descendingStrengths = Arrays.asList(required,
      strongPreferred, preferred, strongDefault, normal, weakDefault, weakest);
}

class Direction {
  protected String name;
  public Direction(String name) {
    this.name = name;
  }

  static final Direction forward = new Direction("forward");
  static final Direction backward = new Direction("backward");
}


// I represent a constrained variable. In addition to my value, I maintain the
// structure of the constraint graph, the current dataflow graph, and various
// parameters of interest to the DeltaBlue incremental constraint solver.
class Variable {
  protected int value;
  protected List<Constraint> constraints = new ArrayList<>(2);
  protected Constraint determinedBy;
  protected int mark = 0;
  protected Strength walkStrength = Strength.weakest;
  protected boolean stay = true;
  protected String name;

  protected Variable(String name, int value) {
    this.name = name;
    this.value = value;
  }

  // Add the given constraint to the set of all constraints that refer to me.
  public void addConstraint(Constraint c) {
    constraints.add(c);
  }

  // Remove all traces of c from this variable.
  public void removeConstraint(Constraint c) {
    constraints.remove(c);
    if (determinedBy == c) determinedBy = null;
  }
}

// I am an abstract class representing a system-maintainable relationship (or
// "constraint") between a set of variables. I supply a strength instance
// variable; concrete subclasses provide a means of storing the constrained
// variables and other information required to represent a constraint.
abstract class Constraint {
  protected Strength strength;

  public Constraint(Strength strength) {
    this.strength = strength;
  }

  // Activate this constraint and attempt to satisfy it.
  public void addConstraint() {
    addToGraph();
    Planner.planner.incrementalAdd(this);
  }

  // Add myself to the constraint graph.
  public abstract void addToGraph();

  // Decide if I can be satisfied and record that decision. The output of the
  // chosen method must not have the given mark and must have a walkabout
  // strength less than that of this constraint.
  public abstract void chooseMethod(int mark);

  // Deactivate this constraint, remove it from the constraint graph, possibly
  // causing other constraints to be satisfied, and destroy it.
  public void destroyConstraint() {
    if (isSatisfied()) Planner.planner.incrementalRemove(this);
    removeFromGraph();
  }

  // Enforce this constraint. Assume that it is satisfied.
  public abstract void execute();

  // Assume that I am satisfied. Answer true if all my current inputs are
  // known. A variable is known if either a) it is 'stay' (i.e. it is a
  // constant at plan execution time), b) it has the given mark (indicating
  // that it has been computed by a constraint appearing earlier in the plan),
  // or c) it is not determined by any constraint.
  public abstract boolean inputsKnown(int mark);

  // Normal constraints are not input constraints. An input constraint is one
  // that depends on external state, such as the mouse, the keyboard, a clock,
  // or some arbitrary piece of imperative code.
  public boolean isInput() {
    return false;
  }

  // Answer true if this constraint is satisfied in the current solution.
  public abstract boolean isSatisfied();

  // Set the mark of all input from the given mark.
  public abstract void markInputs(int mark);

  // Record the fact that I am unsatisfied.
  public abstract void markUnsatisfied();

  // Answer my current output variable. Raise an error if I am not currently
  // satisfied.
  public abstract Variable output();

  // Calculate the walkabout strength, the stay flag, and, if it is 'stay', the
  // value for the current output of this constraint. Assume this constraint is
  // satisfied.
  public abstract void recalculate();

  // Remove myself from the constraint graph.
  public abstract void removeFromGraph();

  // Attempt to find a way to enforce this constraint. If successful, record
  // the solution, perhaps modifying the current dataflow graph. Answer the
  // constraint that this constraint overrides, if there is one, or nil, if
  // there isn't. Assume: I am not already satisfied.
  public Constraint satisfy(int mark) {
    chooseMethod(mark);
    if (!isSatisfied()) {
      if (strength == Strength.required)
          throw new RuntimeException("Could not satisfy a required constraint");
      return null;
    }
    // constraint can be satisfied
    // mark inputs to allow cycle detection in addPropagate
    markInputs(mark);
    Variable out = output();
    Constraint overridden = out.determinedBy;
    if (overridden != null) overridden.markUnsatisfied();
    out.determinedBy = this;
    if (!Planner.planner.addPropagate(this, mark))
        throw new RuntimeException("Cycle encountered");
    out.mark = mark;
    return overridden;
  }
}

// I am an abstract superclass for constraints having a single possible output
// variable.
abstract class UnaryConstraint extends Constraint {
  protected final Variable output;
  protected boolean satisfied = false;

  public UnaryConstraint(Variable output, Strength strength) {
    super(strength);
    this.output = output;
  }

  // Add myself to the constraint graph.
  @Override
  public void addToGraph() {
    output.addConstraint(this);
    satisfied = false;
  }

  // Add myself to the constraint graph.
  @Override
  public void chooseMethod(int mark) {
    satisfied = (output.mark != mark) &&
                strength.strongerThan(output.walkStrength);
  }

  @Override
  public boolean inputsKnown(int mark) {
    return true;
  }

  // Answer true if this constraint is satisfied in the current solution.
  @Override
  public boolean isSatisfied() {
    return satisfied;
  }

  // I have no inputs.
  @Override
  public void markInputs(int mark) {}

  // Record the fact that I am unsatisfied.
  @Override
  public void markUnsatisfied() {
    satisfied = false;
  }

  @Override
  public Variable output() {
    return output;
  }

  // Calculate the walkabout strength, the stay flag, and, if it is 'stay', the
  // value for the current output of this constraint. Assume this constraint
  // is satisfied.
  @Override
  public void recalculate() {
    output.walkStrength = strength;
    output.stay = !isInput();
    if (output.stay) execute(); // Stay optimization
  }

  // Remove myself from the constraint graph.
  @Override
  public void removeFromGraph() {
    if (output != null) output.removeConstraint(this);
    satisfied = false;
  }
}

// I am a unary input constraint used to mark a variable that the client wishes
// to change.
class EditConstraint extends UnaryConstraint {
  public EditConstraint(Variable v, Strength s) {
    super(v, s);
    addConstraint();
  }

  // Edit constraints do nothing.
  @Override
  public void execute() {}

  // I am a unary input constraint used to mark a variable that the client
  // wishes to change.
  @Override
  public boolean isInput() {
    return true;
  }
}

// I mark variables that should, with some level of preference, stay the same.
// I have one method with zero inputs and one output, which does nothing.
// Planners may exploit the fact that, if I am satisfied, my output will not
// change during plan execution. This is called "stay optimization".
class StayConstraint extends UnaryConstraint {
  public StayConstraint(Variable v, Strength s) {
    super(v, s);
    addConstraint();
  }

  // Stay constraints do nothing.
  @Override
  public void execute() {}
}

// I am an abstract superclass for constraints having two possible output variables.
abstract class BinaryConstraint extends Constraint {
  protected final Variable v1;
  protected final Variable v2;
  protected Direction direction;

  public BinaryConstraint(Variable v1, Variable v2, Strength s) {
    super(s);
    this.v1 = v1;
    this.v2 = v2;
  }

  // Add myself to the constraint graph.
  @Override
  public void addToGraph() {
    v1.addConstraint(this);
    v2.addConstraint(this);
    direction = null;
  }

  // Decide if I can be satisfied and which way I should flow based on the
  // relative strength of the variables I relate, and record that decision.
  @Override
  public void chooseMethod(int mark) {
    if (v1.mark == mark) {
      direction = (v2.mark != mark) && strength.strongerThan(v2.walkStrength)
	? Direction.forward
        : null;
      return;
    }
    if (v2.mark == mark) {
      direction = (v1.mark != mark) && strength.strongerThan(v1.walkStrength)
        ? Direction.backward
        : null;
      return;
    }
    // If we get here, neither variable is marked, so we have a choice.
    if (v1.walkStrength.weakerThan(v2.walkStrength)) {
      direction = strength.strongerThan(v1.walkStrength)
        ? Direction.backward
        : null;
    } else {
      direction = strength.strongerThan(v2.walkStrength)
        ? Direction.forward
        : null;
    }
  }

  // Answer my current input variable
  public Variable input() {
    return direction == Direction.forward ? v1 : v2;
  }

  @Override
  public boolean inputsKnown(int mark) {
    Variable i = input();
    return (i.mark == mark) || i.stay || (i.determinedBy == null);
  }

  // Answer true if this constraint is satisfied in the current solution.
  @Override
  public boolean isSatisfied() {
    return direction != null;
  }

  // Mark the input variable with the given mark.
  @Override
  public void markInputs(int mark) {
    input().mark = mark;
  }

  // Record the fact that I am unsatisfied.
  @Override
  public void markUnsatisfied() {
    direction = null;
  }

  // Answer my current output variable.
  @Override
  public Variable output() {
    return direction == Direction.forward ? v2 : v1;
  }

  // Calculate the walkabout strength, the stay flag, and, if it is 'stay', the
  // value for the current output of this constraint. Assume this constraint is
  // satisfied.
  @Override
  public void recalculate() {
    Variable i = input(), o = output();
    o.walkStrength = strength.weakest(i.walkStrength);
    o.stay = i.stay;
    if (o.stay) execute();
  }

  // Calculate the walkabout strength, the stay flag, and, if it is 'stay', the
  // value for the current output of this constraint. Assume this constraint is
  // satisfied.
  @Override
  public void removeFromGraph() {
    if (v1 != null) v1.removeConstraint(this);
    if (v2 != null) v2.removeConstraint(this);
    direction = null;
  }
}

// I constrain two variables to have the same value: "v1 = v2".
class EqualityConstraint extends BinaryConstraint {
  public EqualityConstraint(Variable v1, Variable v2, Strength s) {
    super(v1, v2, s);
    addConstraint();
  }

  // Enforce this constraint. Assume that it is satisfied.
  @Override
  public void execute() {
    output().value = input().value;
  }
}

// I relate two variables by the linear scaling relationship: "v2 = (v1 *
// scale) + offset". Either v1 or v2 may be changed to maintain this
// relationship but the scale factor and offset are considered read-only.
class ScaleConstraint extends BinaryConstraint {
  protected Variable scale;  // scale factor input variable
  protected Variable offset;  // offset input variable
  public ScaleConstraint(Variable src, Variable scale, Variable offset, Variable dest, Strength s) {
    super(src, dest, s);
    this.scale = scale;
    this.offset = offset;
    addConstraint();
  }

  // Add myself to the constraint graph.
  @Override
  public void addToGraph() {
    super.addToGraph();
    scale.addConstraint(this);
    offset.addConstraint(this);
  }

  // Enforce this constraint. Assume that it is satisfied.
  @Override
  public void execute() {
    if (direction == Direction.forward) {
      v2.value = v1.value * scale.value + offset.value;
    } else {
      v1.value = (v2.value - offset.value) / scale.value;
    }
  }

  // Mark the inputs from the given mark.
  @Override
  public void markInputs(int mark) {
    super.markInputs(mark);
    scale.mark = mark;
    offset.mark = mark;
  }

  // Calculate the walkabout strength, the stay flag, and, if it is 'stay', the
  // value for the current output of this constraint. Assume this constraint is
  // satisfied.
  @Override
  public void recalculate() {
    Variable i = input(), o = output();
    o.walkStrength = strength.weakest(i.walkStrength);
    o.stay = i.stay && scale.stay && offset.stay;
    if (o.stay) execute(); // stay optimization
  }

  // Remove myself from the constraint graph.
  @Override
  public void removeFromGraph() {
    super.removeFromGraph();
    if (scale != null) scale.removeConstraint(this);
    if (offset != null) offset.removeConstraint(this);
  }
}

// A Plan is an ordered list of constraints to be executed in sequence to
// resatisfy all currently satisfiable constraints in the face of one or more
// changing inputs.
class Plan {
  protected List<Constraint> constraints = new ArrayList<>();

  public Plan() { }

  public void addConstraint(Constraint c) {
    constraints.add(c);
  }

  // Execute my constraints in order.
  public void execute() {
    for (Constraint c: constraints) {
      c.execute();
    }
  }
}

// I embody the DeltaBlue algorithm described in:
// ''The DeltaBlue Algorithm: An Incremental Constraint Hierarchy Solver''
// by Bjorn N. Freeman-Benson and John Maloney.
// See January 1990 Communications of the ACM
// or University of Washington TR 89-08-06 for further details.
class Planner {
  protected int currentMark = 0;

  public void addConstraintsConsumingTo(Variable v, List<Constraint> list) {
    Constraint determining = v.determinedBy;
    for (Constraint c : v.constraints) {
      if (c != determining && c.isSatisfied()) list.add(c);
    }
  }

  // Recompute the walkabout strengths and stay flags of all variables
  // downstream of the given constraint and recompute the actual values of all
  // variables whose stay flag is true. If a cycle is detected, remove the
  // given constraint and answer false. Otherwise, answer true.
  // Details: Cycles are detected when a marked variable is encountered
  // downstream of the given constraint. The sender is assumed to have marked
  // the inputs of the given constraint with the given mark. Thus, encountering
  // a marked node downstream of the output constraint means that there is a
  // path from the constraint's output to one of its inputs.
  public boolean addPropagate(Constraint c, int mark) {
    List<Constraint> todo = new ArrayList<>();
    todo.add(c);
    while (!todo.isEmpty()) {
      Constraint d = todo.remove(todo.size()-1);
      if (d.output().mark == mark) {
        incrementalRemove(c);
        return false;
      }
      d.recalculate();
      addConstraintsConsumingTo(d.output(), todo);
    }
    return true;
  }

  // This is the standard DeltaBlue benchmark. A long chain of equality
  // constraints is constructed with a stay constraint on one end. An edit
  // constraint is then added to the opposite end and the time is measured for
  // adding and removing this constraint, and extracting and executing a
  // constraint satisfaction plan. There are two cases. In case 1, the added
  // constraint is stronger than the stay constraint and values must propagate
  // down the entire length of the chain. In case 2, the added constraint is
  // weaker than the stay constraint so it cannot be accommodated. The cost in
  // this case is, of course, very low. Typical situations lie somewhere
  // between these two extremes.
  @SuppressWarnings("unused")
  public void chainTest(int n) {
    Variable prev = null, first = null, last = null;
    for (int i = 1; i <= n; i++) {
      String name = "v"+i;
      Variable v = new Variable(name, 0);
      if (prev != null) new EqualityConstraint(prev, v, Strength.required);
      if (i == 1) first = v;
      if (i == n) last = v;
      prev = v;
    }

    new StayConstraint(last, Strength.strongDefault);
    Constraint editC = new EditConstraint(first, Strength.preferred);
    List<Constraint> editV = new ArrayList<>();
    editV.add(editC);
    Plan plan = extractPlanFromConstraints(editV);
    for (int i = 1; i <= 100; i++) {
      first.value = i;
      plan.execute();
      if (last.value != i) throw new RuntimeException("Chain test failed!");
    }
    editC.destroyConstraint();
  }

  // Extract a plan for resatisfaction starting from the outputs of the given
  // constraints, usually a set of input constraints.
  public Plan extractPlanFromConstraints(List<Constraint> constraints) {
    List<Constraint> sources = new ArrayList<>();
    for (Constraint c : constraints) {
      if (c.isInput() && c.isSatisfied()) sources.add(c);
    }
    return makePlan(sources);
  }

  // Attempt to satisfy the given constraint and, if successful, incrementally
  // update the dataflow graph.  Details: If satisfying the constraint is
  // successful, it may override a weaker constraint on its output. The
  // algorithm attempts to resatisfy that constraint using some other method.
  // This process is repeated until either a) it reaches a variable that was
  // not previously determined by any constraint or b) it reaches a constraint
  // that is too weak to be satisfied using any of its methods. The variables
  // of constraints that have been processed are marked with a unique mark
  // value so that we know where we've been. This allows the algorithm to avoid
  // getting into an infinite loop even if the constraint graph has an
  // inadvertent cycle.
  public void incrementalAdd(Constraint c) {
    int mark = newMark();
    Constraint overridden = c.satisfy(mark);
    while (overridden != null) {
      overridden = overridden.satisfy(mark);
    }
  }

  // Entry point for retracting a constraint. Remove the given constraint and
  // incrementally update the dataflow graph.
  // Details: Retracting the given constraint may allow some currently
  // unsatisfiable downstream constraint to be satisfied. We therefore collect
  // a list of unsatisfied downstream constraints and attempt to satisfy each
  // one in turn. This list is traversed by constraint strength, strongest
  // first, as a heuristic for avoiding unnecessarily adding and then
  // overriding weak constraints.
  // Assume: c is satisfied.
  public void incrementalRemove(Constraint c) {
    Variable out = c.output();
    c.markUnsatisfied();
    c.removeFromGraph();
    List<Constraint> unsatisfied = removePropagateFrom(out);
    for (Strength strength : Strength.descendingStrengths) {
      for (Constraint u : unsatisfied) {
        if (u.strength == strength) incrementalAdd(u);
      }
    }
  }

  // Extract a plan for resatisfaction starting from the given source
  // constraints, usually a set of input constraints. This method assumes that
  // stay optimization is desired; the plan will contain only constraints whose
  // output variables are not stay. Constraints that do no computation, such as
  // stay and edit constraints, are not included in the plan.
  // Details: The outputs of a constraint are marked when it is added to the
  // plan under construction. A constraint may be appended to the plan when all
  // its input variables are known. A variable is known if either a) the
  // variable is marked (indicating that has been computed by a constraint
  // appearing earlier in the plan), b) the variable is 'stay' (i.e. it is a
  // constant at plan execution time), or c) the variable is not determined by
  // any constraint. The last provision is for past states of history
  // variables, which are not stay but which are also not computed by any
  // constraint.
  // Assume: sources are all satisfied.
  public Plan makePlan(List<Constraint> sources) {
    int mark = newMark();
    Plan plan = new Plan();
    List<Constraint> todo = sources;
    while (!todo.isEmpty()) {
      Constraint c = todo.remove(todo.size()-1);
      if (c.output().mark != mark && c.inputsKnown(mark)) {
        // not in plan already and eligible for inclusion
        plan.addConstraint(c);
        c.output().mark = mark;
        addConstraintsConsumingTo(c.output(), todo);
      }
    }
    return plan;
  }

  // Select a previously unused mark value.
  public int newMark() {
    currentMark = currentMark + 1;
    return currentMark;
  }

  // This test constructs a two sets of variables related to each other by a
  // simple linear transformation (scale and offset). The time is measured to
  // change a variable on either side of the mapping and to change the scale
  // and offset factors.
  public void projectionTest(int n) {
    Variable src = null, dst = null;
    Variable scale = new Variable("scale", 10);
    Variable offset = new Variable("offset", 1000);
    List<Variable> dests = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      src = new Variable("src"+i, i);
      dst = new Variable("dst"+i, i);
      dests.add(dst);
      new StayConstraint(src, Strength.normal);
      new ScaleConstraint(src, scale, offset, dst, Strength.required);
    }

    setValue(src, 17);
    if (dst.value != 1170)
      throw new RuntimeException("Projection test 1 failed!");

    setValue(dst, 1050);
    if (src.value != 5)
      throw new RuntimeException("Projection test 2 failed!");

    setValue(scale, 5);
    for (int i = 0; i < n-1; i++) {
      if (dests.get(i).value != (i * 5 + 1000))
        throw new RuntimeException("Projection test 3 failed!");
    }

    setValue(offset, 2000);
    for (int i = 0; i < n-1; i++) {
      if (dests.get(i).value != (i * 5 + 2000))
        throw new RuntimeException("Projection test 4 failed!");
    }
  }

  // The given variable has changed. Propagate new values downstream.
  public void propagateFrom(Variable v) {
    List<Constraint> todo = new ArrayList<>();
    addConstraintsConsumingTo(v, todo);
    while (!todo.isEmpty()) {
      Constraint c = todo.remove(todo.size()-1);
      c.execute();
      addConstraintsConsumingTo(c.output(), todo);
    }
  }

  // Update the walkabout strengths and stay flags of all variables downstream
  // of the given constraint. Answer a collection of unsatisfied constraints
  // sorted in order of decreasing strength.
  public List<Constraint> removePropagateFrom(Variable out) {
    out.determinedBy = null;
    out.walkStrength = Strength.weakest;
    out.stay = true;
    List<Constraint> unsatisfied = new ArrayList<>();
    List<Variable> todo = new ArrayList<>();
    todo.add(out);
    while (!todo.isEmpty()) {
      Variable v = todo.remove(todo.size()-1);
      for (Constraint c : v.constraints) {
        if (!c.isSatisfied()) unsatisfied.add(c);
      }
      Constraint determining = v.determinedBy;
      for (Constraint nextC : v.constraints) {
        if (nextC != determining && nextC.isSatisfied()) {
          nextC.recalculate();
          todo.add(nextC.output());
        }
      }
    }
    return unsatisfied;
  }

  public void setValue(Variable v, int newValue) {
    Constraint editC = new EditConstraint(v, Strength.preferred);
    List<Constraint> editV = new ArrayList<>();
    editV.add(editC);
    Plan plan = extractPlanFromConstraints(editV);
    for (int i = 0; i < 10; i++) {
       v.value = newValue;
       plan.execute();
    }
    editC.destroyConstraint();
  }

  static Planner planner; // = new Planner();
}


public final class DeltaBlue {  

  public static void run() {
    Planner.planner = new Planner();
    Planner.planner.chainTest(1000);
    Planner.planner.projectionTest(1000);
  }

  public static void main(String[] args) {
    run();
  }
}
