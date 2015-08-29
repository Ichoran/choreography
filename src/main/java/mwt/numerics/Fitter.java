/* Fitter.java - Finds linear and circular fits to data.
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 * The HyperFit circle-finding algorithm used in this code is from
 * Al-Sharadqah & Chernov, Elec. J. Statistics 3:886-911 (2009).
 */

package mwt.numerics;

public class Fitter {
  public double Ox;
  public double Oy;
  public double Sx;
  public double Sy;
  public double Sxx;
  public double Syy;
  public double Sxy;
  public double Sxz;
  public double Syz;
  public double Szz;
  public int n;
  public boolean automove;
  
  protected EigenFinder eiger;
  public PolynomialRootFinder polyrf;
  public SpotFitter spot;
  public LineFitter line;
  public CircleFitter circ;
  
  public static class SpotParameters {
    // Gaussian blob centered at x0,y0
    public double x0;
    public double y0;
    public double sigma;
    public SpotParameters() { x0=y0=sigma=0.0; }
    public SpotParameters(double x00,double y00,double sigma0) { x0=x00; y0=y00; sigma=sigma0; }
    public SpotParameters(SpotParameters sp) { x0 = sp.x0; y0 = sp.y0; sigma = sp.sigma; }
  }
  
  public static class LinearParameters {
    // Equation of line a*y + b*x + c = 0
    public double a;
    public double b;
    public double c;
    public LinearParameters() { a=b=c=0.0; }
    public LinearParameters(double a0,double b0,double c0) { a=a0; b=b0; c=c0; }
    public LinearParameters(LinearParameters lp) { a = lp.a ; b = lp.b; c = lp.c; }
  }
  public static class CircularParameters {
    // Equation of circle (x-x0)^2 + (y-y0)^2 = R^2
    public double x0;
    public double y0;
    public double R;
    public double MSE;
    public CircularParameters() { x0=y0=R=MSE=0.0; }
    public CircularParameters(double x00,double y00,double R0) { x0=x00; y0=y00; R=R0; MSE=0.0; }
    public CircularParameters(double x00,double y00, double R0,double MSE0) { x0=x00; y0=y00; R=R0; MSE=MSE0; }
    public CircularParameters(CircularParameters cp) { x0 = cp.x0; y0 = cp.y0; R = cp.R; MSE = cp.MSE; } 
  }

