function invoke(f) {
  f("hello");
}

function f1() {
  println("f1");
}

function main() {
  invoke(f1);
  invoke(foo);  
}  
