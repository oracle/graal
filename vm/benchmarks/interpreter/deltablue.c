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


/* This is an implementation of the DeltaBlue incremental dataflow
   constraint solver written in portable C.

   The original version was by John Maloney.
   This version was modified for portability and benchmarking
   by Mario Wolczko, Sun Microsystems Labs, 2 Oct 96.
   */

#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>

typedef enum {false, true} Boolean;
typedef	void (*Proc)();
typedef	void * Element;

/*
 List: Supports variable sized, ordered lists of elements.  */

typedef struct {
  Element	*slots;		/* variable-sized array of element slots */
  int		slotCount;	/* number of slots currently allocated */
  int		first;		/* index of first element */
  int		last;		/* index of last element (first-1, if empty) */
} *List, ListStruct;

/*
  Constraint, variable, and strength data definitions for DeltaBlue.
  */

/* Strength Constants */
typedef enum {
  S_required= 0,
  S_strongPreferred= 1,
  S_preferred= 2,
  S_strongDefault= 3,
  S_default= 4,
  S_weakDefault= 5,
  S_weakest= 6
} Strength;

struct constraint;

typedef struct {
  long		value;
  List		constraints;
  struct constraint* determinedBy;
  long		mark;
  Strength	walkStrength;
  Boolean	stay;
  char		name[10];
} *Variable, VariableStruct;

typedef struct constraint {
  Proc		execute;
  Boolean	inputFlag;
  Strength      strength;
  char		whichMethod;
  char		methodCount;
  char		varCount;
  char		methodOuts[7];
  Variable	variables[1];
} *Constraint, ConstraintStruct;

/* Other Constants and Macros */
#define NO_METHOD	(-1)
#define SATISFIED(c)	((c)->whichMethod != NO_METHOD)
#define Weaker(a,b)	(a > b)

/*
    Implementation of List
    Invariants and relationships:
	slots != NULL
	slotCount > 0
	sizeof(*slots) == slotCount * sizeof(Element)
	0 <= first < slotCount
	-1 <= last < slotCount
	last >= first (if not empty)
	last == first - 1 (if empty)
	NumberOfItems == (last - first) + 1
	*/

/* Private Prototypes */
void Error(char*);
void Grow(List);
void MakeRoom(List);
char* StrengthString(Strength strength);

/* Variables */
Variable	Variable_Create(char *, long);
Variable	Variable_CreateConstant(char *, long);
void		Variable_Destroy(Variable);
void		Variable_Print(Variable);

/* Constraints */
Constraint	Constraint_Create(int, Strength);
void		Constraint_Destroy(Constraint);
void		Constraint_Print(Constraint);

/* Miscellaneous */
void		ExecutePlan(List);

Constraint StayC(Variable v, Strength); /* keep v constant */
Constraint EditC(Variable v, Strength); /* change v */
Constraint EqualsC(Variable a, Variable b, Strength); /* a = b */
/* (src * scale) + offset = dest*/
Constraint ScaleOffsetC(Variable src, Variable scale, Variable offset,
			Variable dest, Strength);

void	InitDeltaBlue(void);
void	AddVariable(Variable);
void	DestroyVariable(Variable);
void	AddConstraint(Constraint);
void	DestroyConstraint(Constraint);
List	ExtractPlan(void);
List	ExtractPlanFromConstraint(Constraint);
List	ExtractPlanFromConstraints(List);



/****** Create and Destruction ******/

List List_Create(int initialCount)
{
  List newList;

  newList = (List) malloc(sizeof(ListStruct));
  if (newList == NULL) Error("out of memory");
  newList->slots = (Element *) malloc(initialCount * sizeof(Element));
  if (newList->slots == NULL) Error("out of memory");
  newList->slotCount = initialCount;
  newList->first = 0;
  newList->last = -1;
  return newList;
}

void List_Destroy(List list)
{
  if (list->slots == NULL) Error("bad ListStruct; already freed?");
  free(list->slots);
  list->slots = NULL;
  list->slotCount = 0;
  list->first = 0;
  list->last = -1;
  free(list);
}

