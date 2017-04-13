function foo() {
  println(test(40, 2));
}

function main() {
  defineFunction("function test(a, b) { return a + b; }");
  foo();

  defineFunction("function test(a, b) { return a - b; }");
  foo();
}  