  // Optimized using Mathematica 7
  private static final double[][] trisectionCoeff = {
    { 1.0, 1.00000000000000000, 0.111111111111111111, -0.0164609053497942387, 0.00426764212772443225, -0.00135480702467442294, 0.000478363961798623407, -0.000180393143843251925, 0.0000711440603923936162, -0.0000289846171969011029, 0.0000121032352129398215 },
    { 0.8, 0.977082867122276646, 0.118255152546255299, -0.0193897791341417425, 0.00557627375386415221, -0.00196514157092676306, 0.000770514808800875661, -0.000322720643940775764, 0.000141376154987797655, -0.0000639831968963048284, 0.0000296811817396416175 },
    { 0.6, 0.952608221822056414, 0.126749955975378451, -0.0232776440585948592, 0.00751747628196047947, -0.00297745895850725166, 0.00131255475720956516, -0.000618205006160952861, 0.000304580032321341748, -0.000155039648770090648, 0.0000808968022240828250 },
    { 0.4, 0.926261757195517814, 0.137070231272876853, -0.0286249288765318218, 0.0105437254508361435, -0.00476754118533357597, 0.00240032992450573429, -0.00129146799705807940, 0.000726949820446853157, -0.000422797151056714268, 0.000252075161192156866 },
    { 0.2, 0.897609874622561880, 0.149960069137274354, -0.0363241753573766228, 0.0155744494451876410, -0.00820636576473876825, 0.00481686991361213750, -0.00302215988177106760, 0.00198397890954231693, -0.00134586205445813352, 0.000935964050340249824, -0.000663716797646247392 },
    { 0, 0.866025403784438647, 0.166666666666666667, -0.0481125224324688137, 0.0246913580246913580, -0.0155920211586704489, 0.0109739368998628258, -0.00825799639144397848, 0.00650307371843723010, -0.00529232705245318462, 0.00441566733967960068, -0.00375689883353158168, 0.00324707658917853464 },
    { -0.2, 0.830540951770293644, 0.189480810933704582, -0.0678012969589677020, 0.0433661360846158919, -0.0341814339105757972, 0.0300464997445734463, -0.0282473438600416210, 0.0277948643996841836, -0.0282669701873795322, 0.0294743536142216752, -0.0313409784504885293, 0.0338551599970753962, -0.0370480106216445160 },
    { -0.4, 0.789519248286085428, 0.223209915136065471, -0.105362099389941873, 0.0895392563472501813, -0.0939461344951441286, 0.110006024238694065, -0.137809846382160710, 0.180728675138160920, -0.244990774652067807, 0.340528791355239743, -0.482705693273669958, 0.695136669439399693, -1.01413747851414350, 1.49568892874247036, -2.22633879971546758 },
    { -11.0/20, 0.753307047010601533, 0.262490747912781887, -0.163491537170758413, 0.184670772262678347, -0.257984981302009979, 0.402472163994208291, -0.671936953967329478, 1.17454843487068024, -2.12240706212364264, 3.93272070942197228, -7.43188652595570640, 14.2684333718844903 },
    { -13.0/20, 0.725207747386557441, 0.302013038695660697, -0.239729102814446611, 0.347301453209952272, -0.623155701742487466, 1.24922578357109792, -2.68058331168361222, 6.02304126141674620, -13.9909105517385130, 33.3273405011592230, -80.9671523335197637, 199.846593560804813, -499.733502640004448 },
    { -15.0/20, 0.692183576319070784, 0.363713439155063518, -0.399650703570253908, 0.808277714617387997, -2.02806123238030522, 5.68845447732404731, -17.0825888145402160, 53.7233990615162681, -174.681515014207970, 582.470274245416627, -1980.91865075167155, 6844.59804730662526, -23960.1450164282985, 84793.9321792031395, -302871.203927300239, 1.09044614208059904e6 },
    { -33.0/40, 0.662234381771498811, 0.441959157481586422, -0.686024114124336247, 1.97712821883550750, -7.08019132759748977, 28.3564607004019782, -121.615653157854629, 546.285200939307338, -2537.15471757620309, 12084.6094164127090, -58707.5736266785127, 289768.305834027679, -1.44901450947640108e6 },
    { -35.0/40, 0.638118652632768511, 0.530125725481802257, -1.14082577559750193, 4.59417471216485013, -23.0169200995707848, 129.013685544972943, -774.489195286730414, 4869.86751110944138, -31661.6195998003585, 211114.220422712711, -1.43576850753159471e6, 9.92090411038118118e6, -6.94522849043971084e7, 4.91543353957992297e8, -3.51123348600714573e9 },
    { -73.0/80, 0.616334469439248000, 0.641676309594736291, -1.95409520887594806, 11.2234549248256990, -80.2839954829407743, 642.695089707672148, -5510.87021074998638, 49497.2211276811750, -459693.691670216836, 4.37857233172808068e6, -4.25387031088488545e7, 4.19893754476790005e8, -4.19919072224060702e9 },
    { -75.0/80, 0.598856508681471799, 0.767136241876516416, -3.24430385067335527, 26.0557185950357168, -260.834601248862153, 2922.74154128270869, -35082.3310508001947, 441111.009177794112, -5.73512978455682138e6, 7.64751286455055172e7, -1.04013188447046704e9, 1.43734976266564108e10, -2.01237658526557345e11, 2.84838856847141309e12, -4.06924865812291993e13 },
    { -153.0/160, 0.583119362323381580, 0.925635988796204458, -5.54957088916928685, 63.6075105255179129, -909.370473069236436, 14554.8411559953788, -249558.869761503194, 4.48241695028402534e6, -8.32518923994283756e7, 1.58584837560858283e9, -3.08122269641655707e10, 6.08264115139567357e11, -1.21656509300474784e13 },
    { -155.0/160, 0.570529684398212177, 1.10369251999730579, -9.20457602765407001, 147.593232737563537, -2953.49466975979287, 66174.3605953345032, -1.58840077861633486e6, 3.99404044735883737e7, -1.03851311627887494e9, 2.76949088990827805e10, -7.53327042141283449e11, 2.08197772527163537e13, -5.82966399020313920e14, 1.65027290571124047e16, -4.71513641437779439e17 },
    new double[0]
  };
  public static double trisectCosine(double t) {
    double[] series;
    if (t > -0.1) {
      if (t > 0.5) {
        if (t > 0.9) series=trisectionCoeff[0];
        else if (t > 0.7) series=trisectionCoeff[1];
        else series=trisectionCoeff[2];
      }
      else {
        if (t > 0.3) series=trisectionCoeff[3];
        else if (t>0.1) series=trisectionCoeff[4];
        else series=trisectionCoeff[5];
      }
    }
    else {
      if (t > -0.5) {
        if (t > -0.3) series=trisectionCoeff[6];
        else series=trisectionCoeff[7];
      }
      else if (t > -0.8) {
        if (t > -0.6) series=trisectionCoeff[8];
        else if (t > -0.7) series=trisectionCoeff[9];
        else series=trisectionCoeff[10];
      }
      else if (t > -0.9) {
        if (t > -0.85) series=trisectionCoeff[11];
        else series=trisectionCoeff[12];
      }
      else if (t > -0.95) {
        if (t > -0.925) series=trisectionCoeff[13];
        else series=trisectionCoeff[14];
      }
      else if (t > -0.975) {
        if (t > -0.9625) series=trisectionCoeff[15];
        else series=trisectionCoeff[16];
      }
      else series=trisectionCoeff[17];
    }
    if (series.length==0) {
      double s = trisectCosine(-t);
      return (0.5*s + 0.866025403784438647*Math.sqrt(1-s*s));
    }
    else {
      double sum = series[1];
      double dt = t - series[0];
      double dtn = dt;
      for (int i=2 ; i<series.length ; i++,dtn*=dt) sum += series[i]*dtn;
      return sum;
    }
  }

  
  public static class RowElements4 {
    public double a;
    public double b;
    public double c;
    public double d;
    public RowElements4(double i,double j,double k,double l) { a=i; b=j; c=k; d=l; }
      
