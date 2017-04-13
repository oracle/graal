function null() {
}

function foo() {
  return "bar";
}

function f(a, b) {
  return a + " < " + b + ": " + (a < b);
}

function main() {  
  println("s" + null());  
  println("s" + null);  
  println("s" + foo());  
  println("s" + foo);
    
  println(null() + "s");  
  println(null() + "s");  
  println(foo() + "s");  
  println(foo + "s");

  println(f(2, 4));
  println(f(2, "4"));
}  
