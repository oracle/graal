function add(a, b) {
  return a + b;
}

function loop(n) {
  i = 0;  
  while (i < n) {  
    i = add(i, 1);  
  }  
  return i;
}  

function main() {
  add("a", "b");
  println(loop(1000));  
}  