    protected void set(double i,double j,double k,double l) { a=i; b=j; c=k; d=l; }
    protected void rowReduceA(RowElements4 re4) {
      double factor = a/re4.a;
      a = 0.0;
      b -= factor*re4.b;
      c -= factor*re4.c;
      d -= factor*re4.d;
    }
    protected void rowReduceB(RowElements4 re4) {
      double factor = b/re4.b;
      b = 0.0;
      c -= factor*re4.c;
      d -= factor*re4.d;
    }
    protected void rowReduceC(RowElements4 re4) {
      d -= c*re4.d/re4.c;
      c = 0.0;
    }
  }
  
  // Finds an eigenvector when loaded with a matrix.
  // Does NOT check to make sure row-reduction leaves free parameters.
  // Does NOT check for multiple redundancies (i.e. repeated eigenvalues)
  public static class EigenFinder {
    public RowElements4 a;
    public RowElements4 b;
    public RowElements4 c;
    public RowElements4 d;
    public RowElements4 eiv;
    public EigenFinder() {
      a = new RowElements4(0,0,0,0);
      b = new RowElements4(0,0,0,0);
      c = new RowElements4(0,0,0,0);
      d = new RowElements4(0,0,0,0);
      eiv = new RowElements4(0,0,0,0);
    }
    public EigenFinder(RowElements4 i,RowElements4 j,RowElements4 k,RowElements4 l) {
      a=i; b=j; c=k; d=l;
      eiv = new RowElements4(0,0,0,0);
    }
    public EigenFinder(RowElements4 i,RowElements4 j,RowElements4 k,RowElements4 l,RowElements4 ei) {
      a=i; b=j; c=k; d=l; eiv=ei;
    }
    
    protected void topA() {
      double aA = Math.abs(a.a);
      double bA = Math.abs(b.a);
      double cA = Math.abs(c.a);
      double dA = Math.abs(d.a);
      if (bA>aA) {
        RowElements4 t=a;
        if (cA>bA) {
          if (dA>cA) { a=d; d=t; }
          else { a=c; c=t; }
        }
        else { a=b; b=t; }
      }
      else if (cA>aA) {
        RowElements4 t=a;
        if (dA>cA) { a=d; d=t; }
        else { a=c; c=t; }
      }
      else if (dA>aA) { RowElements4 t=a; a=d; d=t; }
    }
    protected void triA() {
      b.rowReduceA(a);
      c.rowReduceA(a);
      d.rowReduceA(a);
    }
    
    protected void topB() {
      double bB = Math.abs(b.b);
      double cB = Math.abs(c.b);
      double dB = Math.abs(d.b);
      if (cB>bB) {
        RowElements4 t=b;
        if (dB>cB) { b=d; d=t; }
        else { b=c; c=t; }
      }
      else if (dB>bB) { RowElements4 t=b; b=d; d=t; }
    }
    protected void triB() {
      c.rowReduceB(b);
      d.rowReduceB(b);
    }

    protected void topC() { if (Math.abs(d.c)>Math.abs(c.c)) { RowElements4 t=c; c=d; d=t; } }
    protected void triC() { d.rowReduceC(c); }
    
    public void triangularize(boolean pickTopRow) {
      if (pickTopRow) topA();
      triA();
      topB(); triB();
      topC(); triC();
    }
    public void triangularize() { triangularize(true); }
    
