/* The easiest way to generate null: a function without a return statement implicitly returns null. */
function null() {
}

function main() {  
  println(null());  
  println(null() == null());  
  println(null() != null());  
  println(null() == 42);  
  println(null() != 42);  
  println(null() == "42");  
  println(null() != "42");  
}  