/****** Enumeration and Queries ******/

void List_Do(List list, Proc proc)
{
  Element *nextPtr = &(list->slots[list->first]);
  Element *lastPtr = &(list->slots[list->last]);

  while (nextPtr <= lastPtr) {
    (*proc)(*nextPtr++);
  }
}

int List_Size(List list)
{
  return (list->last - list->first) + 1;
}

void* List_At(List list, int index)
{
  if (index < 0 || index > list->last - list->first + 1)
    Error("List access out of bounds");
  return list->slots[index + list->first];
}

/****** Adding ******/

void List_Add(List list, Element element)
{
  if (list->last >= (list->slotCount - 1)) MakeRoom(list);
  list->slots[++list->last] = element;
}

void List_Append(List list1, List list2)
{
  Element *nextPtr = &(list2->slots[list2->first]);
  Element *lastPtr = &(list2->slots[list2->last]);

  while (nextPtr <= lastPtr) {
    List_Add(list1, *nextPtr++);
  }
}

/****** Removing ******/

void List_Remove(List list, Element element)
{
  Element *srcPtr = &list->slots[list->first];
  Element *destPtr = &list->slots[0];
  Element *lastPtr = &list->slots[list->last];

  list->last = list->last - list->first;
  list->first = 0;
  while (srcPtr <= lastPtr) {
    if (*srcPtr == element) {
      list->last--;
    } else {
      *destPtr++ = *srcPtr;
    }
    srcPtr++;
  }
}

Element List_RemoveFirst(List list)
{
  Element element;

  if (list->last < list->first) return NULL;
  element = list->slots[list->first++];
  return element;
}

void List_RemoveAll(List list)
{
  list->first = 0;
  list->last = -1;
}

/****** Private ******/

#define max(x, y) ((x) > (y) ? (x) : (y))
#define min(x, y) ((x) < (y) ? (x) : (y))

void Error(char *errorString)
{
  printf("error: %s.\n", errorString);
  exit(-1);
}

void Grow(List list)
{
  list->slotCount += min(max(list->slotCount, 2), 512);
  list->slots = realloc(list->slots, (list->slotCount * sizeof(Element)));
  if (list->slots == NULL) Error("out of memory");
}

void MakeRoom(List list)
{
  Element *srcPtr = &list->slots[list->first];
  Element *destPtr = &list->slots[0];
  Element *lastPtr = &list->slots[list->last];

  if (((list->last - list->first) + 1) >= list->slotCount) Grow(list);
  if (list->first == 0) return;
  while (srcPtr <= lastPtr) {
    *destPtr++ = *srcPtr++;
  }
  list->last = list->last - list->first;
  list->first = 0;
}

/*
  Constraint, variable, and other operations for DeltaBlue.
  */

/******* Private *******/

void Execute(Constraint c)
{
  c->execute(c);
}

void Noop(Constraint c)
{
  /* default execute procedure; does nothing */
};

/******* Variables *******/

Variable Variable_Create(char *name, long initialValue)
{
  Variable new;

  new = (Variable) malloc(sizeof(VariableStruct));
  if (new == NULL) Error("out of memory");
  new->value = initialValue;
  new->constraints = List_Create(2);
  new->determinedBy = NULL;
  new->mark = 0;
  new->walkStrength = S_weakest;
  new->stay = true;
  strncpy(new->name, name, 10);
  new->name[9] = 0;
  AddVariable(new);
  return new;
}

Variable Variable_CreateConstant(char *name, long value)
{
  Variable new;

  new = (Variable) malloc(sizeof(VariableStruct));
  if (new == NULL) Error("out of memory");
  new->value = value;
  new->constraints = List_Create(0);
  new->determinedBy = NULL;
  new->mark = 0;
  new->walkStrength = S_required;
  new->stay = true;
  strncpy(new->name, name, 10);
  new->name[9] = 0;
  AddVariable(new);
  return new;
}

void Variable_Destroy(Variable v)
{
  if (v->constraints == NULL) {
    Error("bad VariableStruct; already freed?");
  }
  List_Destroy(v->constraints);
  v->constraints = NULL;
  free(v);
}

