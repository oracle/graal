function main() {  
  obj1 = new();
  println(obj1.x);
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
}

function mkobj() {
  newobj = new();
  newobj.z = "zzz";
  return newobj;
}
