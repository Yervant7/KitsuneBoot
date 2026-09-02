# KitsuneBoot - ProGuard & R8 Configuration
# Optimized according to modern AGP and R8 standards

-repackageclasses 'o'
-allowaccessmodification
-optimizations !code/simplification/arithmetic
-keepattributes InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