void Variable_Print(Variable v)
{
  printf(
      "%s(%s,%ld)",
      v->name, StrengthString(v->walkStrength), v->value);
}

/******* Constraints *******/

Constraint Constraint_Create(int variableCount, Strength strength)
{
  Constraint new;
  int i;

  new = (Constraint) malloc(sizeof(ConstraintStruct)
			    + ((variableCount - 1) * sizeof(Variable)));
  if (new == NULL) Error("out of memory");
  new->execute = Noop;
  new->inputFlag = false;
  new->strength = strength;
  new->whichMethod = NO_METHOD;
  new->methodCount = 0;
  for (i = 0; i < 7; i++) {
    new->methodOuts[i] = 0;
  }
  new->varCount = variableCount;
  for (i = 0; i < new->varCount; i++) {
    new->variables[i] = NULL;
  }
  return new;
}

void Constraint_Destroy(Constraint c)
{
  if (c->execute == NULL) {
    Error("bad ConstraintStruct; already freed?");
  }
  c->execute = NULL;
  free(c);
}

void Constraint_Print(Constraint c)
{
  int i, outIndex;

  if (!SATISFIED(c)) {
    printf("Unsatisfied(");
    for (i = 0; i < c->varCount; i++) {
      Variable_Print(c->variables[i]);
      printf(" ");
    }
    printf(")");
  } else {
    outIndex = c->methodOuts[c->whichMethod];
    printf("Satisfied(");
    for (i = 0; i < c->varCount; i++) {
      if (i != outIndex) {
	Variable_Print(c->variables[i]);
	printf(" ");
      }
    }
    printf("-> ");
    Variable_Print(c->variables[outIndex]);
    printf(")");
  }
  printf("\n");
}

/******* Miscellaneous Functions *******/

char* StrengthString(Strength strength)
{
  static char temp[20];

  switch (strength) {
  case S_required:		return "required";
  case S_strongPreferred:	return "strongPreferred";
  case S_preferred:		return "preferred";
  case S_strongDefault:   	return "strongDefault";
  case S_default:		return "default";
  case S_weakDefault:		return "weakDefault";
  case S_weakest:		return "weakest";
  default:
				sprintf(temp, "strength[%d]", strength);
				return temp;
  }
}

void ExecutePlan(List list)
{
  List_Do(list, Execute);
}


/*

    DeltaBlue, an incremental dataflow constraint solver.

    */

/******* Private Macros and Prototypes *******/

#define OUT_VAR(c)	(c->variables[c->methodOuts[c->whichMethod]])

void		FreeVariable(Variable);
void		AddIfSatisfiedInput(Constraint);
void		CollectSatisfiedInputs(Variable);
List		MakePlan(void);
void		IncrementalAdd(Constraint);
void		AddAtStrength(Constraint);
void		IncrementalRemove(Constraint);
Boolean		AddPropagate(Constraint);
void		CollectUnsatisfied(Constraint);
void		RemovePropagateFrom(Variable);
Constraint	Satisfy(Constraint);
int		ChooseMethod(Constraint);
void		Recalculate(Constraint);
int		OutputWalkStrength(Constraint);
Boolean		ConstantOutput(Constraint);
Boolean		InputsKnown(Constraint);
void		NewMark(void);
void		Error(char *);
Constraint	NextDownstreamConstraint(List, Variable);

/******* DeltaBlue Globals *******/

List allVariables = NULL;
long currentMark = 0;

/******** Public: Initialization *******/

void InitDeltaBlue(void)
{
  Variable v;

  if (allVariables == NULL) allVariables = List_Create(128);
  v = (Variable) List_RemoveFirst(allVariables);
  while (v != NULL) {
    FreeVariable(v);
    v = (Variable) List_RemoveFirst(allVariables);
  }
  List_RemoveAll(allVariables);
  currentMark = 0;
}