    public RowElements4 eigenGivenTriangle() {
      eiv.c = -c.d/c.c;
      eiv.b = -(b.c*eiv.c+b.d)/b.b;
      eiv.a = -(a.b*eiv.b+a.c*eiv.c+a.d)/a.a;
      double eiscale = 1.0/Math.sqrt(1 + eiv.c*eiv.c + eiv.b*eiv.b + eiv.a*eiv.a);
      eiv.a *= eiscale;
      eiv.b *= eiscale;
      eiv.c *= eiscale;
      eiv.d = eiscale;
      return eiv;
    }
    
    public RowElements4 makeEigenvector(boolean pickTopRow) { triangularize(pickTopRow); return eigenGivenTriangle(); }
    public RowElements4 makeEigenvector() { triangularize(true); return eigenGivenTriangle(); }
  }
  
  // Finds the real roots of quadratic, cubic, and quartic polynomials exactly.
  // If only some real roots exist, they appear first and the remaining roots will be set to Double.NaN
  // Only those roots that might exist are set (i.e. quadratic root-finding sets x0 and x1).
  public static class PolynomialRootFinder {
    protected double b;
    protected double c;
    protected double d;
    protected double e;
    public double x0;
    public double x1;
    public double x2;
    public double x3;
    public PolynomialRootFinder() { b=c=d=e=0.0; x0=x1=x2=x3=Double.NaN; }
    
    public void setQuadratic(double a,double b0,double c0) { b=b0/a; c=c0/a; }
    public void setQuadratic(double b0,double c0) { b=b0; c=c0; }
    public void setCubic(double a,double b0,double c0,double d0) { b=b0/a; c=c0/a; d=d0/a; }
    public void setCubic(double b0,double c0,double d0) { b=b0; c=c0; d=d0; }
    public void setQuartic(double a,double b0,double c0,double d0,double e0) { b=b0/a; c=c0/a; d=d0/a; e=e0/a; }
    public void setQuartic(double b0,double c0,double d0,double e0) { b=b0; c=c0; d=d0; e=e0; }
    
    public void solveQuadratic() {
      double f = -0.5*b;
      double g = f*f-c;
      if (g<0) { x0 = x1 = Double.NaN; }
      else {
        double h = Math.sqrt(g);
        x0 = f+h;
        x1 = f-h;
      }
    }
    
    protected void solveLimitedReducedCubic(double limit) {
      double DD = c*c*c + d*d;
      if (DD>0) {
        double D = Math.sqrt(DD);
        double j = Math.cbrt(d+D);
        double k = Math.cbrt(d-D);
        x0 = j+k;
        x1 = x2 = Double.NaN;
      }
      else if (DD==0) {
        x0 = 2.0*Math.sqrt(-c);
        x1 = x2 = -0.5*x0;
      }
      else {
        double l = Math.sqrt(-c);
        double h = trisectCosine(d/(l*l*l));  // This is much faster than cos(acos(x)/3)!
        x0 = 2.0*l*h;
        if (x0>limit) return;
        double n = Math.sqrt(3.0)*l*Math.sqrt(1.0 - h*h);
        x1 = -0.5*x0 + n;
        x2 = -0.5*x0 - n;
      }      
    }
    public void solveReducedCubic() {
      c /= 3.0;
      d *= -0.5;
      solveLimitedReducedCubic(Double.POSITIVE_INFINITY);
    }
    protected void solveCubicPositively(boolean stopOnPositive) {
      if (d==0.0) {
        solveQuadratic();
        if (Double.isNaN(x0)) { x0 = 0; x2 = Double.NaN; }
        else x2 = 0;
      }
      else {
        double c0 = c;
        c = (3.0*c0 - b*b)/9.0;
        d = b*(4.5*c0 - b*b)/27.0 - 0.5*d;
        if (stopOnPositive) {
          solveLimitedReducedCubic(b/3.0);
          x0 -= b/3.0;
          if (x0>0) return;
        }
        else {
          solveLimitedReducedCubic(java.lang.Double.POSITIVE_INFINITY);
          x0 -= b/3.0;
        }
        x1 -= b/3.0;
        x2 -= b/3.0;
      }
    }
    public void solveCubic() { solveCubicPositively(false); }
    
