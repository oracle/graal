/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function main() {
  context = java("org.graalvm.polyglot.Context").create();
  context.eval("sl", "function createObject() { return new(); }");
  context.eval("sl", "function getPrimitive() { return 42; }");
  innerBindings = context.getBindings("sl");
  println(innerBindings.createObject());
  println(innerBindings.getPrimitive());
  context.close();
  
  // this is expected fail as
  innerBindings.getPrimitive();
}  
 