/* this is used when we know we are going to throw away all variables */
void FreeVariable(Variable v)
{
  Constraint c;
  int i;

  c = (Constraint) List_RemoveFirst(v->constraints);
  while (c != NULL) {
    for (i = c->varCount - 1; i >= 0; i--) {
      List_Remove((c->variables[i])->constraints, (Element) c);
    }
    Constraint_Destroy(c);
    c = (Constraint) List_RemoveFirst(v->constraints);
  }
  Variable_Destroy(v);
}

/******** Public: Variables and Constraints *******/

void AddVariable(Variable v)
{
  List_Add(allVariables, v);
}

void DestroyConstraint(Constraint c);

void DestroyVariable(Variable v)
{
  Constraint c;

  c = (Constraint) List_RemoveFirst(v->constraints);
  while (c != NULL) {
    DestroyConstraint(c);
    c = (Constraint) List_RemoveFirst(v->constraints);
  }
  List_Remove(allVariables, v);
  Variable_Destroy(v);
}

void AddConstraint(Constraint c)
{
  int i;

  for (i = c->varCount - 1; i >= 0; i--) {
    List_Add((c->variables[i])->constraints, (Element) c);
  }
  c->whichMethod = NO_METHOD;
  IncrementalAdd(c);
}

void DestroyConstraint(Constraint c)
{
  int i;

  if (SATISFIED(c)) IncrementalRemove(c);
  for (i = c->varCount - 1; i >= 0; i--) {
    List_Remove((c->variables[i])->constraints, (Element) c);
  }
  Constraint_Destroy(c);
}

/******** Public: Plan Extraction *******/

List hot = NULL;	/* used to collect "hot" constraints */

void AddIfSatisfiedInput(Constraint c)
{
  if (c->inputFlag && SATISFIED(c)) {
    List_Add(hot, c);
  }
}

void CollectSatisfiedInputs(Variable v)
{
  List_Do(v->constraints, AddIfSatisfiedInput);
}

List ExtractPlan(void)
{
  if (hot == NULL) hot = List_Create(128);
  List_RemoveAll(hot);
  List_Do(allVariables, CollectSatisfiedInputs);
  return MakePlan();
}

List ExtractPlanFromConstraint(Constraint c)
{
  if (hot == NULL) hot = List_Create(128);
  List_RemoveAll(hot);
  AddIfSatisfiedInput(c);
  return MakePlan();
}

List ExtractPlanFromConstraints(List constraints)
{
  if (hot == NULL) hot = List_Create(128);
  List_RemoveAll(hot);
  List_Do(constraints, AddIfSatisfiedInput);
  return MakePlan();
}

/******* Private: Plan Extraction *******/

List MakePlan()
{
  List	plan;
  Constraint	nextC;
  Variable	out;

  NewMark();
  plan = List_Create(128);
  nextC = (Constraint) List_RemoveFirst(hot);
  while (nextC != NULL) {
    out = OUT_VAR(nextC);
    if ((out->mark != currentMark) && InputsKnown(nextC)) {
      List_Add(plan, nextC);
      out->mark = currentMark;
      nextC = NextDownstreamConstraint(hot, out);
    } else {
      nextC = (Constraint) List_RemoveFirst(hot);
    }
  }
  return plan;
}

Boolean InputsKnown(Constraint c)
{
  int	outIndex, i;
  Variable	in;

  outIndex = c->methodOuts[c->whichMethod];
  for (i = c->varCount - 1; i >= 0; i--) {
    if (i != outIndex) {
      in = c->variables[i];
      if ((in->mark != currentMark) &&
	  (!in->stay) &&
	  (in->determinedBy != NULL)) {
	return false;
      }
    }
  }
  return true;
}

/******* Private: Adding *******/

void IncrementalAdd(Constraint c)
{
  Constraint overridden;

  NewMark();
  overridden = Satisfy(c);
  while (overridden != NULL) {
    overridden = Satisfy(overridden);
  }
}

