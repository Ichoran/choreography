/* Statistic.java - Various numerical and statistical utilities
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.numerics;

import java.util.*;

public class Statistic
{
  // Useful constants computed to 20 digits using Mathematica 8.0
  public static final double quartile_to_sd = 1.48260221850560186;
  public static final double halfwidth_to_sd = 0.74130110925280093;
  public static final double over_sqrt_2 = 0.7071067811865475244;
  public static final double over_sqrt_2_pi = 0.39894228040143267794;
  public static final double sqrt_2 = 1.4142135623730950488;
  
  // Based on Applied Statistics 37:477-484 (1988), alg. AS241
  // Use only for small tail (p < 0.2)
  // We need this to figure out how many standard deviations to go before rejecting data
  public static float invnormcdf_tail(float p)
  {
    double d = (float)Math.sqrt( -Math.log(p) );
    if (d<5.0)
    {
      d -= 1.6;
      d = (1.4234372777+d*(2.75681539+d*(1.3067284816+d*0.17023821103))) / (1.0+d*(0.7370016425+d*0.12021132975));
      return (float)d;
    }
    else
    {
      d -= 5.0;
      d = (6.657905115+d*(3.081226386+d*(0.42868294337+d*0.017337203997))) / (1.0+d*(0.24197894225+d*0.012258202635));
      return (float)d;
    }
  }
  public static float invnormcdf_tail(float p,float avg,float std)
  {
    return avg + std*invnormcdf_tail(p);
  }
  
  // Higher-quality error/inverse error functions for general use
  // Based on Applied Statistics 37:477-484 (1988), alg. AS241
  public static double icdfNormal(double p) {
    double h = p-0.5;
    double x = 0.180625 - h*h;
    if (x>=0) {
      return h*
             (((((((2.5090809287301226727e3*x + 3.3430575583588128105e4
                   )*x + 6.7265770927008700853e4
                  )*x + 4.5921953931549871457e4
                 )*x + 1.3731693765509461125e4
                )*x + 1.9715909503065514427e3
               )*x + 1.3314166789178437745e2
              )*x + 3.3871328727963666080e0
             ) /
             (((((((5.2264952788528545610e3*x + 2.8729085735721942674e4
                   )*x + 3.9307895800092710610e4
                  )*x + 2.1213794301586595867e4
                 )*x + 5.3941960214247511077e3
                )*x + 6.8718700749205790830e2
               )*x + 4.2313330701600911252e1
              )*x + 1.0e0
             );
    }
    else {
      if (h<=0) { x = p; h = -1.0; }
      else { x= 1.0-p; h = 1.0; }
      x = Math.sqrt(-Math.log(x));
      if (x<=5.0) {
        x -= 1.6;
        return h*
               (((((((7.74545014278341407640e-4*x + 2.27238449892691845833e-2
                     )*x + 2.41780725177450611770e-1
                    )*x + 1.27045825245236838258e0
                   )*x + 3.64784832476320460504e0
                  )*x + 5.76949722146069140550e0
                 )*x + 4.63033784615654529590e0
                )*x + 1.42343711074968357734e0
               ) /
               (((((((1.05075007164441684324e-9*x + 5.47593808499534494600e-4
                     )*x + 1.51986665636164571966e-2
                    )*x + 1.48103976427480074590e-1
                   )*x + 6.89767334985100004550e-1
                  )*x + 1.67638483018380384940e0
                 )*x + 2.05319162663775882187e0
                )*x + 1.0
               );
      }
      else {
        x -= 5.0;
        return h*
               (((((((2.01033439929228813265e-7*x + 2.71155556874348757815e-5
                     )*x + 1.24266094738807843860e-3
                    )*x + 2.65321895265761230930e-2
                   )*x + 2.96560571828504891230e-1
                  )*x + 1.78482653991729133580e0
                 )*x + 5.46378491116411436990e0
                )*x + 6.65790464350110377720e0
               ) /
               (((((((2.04426310338993978564e-15*x + 1.42151175831644588870e-7
                     )*x + 1.84631831751005468180e-5
                    )*x + 7.86869131145613259100e-4
                   )*x + 1.48753612908506148525e-2
                  )*x + 1.36929880922735805310e-1
                 )*x + 5.99832206555887937690e-1
                )*x + 1.0
               );
      }
    }
  }
  public static double erfInv(double x) { return over_sqrt_2*icdfNormal(0.5+0.5*x); }
  public static double erfcInv(double x) { return over_sqrt_2*icdfNormal(1.0-0.5*x); }
  // Piecewise rational function approximation of CDF for Normal distribution (courtesy of Mathematica 7)
  // Should be full double precision
  public static double cdfNormal(double y) {
    if (y > 8.3) return 1.0;
    else if (y < - 38.5) return 0.0;
    
    double x = y;
    if (x<0) x=-x;
    double f;
    if (x < 3) {
      f = Math.exp( -0.5*x*x -
                   (((((((-3.6271830621274548308e-6*x - 6.2054577195631746255e-5
                         )*x + 0.0020555154846807655013
                        )*x + 0.032099345474574417685
                       )*x + 0.21504119632351847003
                      )*x + 0.73055326515392090713
                     )*x + 1.3812898842892215850
                    )*x + 0.69314718055994526146
                   ) /
                   (((((((-5.8186829446354815108e-7*x - 2.2135273033157240657e-5
                         )*x + 3.6576165145176352643e-4
                        )*x + 0.0094667294072793799548
                       )*x + 0.078740088812851505927
                      )*x + 0.34723234319509102797
                     )*x + 0.84167596702197143827
                    )*x + 1.0
                   )
                  );
    }
    else if (x < 16) {
      f = Math.exp( -0.5*x*x ) *
          ((((((0.00118089255719362346624*x + 0.0136334301130162766315
               )*x + 0.086474160844062169269
              )*x + 0.33993667920309143168
             )*x + 0.86339167691367313008
            )*x + 1.3345326346191572297
           )*x + 1
          ) /
          (((((((0.0029600586715196076372*x + 0.034173941597530707646
                )*x + 0.21971862448906668587
               )*x + 0.88626919617829879773
              )*x + 2.3750320592403537542
             )*x + 4.1290652702771203918
            )*x + 4.2651316245967753927
           )*x + 1.9999244808870340017
          );
    }
    else {
      f = Math.exp(-0.5*x*x)*over_sqrt_2_pi;
      double z = 1/(x*x);
      double g = 1/x;
      double sum = g;
      for (int i = -1 ; i>-20 ; i-=2) { g *= i*z; sum += g; }
      f *= sum;
    }
    if (y>0) return 1.0-f;
    else return f;
  }
  public static double erf(double x) { return 2.0*cdfNormal(sqrt_2*x)-1.0; }
  public static double erfc(double x) { return -2.0*cdfNormal(-sqrt_2*x); }
  
  // Gamma function--helpful for various statistical stuff even if we don't use it.
  // Should be accurate to 6-7 decimal places (floating point error; algorithm is better than that).
  public static final double ln_two_pi = 1.83787706640934548356;
  public static final double ln_err_11 = 1.0467984092952257e-6;
  public static final double ln_err_10 = 1.3942028746112077e-6;
  public static double lngamma(double z)
  {
    if (z>=12.0) return 0.5*(ln_two_pi - Math.log(z)) + z*(Math.log(z + 1/(12*z + 1/(10*z))) - 1);
    else if (z>=11) return 0.5*(ln_two_pi - Math.log(z)) + z*(Math.log(z + 1/(12*z + 1/(10*z))) - 1) + ln_err_11*(12.0-z);
    else if (z <= 0.0) return Double.NaN;
    else if (z == 1.0) return 0.0;
    else if (z == 2.0) return 0.0;
    else
    {
      double prod = 1.0;
      while (z<10.0) { prod *= z; z += 1.0; }
      return 0.5*(ln_two_pi - Math.log(z)) + z*(Math.log(z + 1/(12*z + 1/(10*z))) - 1) - Math.log(prod) + ln_err_10*(11.0-z) + ln_err_11*(z-10.0);
    }
  }
  public static double gamma(double z)
  {
    if (z>0) return Math.exp( lngamma(z) );
    else
    {
      double d = Math.sin( Math.PI * z );
      if (d==0) return Double.NaN;
      else return - Math.PI / ( z * d * Math.exp( lngamma(1.0-z) ) );  // Reflection formula
    }
  }
  
  // Regularized incomplete gamma function -- assume s,x > 0
  // $\gamma (s,x) = \frac{1}{\Gamma (s)} \cdot \int_{0}^{x} t^{s-1} e^{-t} dt$
  // $\Gamma (s,x) = 1.0 - \gamma (s,x)$
  // Using standard form found in Cuyt & Peterson's "Handbook of Continued Fractions for Special Functions"
  // unless x is small so the series form should do better.  Assumes s>0,x>0.
  // Takes 0.5-1 us to compute for s,x<1000.  For large s,x (e.g. 10000+), a better split could be found.
  public static double regularIncompleteGamma(double s,double x,boolean lower) {
    if (x < s+1) {
      double taylor = 1.0/s;
      double sum = taylor;
      for (double denom=(s+1.0) ; taylor > 100*Math.ulp(sum) ; denom+=1.0) { taylor *= x/denom; sum += taylor; }
      double lowIncGamma = sum*Math.exp( - x + s*Math.log(x) - lngamma(s) );
      if (lower) return lowIncGamma;
      else return 1.0-lowIncGamma;
    }
    else {
      double tiny = Math.sqrt(Double.MIN_VALUE);
      double huge = 1.0/tiny;
      double eps = 100*Math.ulp(1.0);
      double continuedA;
      double continuedB = x + 1.0 - s;
      double lentzC = huge;
      double lentzD = (Math.abs(continuedB)<tiny) ? huge : 1.0 / continuedB;  // Only need condition if you change x<s+1 to something else
      double factor = 2.0;
      double prod = lentzD;
      for (int n=1 ; Math.abs(factor-1.0)>eps ; n++ , prod*=factor) {
        continuedA = n*(s-n);
        continuedB += 2.0;
        lentzC = continuedB + continuedA/lentzC;
        if (Math.abs(lentzC)<tiny) lentzC = tiny;
        lentzD = continuedB + continuedA*lentzD;
        if (Math.abs(lentzD)<tiny) lentzD = huge;
        else lentzD = 1.0/lentzD;
        factor = lentzC*lentzD;
      }
      double highIncGamma = prod*Math.exp( - x + s*Math.log(x) - lngamma(s) );
      if (lower) return 1.0-highIncGamma;
      else return highIncGamma;
    }
  }
  public static double regularLowerIncompleteGamma(double s,double x) { return regularIncompleteGamma(s,x,true); }
  public static double regularUpperIncompleteGamma(double s,double x) { return regularIncompleteGamma(s,x,false); }
  public static double cdfChiSq(long df,double chisq) {
    return regularIncompleteGamma(0.5*df,0.5*chisq,true);
  }
  public static double cdfNotChiSq(long df,double chisq) {
    return regularIncompleteGamma(0.5*df,0.5*chisq,false);
  }
  
  
  // Incomplete Beta for integer arguments only; easy to calculate the
  // regularized version and then convert if need be
  public static double incBetaReg(double x,int a,int b) {
    int n = a+b-1;
    double sum = 0.0;
    double xpart = 0.0;
    double choose = 0.0;
    double ln_nfac = lngamma(1+n);
    for (int i=a ; i<=n ; i++) {
      choose = Math.exp( ln_nfac - lngamma(1+i) - lngamma(1+n-i) );
      xpart = Math.pow(x,i) * Math.pow(1.0-x,n-i);
      sum += choose*xpart;
    }
    return sum;
  }
  public static double beta(int a,int b) {
    return Math.exp( lngamma(a) + lngamma(b) - lngamma(a+b) );
  }
  public static double incompleteBeta(double x,int a,int b) {
    return incBetaReg(x,a,b)*beta(a,b);
  }
  
  // Cumulative F distribution statistic--just pass off to incomplete regularized Beta
  public static double cdfFstat(double F,int n,int m) {
    n /= 2; m /= 2;
    return incBetaReg( (n*F)/(m+n*F) , n , m );
  }
  
  // Inverse CDF for F statistic; method is slow, so don't use it for anything intensive!
  public static double icdfFstat(double p,int n, int m,double fracErr) {
    double above = 1.0;
    double below = 1.0;
    double mid = 1.0;
    while (cdfFstat(above,n,m) < p) above *= 2;
    while (cdfFstat(below,n,m) > p) below /= 2;
    while ((above-below)/(above+below) > fracErr) {
      mid = Math.sqrt(above*below);
      if (cdfFstat(mid,n,m) < p) below = mid;
      else above=mid;
    }
    return Math.sqrt(above*below);
  }
  
  // Cumulative t distribution statistic--pass off to incomplete regularized Beta
  public static double cdfTstat(double t,int n) {
    return incBetaReg( 0.5*(1 + t/Math.sqrt(t*t+n)) , n/2 , n/2 );
  }
  
  // Inverse CDF for t statistic--slow, so use non-intensively
  public static double icdfTstat(double p,int n,double fracErr) {
    double above = 1.0;
    double below = 1.0;
    double mid = 1.0;
    while (cdfTstat(above,n) < p) above *= 2;
    while (cdfTstat(below,n) > p) below /= 2;
    while ((above-below)/(above+below) > fracErr) {
      mid = Math.sqrt(above*below);
      if (cdfTstat(mid,n) < p) below = mid;
      else above = mid;
    }
    return Math.sqrt(above*below);
  }
  
  
  public double maximum;
  public double minimum;
  public double average;
  public double deviation;
  public double median;
  public double first_quartile;
  public double last_quartile;
  public double jitter;
  public int n;
  
  public Statistic()
  {
    zero();
  }
  
  public Statistic( float[] numbers )
  {
    compute(numbers,0,numbers.length);
  }
  
  public Statistic( float[] numbers , int lower_bound , int upper_bound )
  {
    compute(numbers,lower_bound,upper_bound);
  }
    
  public void zero()
  {
    maximum = minimum = average = deviation = median = first_quartile = last_quartile = jitter = 0;
    n = 0;
  }
  
  public Statistic clone(Statistic s)
  {
    maximum = s.maximum;
    minimum = s.minimum;
    average = s.average;
    deviation = s.deviation;
    median = s.median;
    first_quartile = s.first_quartile;
    last_quartile = s.last_quartile;
    jitter = s.jitter;
    n = s.n;
    return this;
  }
  
  public void approximatelyIncorporate(Statistic s)
  {
    maximum = (maximum > s.maximum) ? maximum : s.maximum;
    minimum = (minimum < s.minimum) ? minimum : s.minimum;
    average = (n*average + s.n*s.average)/(n + s.n);
    deviation = Math.sqrt((n-1)*deviation*deviation + (s.n-1)*s.deviation*s.deviation)/Math.max(1,n + s.n - 1);
    median = (n*median + s.n*s.median)/(n + s.n);  // Approximation
    first_quartile = (n*first_quartile + s.n*s.first_quartile)/(n + s.n); // Approximation
    last_quartile = (n*last_quartile + s.n*last_quartile)/(n + s.n); // Approximation
    n += s.n;
  }
  
  public void compute(float numbers[],int lower_bound,int upper_bound)
  {
    if (numbers==null || lower_bound<0 || upper_bound<=lower_bound || upper_bound > numbers.length)
    {
      zero();
      return;
    }

    n = 0;
    
    float f = numbers[lower_bound];
    boolean unsorted = false;
    double sum = 0.0;
    double sumsq = 0.0;
    float prev_f;
    for (int i = lower_bound ; i < upper_bound ; i++)
    {
      prev_f = f;
      f = numbers[i];
      if ( Float.isNaN(f) )
      {
        upper_bound--;
        while ( Float.isNaN(numbers[upper_bound]) && upper_bound > i ) upper_bound--;
        if (upper_bound <= i) break;  // Everything left is NaN
        numbers[i] = numbers[upper_bound];
        numbers[upper_bound] = f;
        f = numbers[i];
      }
      if (f < prev_f) unsorted = true;
      n++;
      sum += f;
      sumsq += f*f;
    }

    if (n==0)
    {
      zero();
      return;
    }
    
    average = sum / n;
    if (n==0) deviation = 0;
    else if (n==1) deviation = average;
    else deviation = Math.sqrt( sumsq/(n-1) - average*average*(n/(n-1))  );
    
    upper_bound = lower_bound + n;  // NaN will have all gotten sorted to end
    if (unsorted) Arrays.sort(numbers,lower_bound,upper_bound);
    
    minimum = numbers[lower_bound];
    maximum = numbers[upper_bound-1];
    median = numbers[(lower_bound+upper_bound)/2];
    first_quartile = numbers[ (3*lower_bound+upper_bound)/4 ];
    last_quartile = numbers[ (lower_bound+3*upper_bound)/4 ];
  }
  public void compute(float[] numbers) { compute(numbers,0,numbers.length); }
  
  // Returns the number of outliers that were rejected
  public int robustCompute(float numbers[],float n_sd_cutoff,int lower_bound,int upper_bound)
  {
    compute(numbers,lower_bound,upper_bound);
    upper_bound = lower_bound + n;  // Ignore NaN at end
    
    int old_n = n;
    
    if (n<5) return 0;  // Can't assess outliers with less than 5 data points
    if (median==first_quartile || median==last_quartile) return 0;  // Can't assess outliers without a distribution
    
    if (Float.isNaN(n_sd_cutoff)) n_sd_cutoff = invnormcdf_tail( 0.001f/(n-4) );  // Reasonable estimate for "shocking" outliers (p<0.002)
    double n_sd_cut = (double)n_sd_cutoff;  // Just to avoid typecasting over and over
    
    double lower_plausible,upper_plausible; 
    
    // Test to see whether the left-half and right-half distributions seem to be statistically different
    // If so, use different outlier tests for left and righ halves of distribution
    if (n < 13) // Can't tell statistically
    {
      lower_plausible = median - n_sd_cut*(last_quartile-first_quartile)*halfwidth_to_sd;
      upper_plausible = median + n_sd_cut*(last_quartile-first_quartile)*halfwidth_to_sd;
    }
    else  // Can check statistics
    {
      boolean unlikely = false;
      
      int med_i = (lower_bound+upper_bound)/2;
      double lower_dist = median - first_quartile;
      double upper_dist = last_quartile - median;
      int N;
      int closer_i,farther_i;
      double f;
      // Check if lower half of data is consistent with upper quartile value
      N = med_i-lower_bound;
      f = invnormcdf_tail( 0.025f , 0.5f*N , 0.5f*(float)Math.sqrt(N) );
      farther_i = med_i - (int)f;
      closer_i = med_i + (int)f - N;
      if (farther_i >= lower_bound && median - numbers[farther_i] < upper_dist) unlikely = true;
      if (closer_i < med_i && median - numbers[closer_i] > upper_dist) unlikely = true;
      // Check if upper half of data is consistent with lower quartile value
      N = upper_bound-(med_i+1);
      f = invnormcdf_tail( 0.025f , 0.5f*N , 0.5f*(float)Math.sqrt(N) );
      farther_i = med_i + (int)f;
      closer_i = med_i - (int)f + N;
      if (farther_i < upper_bound && numbers[farther_i] - median < lower_dist) unlikely = true;
      if (closer_i > med_i && numbers[closer_i] - median > lower_dist) unlikely = true;
      
      if (unlikely)
      {
        lower_plausible = median - n_sd_cut*lower_dist*quartile_to_sd;
        upper_plausible = median + n_sd_cut*upper_dist*quartile_to_sd;
      }
      else
      {
        lower_plausible = median - n_sd_cut*(last_quartile-first_quartile)*halfwidth_to_sd;
        upper_plausible = median + n_sd_cut*(last_quartile-first_quartile)*halfwidth_to_sd;
      }
    }
    
    if (minimum >= lower_plausible && maximum <= upper_plausible) return 0;  // All data is plausible
    
    n = 0;
    double sum = 0.0;
    double sumsq = 0.0;
    float f;
    int lower_ok = lower_bound;
    int upper_ok = upper_bound;
    for (int i=lower_bound ; i<upper_bound ; i++)
    {
      f = numbers[i];
      if (f < lower_plausible) { lower_ok = i+1; continue; }
      if (f > upper_plausible) { upper_ok = i; break; }
      n++;
      sum += f;
      sumsq += f*f;
    }
    
    if (n==0)
    {
      zero();
      return old_n;
    }
    
    average = sum / n;
    if (n==0) deviation = 0;
    else if (n==1) deviation = average;
    deviation = Math.sqrt( sumsq/(n-1) - average*average*n/(n-1) );
    
    minimum = numbers[lower_ok];
    maximum = numbers[upper_ok];
    median = numbers[(lower_ok+upper_ok)/2];
    first_quartile = numbers[ (3*lower_ok + upper_ok)/4 ];
    last_quartile = numbers[ (lower_ok + 3*upper_ok)/4 ];
    
    return old_n - n;
  }
  public int robustCompute(float[] numbers,int lower_bound,int upper_bound) { return robustCompute(numbers,Float.NaN,lower_bound,upper_bound); }
  public int robustCompute(float[] numbers,float n_sd_cutoff) { return robustCompute(numbers,n_sd_cutoff,0,numbers.length); }
  public int robustCompute(float[] numbers) { return robustCompute(numbers,Float.NaN,0,numbers.length); }
  
}