    public void solveReducedQuartic() {
      if (e==0) {
        solveLimitedReducedCubic(Double.POSITIVE_INFINITY);
        if (Double.isNaN(x0)) { x0 = 0; x3 = Double.NaN; }
        else if (Double.isNaN(x1)) { x1 = 0; x3 = Double.NaN; }
        else x3 = 0;
      }
      else if (d==0) {
        b = c;
        c = e;
        solveQuadratic();
        if (x0 >= 0) {
          x0 = Math.sqrt(x0);
          if (x1 >= 0) {
            x1 = Math.sqrt(x1);
            x2 = -x0;
            x3 = -x1;
          }
          else {
            x1 = -x0;
            x2 = x3 = Double.NaN;
          }
        }
        else if (x1 >= 0) {
          x0 = Math.sqrt(x1);
          x1 = -x0;
          x2 = x3 = Double.NaN;
        }
        else x0 = x1 = x2 = x3 = Double.NaN;
      }
      else {
        double f = c;
        double g = d;
        b = 2*c;
        c = c*c-4*e;
        d = -d*d;
        solveCubicPositively(true);
        double j = (x0>0.0) ? x0 : ((x1>0.0) ? x1 : ((x2>0.0) ? x2 : x3));
        if (Double.isNaN(j) || j<=0.0) { x0 = x1 = x2 = x3 = Double.NaN; }
        else {
          double m = Math.sqrt(j);
          double n = 0.5*(f + j - g/m);
          b = m;
          c = n;
          solveQuadratic();
          x2 = x0;
          x3 = x1;
          b = -m;
          c = e/n;
          solveQuadratic();
          if (java.lang.Double.isNaN(x0)) {
            x0 = x2;
            x1 = x3;
            x2 = x3 = Double.NaN;
          }
        }
      }
    }
    public void solveQuartic() {
      if (e==0) {
        solveCubicPositively(false);
        if (Double.isNaN(x0)) { x0 = 0; x3 = Double.NaN; }
        else if (Double.isNaN(x1)) { x1 = 0; x3 = Double.NaN; }
        else x3 = 0;
      }
      else {
        double b0 = 0.25*b;
        double c0 = c;
        double d0 = d;
        c -= 6.0*b0*b0;
        d -= (2.0*c0 - 8.0*b0*b0)*b0;
        e -= b0*(d0 - b0*(c0 - 3.0*b0*b0));
        solveReducedQuartic();
        x0 -= b0;
        x1 -= b0;
        x2 -= b0;
        x3 -= b0;
      }
    }
  }
  
  // Boring symmetric Gaussian fit
  public class SpotFitter {
    public SpotParameters params;
    public SpotFitter() { params = new SpotParameters(); }
    public SpotFitter(SpotFitter s) { params = new SpotParameters(s.params); }
    public void fit() {
      params.x0 = Ox + Sx/n;
      params.y0 = Oy + Sy/n;
      params.sigma = Math.sqrt( ((Sxx+Syy) - (Sx*Sx + Sy*Sy)/n)/n );
    }
    public double sqError(double x,double y) {
      return (x-params.x0)*(x-params.x0) + (y-params.y0)*(y-params.y0);
    }
    public double meanVariance() {
      return ((Sxx+Syy) - (Sx*Sx + Sy*Sy)/n)/n;
    }
    public double pFit(double sigma2) {
      double chiSq = meanVariance()*(n-1)/sigma2;
      return Statistic.cdfNotChiSq(n-1,chiSq);
    }
  }
  