Constraint Satisfy(Constraint c)
{
  int	outIndex, i;
  Constraint	overridden;
  Variable	out;

  c->whichMethod = ChooseMethod(c);
  if (SATISFIED(c)) {
    /* mark inputs to allow cycle detection in AddPropagate */
    outIndex = c->methodOuts[c->whichMethod];
    for (i = c->varCount - 1; i >= 0; i--) {
      if (i != outIndex) {
	c->variables[i]->mark = currentMark;
      }
    }
    out = c->variables[outIndex];
    overridden = (Constraint) out->determinedBy;
    if (overridden != NULL) overridden->whichMethod = NO_METHOD;
    out->determinedBy = c;
    if (!AddPropagate(c)) {
      Error("Cycle encountered");
      return NULL;
    }
    out->mark = currentMark;
    return overridden;
  } else {
    if (c->strength == S_required) {
      Error("Could not satisfy a required constraint");
    }
    return NULL;
  }
}

int ChooseMethod(Constraint c)
{
  int	best, m;
  Strength bestOutStrength;
  Variable	mOut;

  best = NO_METHOD;
  bestOutStrength = c->strength;
  for (m = c->methodCount - 1; m >= 0; m--) {
    mOut = c->variables[c->methodOuts[m]];
    if ((mOut->mark != currentMark) &&
	(Weaker(mOut->walkStrength, bestOutStrength))) {
      best = m;
      bestOutStrength = mOut->walkStrength;
    }
  }
  return best;
}

Boolean AddPropagate(Constraint c)
{
  List		todo;
  Constraint	nextC;
  Variable	out;

  todo = List_Create(8);	/* unprocessed constraints */
  nextC = c;
  while (nextC != NULL) {
    out = OUT_VAR(nextC);
    if (out->mark == currentMark) {
      /* remove the cycle-causing constraint */
      IncrementalRemove(c);
      return false;
    }
    Recalculate(nextC);
    nextC = NextDownstreamConstraint(todo, out);
  }
  List_Destroy(todo);
  return true;
}

/******* Private: Removing *******/

List unsatisfied; /* used to collect unsatisfied downstream constraints */
Strength strength; /* used to add unsatisfied constraints in strength order */

void AddAtStrength(Constraint c)
{
  if (c->strength == strength) IncrementalAdd(c);
}

void CollectUnsatisfied(Constraint c)
{
  if (!SATISFIED(c)) List_Add(unsatisfied, c);
}

void IncrementalRemove(Constraint c)
{
  Variable out;
  int i;

  out = OUT_VAR(c);
  c->whichMethod = NO_METHOD;
  for (i = c->varCount - 1; i >= 0; i--) {
    List_Remove((c->variables[i])->constraints, (Element) c);
  }
  unsatisfied = List_Create(8);
  RemovePropagateFrom(out);
  for (strength = S_required; strength <= S_weakest; strength++) {
    List_Do(unsatisfied, AddAtStrength);
  }
  List_Destroy(unsatisfied);
}

void RemovePropagateFrom(Variable v)
{
  Constraint	nextC;
  List	todo;

  v->determinedBy = NULL;
  v->walkStrength = S_weakest;
  v->stay = true;
  todo = List_Create(8);
  while (true) {
    List_Do(v->constraints, CollectUnsatisfied);
    nextC = NextDownstreamConstraint(todo, v);
    if (nextC == NULL) {
      break;
    } else {
      Recalculate(nextC);
      v = OUT_VAR(nextC);
    }
  }
  List_Destroy(todo);
}

/******* Private: Recalculation *******/

void Recalculate(Constraint c)
{
 Variable out;

 out = OUT_VAR(c);
 out->walkStrength = OutputWalkStrength(c);
 out->stay = ConstantOutput(c);
 if (out->stay) c->execute(c);
}

int OutputWalkStrength(Constraint c)
{
  int outIndex, m, mOutIndex;
  Strength minStrength;

  minStrength = c->strength;
  outIndex = c->methodOuts[c->whichMethod];
  for (m = c->methodCount - 1; m >= 0; m--) {
    mOutIndex = c->methodOuts[m];
    if ((mOutIndex != outIndex) &&
	(Weaker(c->variables[mOutIndex]->walkStrength, minStrength))) {
      minStrength = c->variables[mOutIndex]->walkStrength;
    }
  }
  return minStrength;
}

