/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function main() {  
  obj1 = new();
  obj1.x = 42;
  println(obj1.x);
  
  obj2 = new();
  obj2.o = obj1;
  println(obj2.o.x);
  obj2.o.y = "why";
  println(obj1.y);
  
  println(mkobj().z);
  
  obj3 = new();
  obj3.fn = mkobj;
  println(obj3.fn().z);

  obj4 = new();
  write(obj4, 1);
  read(obj4);
  write(obj4, 2);
  read(obj4);
  write(obj4, "three");
  read(obj4);

  obj5 = new();
  println(obj5.x);
}

function mkobj() {
  newobj = new();
  newobj.z = "zzz";
  return newobj;
}

function read(obj) {
  return obj.prop;
}

function write(obj, value) {
  return obj.prop = value;
}