  // Typical linear least squares fit
  public class LineFitter {
    public LinearParameters params;
    public LineFitter() { params = new LinearParameters(); }
    public LineFitter(LineFitter l) { params = new LinearParameters(l.params); }
    public void fit() {
      double Dx = Sxx - Sx*Sx/n;
      double Dy = Syy - Sy*Sy/n;
      if (Math.abs(Dy) > Math.abs(Dx)) {
        params.a = (Sx*Sy/n - Sxy)/Dy;
        params.b = 1;
      }
      else {
        params.a = 1;
        params.b = (Sx*Sy/n - Sxy)/Dx;
      }
      params.c = -(params.a*Sy + params.b*Sx)/n - params.a*Oy - params.b*Ox;
    }
    public double getX(double y) { return (params.b==0) ? 0.0 : -(params.c + params.a*y)/params.b; }
    public double getY(double x) { return (params.a==0) ? 0.0 : -(params.c + params.b*x)/params.a; }
    public double getSigmaX(double y) {
      y -= Oy;
      if (n<3 || params.b==0) return Double.POSITIVE_INFINITY;
      double my = Sy/n;
      return Math.sqrt( Sxy*(1.0/n + (y - my)*(y - my)/(Syy/n - my*my)) ); 
    }
    public double getSigmaY(double x) {
      x -= Ox;
      if (n<3 || params.a==0) return Double.POSITIVE_INFINITY;
      double mx = Sx/n;
      return Math.sqrt( Sxy*(1.0/n + (x - mx)*(x - mx)/(Sxx/n - mx*mx)) ); 
    }
    public double parallelCoord(double x,double y) {
      if (params.a==1) { y += params.c; }
      else { x += params.c; }
      return (y*params.b - x*params.a)/Math.sqrt(params.a*params.a + params.b*params.b);
    }
    public double perpendicularCoord(double x,double y) {
      return Math.sqrt(sqError(x,y));
    }
    public double lineCoord(double x,double y) {
      if (params.a==1) return x + params.b*params.c/(1.0+params.b*params.b);
      else return y + params.a*params.c/(1.0+params.a*params.a);
    }
    public double sqError(double x,double y) {
      if (params.a==1) { y += params.c; }
      else { x += params.c; }
      double dotprod = (y*params.b - x*params.a)/(params.a*params.a + params.b*params.b);
      x = x + params.a*dotprod;
      y = y - params.b*dotprod;
      return x*x + y*y;
    }
    public double totalVariance() {
      return Sxx - Sx*Sx/n + Syy - Sy*Sy/n;
    }
    public double unfitVariance() {
      double regSS = (Sxy - Sx*Sy/n);
      regSS *= regSS;
      double residSS;
      if (params.b==1) residSS = (Sxx - Sx*Sx/n) - regSS/(Syy - Sy*Sy/n);
      else residSS = (Syy - Sy*Sy/n) - regSS/(Sxx - Sx*Sx/n);
      return residSS;
    }
    public double varianceAngleBias() {
      return (params.a*params.a + params.b*params.b);
    }
    public double pRound() {
      double Fstat = 0.5*totalVariance()*varianceAngleBias()/unfitVariance();
      return (1.0 - Statistic.cdfFstat( Fstat , n-1 , n-2 ));
    }
    public double pFit(double sigma2) {
      double chiSq = unfitVariance()/(varianceAngleBias()*sigma2);
      return Statistic.cdfNotChiSq(n-2,chiSq);
    }
    public double pNonRoundFit(double sigma2,double pNotRound) {
      double unVar = unfitVariance()/varianceAngleBias();
      double Fstat = 0.5*totalVariance()/unVar;
      if ((1.0 - Statistic.cdfFstat( Fstat , n-1 , n-2)) > pNotRound) return 0.0;
      double chiSq = unVar/sigma2;
      return Statistic.cdfNotChiSq(n-2,chiSq);
    }
    public double tScoreCorrelation() {
      double unVar = unfitVariance()/(n-2);
      if (params.a==1) return Math.abs(params.b) * Math.sqrt((Sxx - Sx*Sx/n) / unVar);
      else return Math.abs(params.a) * Math.sqrt((Syy - Sy*Sy/n) / unVar);
    }
  }
  
