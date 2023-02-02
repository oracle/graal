#include <RcppArmadillo.h>
#include <cmath>

//[[Rcpp::depends(RcppArmadillo)]]
using namespace Rcpp;

// [[Rcpp::export]]
double mutual_cpp(arma::mat joint_dist){
  joint_dist = joint_dist / sum(sum(joint_dist));
  double mutual_information = 0;
  int num_rows = joint_dist.n_rows;
  int num_cols = joint_dist.n_cols;
  arma::mat colsums = sum(joint_dist, 0);
  arma::mat rowsums = sum(joint_dist, 1);
  for(int i = 0; i < num_rows; ++i){
    for(int j = 0; j <  num_cols; ++j){
      double temp = log((joint_dist(i, j) / (colsums[j] * rowsums[i])));
      if(!std::isfinite(temp)){
        temp = 0;
      }
      mutual_information += joint_dist(i, j) * temp;
    }
  }
  return mutual_information;
}

// [[Rcpp::export]]
List mutual_test(arma::mat joint_dist){
  return List::create(Named("sum") = sum(joint_dist));
}
