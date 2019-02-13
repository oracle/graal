function e(a, b) {
  return a == b;
}

function main() {  
  println(e(4, 4));  
  println(e(3, "aaa"));  
  println(e(4, 4));  
  println(e("a", "a"));  
  println(e(1==2, 1==2));  
  println(e(1==2, 1));  
  println(e(e, e));  
}  