  // Implementation of Hyper algebraic circle fitting algorithm by Al-Sharadqah & Chernov, E. J. Stats v3 p886 (2009)
  // Using analytic solution to eigenvalue problem--much faster than approximate numeric methods!
  public class CircleFitter {
    public CircularParameters params;
    public CircleFitter() { params = new CircularParameters(); }
    public CircleFitter(CircleFitter c) { params = new CircularParameters(c.params); }
    public void fit() {
      double nInv = (n>0) ? 1.0/n : 1.0;
      double x = Sx*nInv;
      double y = Sy*nInv;
      double X = Sxx*nInv;
      double Y = Syy*nInv;
      double Z = Szz*nInv;
      double A = Sxy*nInv;
      double B = Sxz*nInv;
      double C = Syz*nInv;
      double P = X+Y;
      double M = X-Y;
      double Dx = X - x*x;
      double Dy = Y - y*y;
      polyrf.c = (B*x + C*y - A*A) - 0.25*(X*(3*X+2*Y) + 3*Y*Y + Z);
      polyrf.d = A*(C*x + B*y - A*P) + 0.25*(2*(B*x - C*y) + Y*Y - X*X)*M + 0.25*( (Dx + Dy)*Z - B*B - C*C /*+ (Y*Y - X*X)*M*/ );
      polyrf.e = 0.25*(C*C*Dx+ 2*B*C*(x*y - A) + B*B*Dy + (2*(A*(B*y + C*x) - B*x*Y - C*X*y) + (X*Y - A*A)*P)*P + (A*(A - 2*x*y) + (X*y*y - Y*Dx))*Z);
      polyrf.solveReducedQuartic();
      double eiv = polyrf.x0;
      if (polyrf.x1>0 && polyrf.x1<eiv) eiv = polyrf.x1;
      if (polyrf.x2>0 && polyrf.x2<eiv) eiv = polyrf.x2;
      if (polyrf.x3>0 && polyrf.x3<eiv) eiv = polyrf.x3;
      double DxDy = 2*(x*x - Dx + y*y - Dy);
      eiger.a.set(-eiv + 0.5*P, 0.5*x, 0.5*y, 0.5);
      eiger.b.set(B - 2*x*P, -eiv - 2*x*x + X, A - 2*x*y, -x);
      eiger.c.set(C - 2*y*P, A - 2*x*y, -eiv - 2*y*y + Y, -y);
      eiger.d.set(-2*(B*x + C*y) + P*DxDy + 0.5*Z, -2*(x*X + A*y) + x*DxDy + 0.5*B, -2*(A*x + y*Y) + y*DxDy + 0.5*C, -eiv - 2*(Dx+Dy) + 0.5*P);
      eiger.makeEigenvector();
      double aInv2 = 0.5/eiger.eiv.a;
      params.x0 = -eiger.eiv.b*aInv2 + Ox;
      params.y0 = -eiger.eiv.c*aInv2 + Oy;
      params.R = Math.sqrt(eiger.eiv.b*eiger.eiv.b + eiger.eiv.c*eiger.eiv.c - 4*eiger.eiv.a*eiger.eiv.d)*Math.abs(aInv2);
      params.MSE = eiv;
    }
    public double totalVariance() { return Sxx - Sx*Sx/n + Syy - Sy*Sy/n; }
    public double unfitVariance() { return n*params.MSE; }
    public double pFit(double sigma2) {
      if (n<4) return 0;
      double chiSq = unfitVariance()/sigma2;
      return Statistic.cdfNotChiSq(n-3,chiSq);
    }
    public double arcCoord(double x, double y) {
      return Math.atan2(y-params.y0,x-params.x0);
    }
    public double arcDeltaCoord(double x1,double y1,double x2,double y2) {
      x1 -= params.x0; x2 -= params.x0;
      y1 -= params.y0; y2 -= params.y0;
      double sintheta = (x1*y2-x2*y1)/Math.sqrt( (x1*x1+y1*y1)*(x2*x2+y2*y2) );
      if (sintheta<0.077) return sintheta;  // Max 0.1% error
      else if (sintheta<0.33) return sintheta*(1.0 + sintheta*sintheta/6);  // Also max 0.1%
      else return Math.asin(sintheta);
    }
    public double sqError(double x,double y) {
      double Rfit = Math.sqrt( (x-params.x0)*(x-params.x0) + (y-params.y0)*(y-params.y0) );
      return (Rfit - params.R)*(Rfit - params.R);
    }
  }
  
  
  public Fitter() {
    eiger = new EigenFinder();
    polyrf = new PolynomialRootFinder();
    spot = new SpotFitter();
    line = new LineFitter();
    circ = new CircleFitter();
    Ox = Oy = 0.0;
    automove = false;
  }
  
  public Fitter(Fitter oldfit) {
    eiger = oldfit.eiger;
    polyrf = oldfit.polyrf;
    spot = new SpotFitter(oldfit.spot);
    line = new LineFitter(oldfit.line);
    circ = new CircleFitter(oldfit.circ);
    automove = oldfit.automove;
    Ox = oldfit.Ox;
    Oy = oldfit.Oy;
    Sx = oldfit.Sx;
    Sy = oldfit.Sy;
    Sxx = oldfit.Sxx;
    Syy = oldfit.Syy;
    Sxy = oldfit.Sxy;
    Sxz = oldfit.Sxz;
    Syz = oldfit.Syz;
    Szz = oldfit.Szz;
    n = oldfit.n;    
  }
  
  public void addL(double x,double y) {
    if (automove && n==0) {
      Ox = x;
      Oy = y;
      n++; 
      return;
    }
    x -= Ox;
    y -= Oy;
    Sx += x;
    Sy += y;
    Sxx += x*x;
    Syy += y*y;
    Sxy += x*y;
    n++;
  }
  
  public void subL(double x,double y) {
    x -= Ox;
    y -= Oy;
    Sx -= x;
    Sy -= y;
    Sxx -= x*x;
    Syy -= y*y;
    Sxy -= x*y;
    n--;
  }

  public void addC(double x,double y) {
    if (automove && n==0) {
      Ox = x;
      Oy = y;
      n++; 
      return;
    }
    x -= Ox;
    y -= Oy;
    double z = x*x + y*y;
    Sx += x;
    Sy += y;
    Sxx += x*x;
    Syy += y*y;
    Szz += z*z;
    Sxy += x*y;
    Sxz += x*z;
    Syz += y*z;
    n++;
  }
  
  public void subC(double x,double y) {
    x -= Ox;
    y -= Oy;
    double z = x*x + y*y;
    Sx -= x;
    Sy -= y;
    Sxx -= x*x;
    Syy -= y*y;
    Szz -= z*z;
    Sxy -= x*y;
    Sxz -= x*z;
    Syz -= y*z;
    n--;
  }
  
