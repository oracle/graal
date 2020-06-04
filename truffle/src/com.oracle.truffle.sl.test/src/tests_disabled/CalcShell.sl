/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function iterations() {
  return 80;
}

function calcLoop(n) {
  i = 0;
  prev = cur = 0;
  while (i <= n) {
    next = calc(prev, cur, i);
    prev = cur;
    cur = next;  
    i = i + 1;
  }  
  return cur;  
}

function timing(n) {
  i = 0;
  while (i < 20) {
    start = nanoTime();
    calcLoop(n);
    end = nanoTime();
    i = i + 1;

    println("** run " + i + ": " + (end - start) + " ns");
  }
}

function run(n) {
  previousResult = calcLoop(n);
  println("** first: " + previousResult);
  i = 0;
  nextResult = 0;
  while (i < 101) {
    nextResult = calcLoop(n);
    if (nextResult != previousResult) {
        println("ERROR: result not stable iteration: " + i + " currentResult: " + nextResult + " : previousResult " + previousResult);
        break;
    }
    previousResult = nextResult;
    i = i + 1;
  }
  println("** last:  " + nextResult);
}  

function help() {
  println("available commands:");
  println("x: exit");
  println("c: define the calculation function with the parameters prev, cur, and i");
  println("   prev and cur start with 0; i is the loop variable from 0 to n");
  println("     example: 'return cur + i;' computes the sum of 1 to n"); 
  println("     example: 'if (i == 0) { return 1; } else { return cur * i; }' computes the factorial of i"); 
  println("     example: 'if (i <= 2) { return 1; } else { return prev + cur; }' computes the nth Fibonacci number");
  println("i: define the number of iterations, i.e, the number n in the examples above");
  println("r: Run the calculation loop often, and print the first and last result");
  println("t: Run the calculation loop a couple of time, and print timing information for each run");
  println("h: Print this help message");
  println("");
}

function main() {
  help();
  
  while (1 == 1) {
    println("cmd>");
    cmd = readln();
    if (cmd == "x" || cmd == "") {
      return;
    }
    if (cmd == "h") {
      help();
    }
    if (cmd == "c") {
      println("function>");
      code = readln();
      defineFunction("function calc(prev, cur, i) { " + code + "}");
    }
    if (cmd == "t") {
      timing(iterations());
    }
    if (cmd == "r") {
      run(iterations());
    }
    if (cmd == "i") {
      println("n>");
      n = readln();
      defineFunction("function iterations() { return " + n + "; }");
    }
  }
}  
