function null() {
}

function foo() {
  return "bar";
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
}  