Boolean ConstantOutput(Constraint c)
{
  int outIndex, i;

  if (c->inputFlag) return false;
  outIndex = c->methodOuts[c->whichMethod];
  for (i = c->varCount - 1; i >= 0; i--) {
    if (i != outIndex) {
      if (!c->variables[i]->stay) return false;
    }
  }
  return true;
}

/******* Private: Miscellaneous *******/

void NewMark(void)
{
    currentMark++;
}

Constraint NextDownstreamConstraint(List todo, Variable variable)
{
  List allC = variable->constraints;
  Constraint *nextPtr = (Constraint *) &(allC->slots[allC->first]);
  Constraint *lastPtr = (Constraint *) &(allC->slots[allC->last]);
  Constraint determiningC = variable->determinedBy;
  Constraint first = NULL;

  for ( ; nextPtr <= lastPtr; nextPtr++) {
    if ((*nextPtr != determiningC) && SATISFIED(*nextPtr)) {
      if (first == NULL) {
	first = *nextPtr;
      } else {
	List_Add(todo, *nextPtr);
      }
    }
  }
  if (first == NULL) {
    first = (Constraint) List_RemoveFirst(todo);
  }
  return first;
}


/*
  Some useful constraints. Each function instantiates and installs
  a constraint on the argument variables.
  */


/* macro to reference a constraint variable value */
#define var(i) ((c->variables[i])->value)

/******* Stay Constraint *******/

Constraint StayC(Variable v, Strength strength)
{
  Constraint new = Constraint_Create(1, strength);
  new->variables[0] = v;
  new->methodCount = 1;
  new->methodOuts[0] = 0;
  AddConstraint(new);
  return new;
};

/******* Edit Constraint *******/

Constraint EditC(Variable v, Strength strength)
{
  Constraint new = Constraint_Create(1, strength);
  new->inputFlag = true;
  new->variables[0] = v;
  new->methodCount = 1;
  new->methodOuts[0] = 0;
  AddConstraint(new);
  return new;
};

/****** Equals Constraint ******/

void EqualsC_Execute(Constraint c)
{
  /* a = b */
  switch (c->whichMethod) {
  case 0:
    var(0) = var(1);
    break;
  case 1:
    var(1) = var(0);
    break;
  }
}

Constraint EqualsC(Variable a, Variable b, Strength strength)
{
  Constraint new = Constraint_Create(2, strength);
  new->execute = EqualsC_Execute;
  new->variables[0] = a;
  new->variables[1] = b;
  new->methodCount = 2;
  new->methodOuts[0] = 0;
  new->methodOuts[1] = 1;
  AddConstraint(new);
  return new;
};

/******** Add Constraint *******/

void AddC_Execute(Constraint c)
{
  /* a + b = sum */
  switch (c->whichMethod) {
  case 0:
    var(2) = var(0) + var(1);
    break;
  case 1:
    var(1) = var(2) - var(0);
    break;
  case 2:
    var(0) = var(2) - var(1);
    break;
  }
}

Constraint AddC(Variable a, Variable b, Variable sum, Strength strength)
{
  Constraint new = Constraint_Create(3, strength);
  new->execute = AddC_Execute;
  new->variables[0] = a;
  new->variables[1] = b;
  new->variables[2] = sum;
  new->methodCount = 3;
  new->methodOuts[0] = 2;
  new->methodOuts[1] = 1;
  new->methodOuts[2] = 0;
  AddConstraint(new);
  return new;
};

/******** ScaleOffset Constraint *******/

void ScaleOffsetC_Execute(Constraint c)
{
  /* (src * scale) + offset = dest */
  switch (c->whichMethod) {
  case 0:
    var(3) = (var(0) * var(1)) + var(2);
    break;
  case 1:
    var(0) = (var(3) - var(2)) / var(1);
    break;
  }
}

