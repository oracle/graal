function doIt(a) {
  println("Initial stack trace:");
  println(stacktrace());
  
  hello = 123;
  println("After 123 assignment:");
  println(stacktrace());
  
  helloEqualsWorld();
  println("After hello assignment:");
  println(stacktrace());
  
//  readln();
}

function main() {
  i = 0;
  while (i < 10) {
    doIt(i);
    i = i + 1;
  }
}  
