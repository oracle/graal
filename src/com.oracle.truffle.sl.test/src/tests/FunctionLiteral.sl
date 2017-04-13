function add(a, b) {
  return a + b;
}

function sub(a, b) {
  return a - b;
}

function foo(f) {
  println(f(40, 2));
}

function main() {
  foo(add);
  foo(sub);
}  