Constraint ScaleOffsetC(Variable src, Variable scale, Variable offset,
			Variable dest, Strength strength)
{
  Constraint new = Constraint_Create(4, strength);
  new->execute = ScaleOffsetC_Execute;
  new->variables[0] = src;
  new->variables[1] = scale;
  new->variables[2] = offset;
  new->variables[3] = dest;
  new->methodCount = 2;
  new->methodOuts[0] = 3;
  new->methodOuts[1] = 0;
  AddConstraint(new);
  return new;
};

/***************************************************************************
*
* This is the standard DeltaBlue benchmark. A long chain of equality
* constraints is constructed with a stay constraint on one end. An edit
* constraint is then added to the opposite end and the time is measured for
* adding and removing this constraint, and extracting and executing a
* constraint satisfaction plan. There are two cases. In case 1, the added
* constraint is stronger than the stay constraint and values must propagate
* down the entire length of the chain. In case 2, the added constraint is
* weaker than the stay constraint so it cannot be accomodated. The cost in
* this case is, of course, very low. Typical situations lie somewhere between
* these two extremes.
*
****************************************************************************/

void ChainTest(int n)
{
  long 	msecs, i;
  char	name[20];
  Variable	prev, v, first, last;
  Constraint	editC;
  List		plan;

  InitDeltaBlue();
  prev = first = last = NULL;

  for (i = 0; i < n; i++) {
    sprintf(name, "v%ld", i);
    v = Variable_Create(name, 0);
    if (prev != NULL) {
      EqualsC(prev, v, S_required);
    }
    if (i == 0) first = v;
    if (i == (n-1)) last = v;
    prev = v;
  }
  StayC(last, S_default);
  editC = EditC(first, S_strongDefault);
  plan = ExtractPlanFromConstraint(editC);
  for (i = 0; i < 100; i++) {
    first->value = i;
    ExecutePlan(plan);
    if (last->value != i)
      Error("ChainTest failed!");
  }
  List_Destroy(plan);
  DestroyConstraint(editC);
}

/***************************************************************************
 *
 * This test constructs a two sets of variables related to each other by a
 * simple linear transformation (scale and offset). The time is measured to
 * change a variable on either side of the mapping and to change the scale or
 * offset factors. It has been tested for up to 2000 variable pairs.
 *
 ****************************************************************************/

void Change(Variable v, long newValue)
{
  Constraint	editC;
  long 	i, msecs;
  List	plan;

  editC = EditC(v, S_strongDefault);
  plan = ExtractPlanFromConstraint(editC);
  v->value = newValue;
  for (i = 0; i < 10; i++) {
    ExecutePlan(plan);
  }
  List_Destroy(plan);
  DestroyConstraint(editC);
}

void ProjectionTest(int n)
{
  Variable	src, scale, offset, dest;
  long 	msecs, i;
  char	name[20];
  List dests;

  InitDeltaBlue();

  scale = Variable_Create("scale", 10);
  offset = Variable_Create("offset", 1000);
  dests = List_Create(n);

  for (i = 1; i <= n; i++) {
    /* make src and dest variables */
    sprintf(name, "src%ld", i);
    src = Variable_Create(name, i);
    sprintf(name, "dest%ld", i);
    dest = Variable_Create(name, i);
    List_Add(dests, dest);

    /* add stay on src */
    StayC(src, S_default);

    /* add scale/offset constraint */
    ScaleOffsetC(src, scale, offset, dest, S_required);
  }
  Change(src, 17);
  if (dest->value != 1170)
    Error("Projection Test 1 failed!");

  Change(dest, 1050);
  if (src->value != 5)
    Error("Projection Test 2 failed!");

  Change(scale, 5);
  for (i = 1; i < List_Size(dests); ++i)
    if (((Variable)List_At(dests, i - 1))->value != i * 5 + 1000)
      Error("Projection Test 3 failed!");

  Change(offset, 2000);
  for (i = 1; i < List_Size(dests); ++i)
    if (((Variable)List_At(dests, i - 1))->value != i * 5 + 2000)
      Error("Projection Test 4 failed!");

  List_Destroy(dests);
}

int run()
{
  int n = 1000;
  ChainTest(n);
  ProjectionTest(n);
  return 0;
}

int main(int argc, char* argv[]) {
  return run();
}
