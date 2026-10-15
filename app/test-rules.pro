# The QA runner discovers the fixture and its @Rule field through reflection.
# Keep the test fixture and runner intact; application code retains its normal release optimization.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontobfuscate
-dontoptimize

-keep class pl.vistula.checker.ProductionReadinessTest { *; }
-keep class androidx.test.** { *; }
-keep interface androidx.test.** { *; }
-dontwarn **
