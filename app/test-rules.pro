# The QA runner discovers the fixture and its @Rule field through reflection.
# Keep only the test fixture; application code retains its normal release optimization.
-keepattributes *Annotation*
-keep class pl.vistula.checker.ProductionReadinessTest { *; }