  public void moveBy(double X,double Y) {
    if (automove && n<2) return;
    double Z = X*X + Y*Y;
    Szz += -4*(X*Sxz+Y*Syz) + (4*X*X+2*Z)*Sxx + (4*Y*Y+2*Z)*Syy + 8*X*Y*Sxy - 4*Z*(X*Sx+Y*Sy) + n*Z*Z;
    Syz += -Y*(3*Syy+Sxx+n*Z) + 2*X*(Y*Sx - Sxy) + (2*Y*Y+Z)*Sy;
    Sxz += -X*(3*Sxx+Syy+n*Z) + 2*Y*(X*Sy - Sxy) + (2*X*X+Z)*Sx;
    Sxy += n*X*Y - Y*Sx - X*Sy;
    Syy += n*Y*Y - 2*Y*Sy;
    Sxx += n*X*X - 2*X*Sx;
    Sy -= n*Y;
    Sx -= n*X;
    Ox += X;
    Oy += Y;
  }
  
  public void shiftZero(boolean b) { automove = b; }
  
  public Fitter reset() {
    Ox = Oy = Sx = Sy = Sxx = Syy = Sxy = Sxz = Syz = Szz = 0.0;
    n = 0;
    return this;
  }

  public Fitter resetAt(double X, double Y) {
    Ox = X; Oy = Y;
    Sx = Sy = Sxx = Syy = Sxy = Sxz = Syz = Szz = 0.0;
    n = 0;
    return this;
  }
  
  public Fitter join(Fitter f) {
    if (Ox!=f.Ox || Oy!=f.Oy) moveBy(f.Ox-Ox , f.Oy-Oy);
    Sx += f.Sx;
    Sy += f.Sy;
    Sxx += f.Sxx;
    Syy += f.Syy;
    Sxy += f.Sxy;
    Sxz += f.Sxz;
    Syz += f.Syz;
    Szz += f.Szz;
    n += f.n;
    return this;
  }

  
  public static void main(String[] args) {
    int N = 10000000;
    double s = 0.0;
    Fitter f = new Fitter();
    f.addL(1,1);
    f.addL(2,4);
    f.addL(3,7);
    f.line.fit();
    System.out.println(f.line.params.a + " " + f.line.params.b + " " + f.line.params.c);  // Expect -1/3 1 -2/3
    f.reset();
    f.addL(1,1);
    f.addL(4,2);
    f.addL(7,3);
    f.line.fit();
    System.out.println(f.line.params.a + " " + f.line.params.b + " " + f.line.params.c); // Expect 1 -1/3 -2/3
    f.reset();
    f.addC(0,1);
    f.addC(1,0);
    f.addC(Math.sqrt(0.5),Math.sqrt(0.5));
    f.circ.fit();
    System.out.println(f.circ.params.x0 + "," + f.circ.params.y0 + " " + f.circ.params.R); // Expect 0,0 1
    f.reset();
    f.addC(-1,-1);
    f.addC(0,0);
    f.addC(1,1);
    f.circ.fit();
    System.out.println(f.circ.params.x0 + "," + f.circ.params.y0 + " " + f.circ.params.R); // Expect ? ? ?--it's a line!
    f.reset();
    
    f.n = 100;
    f.Sx = 89.4835867610749;
    f.Sy = 51.6409778943963;
    f.Sxx = 81.9265880920993;
    f.Syy = 31.9465726515977;
    f.Szz = 130.0150529011205;
    f.Sxy = 43.2913521086051;
    f.Sxz = 101.9918389822410;
    f.Syz = 58.9510832501044;
    long t0 = System.currentTimeMillis();
    for (int i=0;i<N;i++) {
      double u = Math.ulp(f.Sx);
      f.Sx += u;
      f.Sy += u;
      f.Sxx += 2*u;
      f.Syy += 2*u;
      f.Szz += 4*u;
      f.Sxy += 2*u;
      f.Sxz += 3*u;
      f.Syz += 3*u;
      f.circ.fit();
      s += f.polyrf.x0*f.polyrf.x0 + f.polyrf.x1*f.polyrf.x1 + f.polyrf.x2*f.polyrf.x2 + f.polyrf.x3*f.polyrf.x3;
      //f.line.fit();
      //s += f.line.params.a*f.line.params.a + f.line.params.b*f.line.params.b + f.line.params.c*f.line.params.c;
    }
    s/=N;
    long t1 = System.currentTimeMillis();
    System.out.println(s);  // Expect just under 0.01233584
    System.out.println( "Million fits per second = " + (N/(1.0e3*(t1-t0))) );
  }
}

