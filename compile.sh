#! /bin/sh
mkdir -p META-INF/native-image

echo '[
  {
    "name" : "java.lang.String",
    "allDeclaredConstructors" : true,
    "allPublicConstructors" : true,
    "allDeclaredMethods" : true,
    "allPublicMethods" : true,
    "allDeclaredClasses" : true,
    "allPublicClasses" : true
  },
  {
    "name" : "java.lang.Character",
    "allDeclaredConstructors" : true,
    "allPublicConstructors" : true,
    "allDeclaredMethods" : true,
    "allPublicMethods" : true,
    "allDeclaredClasses" : true,
    "allPublicClasses" : true
  },
  {
    "name" : "java.util.Base64",
    "allDeclaredConstructors" : true,
    "allPublicConstructors" : true,
    "allDeclaredMethods" : true,
    "allPublicMethods" : true,
    "allDeclaredClasses" : true,
    "allPublicClasses" : true
  }
  ]' | tee META-INF/native-image/logging.json

# remove -H:+StaticExecutableWithDynamicLibC flag for osx m1 buils

native-image -jar dartclj.jar --no-server --initialize-at-build-time --no-fallback \
	     -J-Dclojure.spec.skip.macros=true -J-Dclojure.compiler.direct-linking=true -J-Xmx3G \
	     --initialize-at-build-time --enable-http --enable-https --verbose --no-fallback --no-server\
	     --report-unsupported-elements-at-runtime --native-image-info \
	     -H:+StaticExecutableWithDynamicLibC \
             -H:CCompilerOption=-pipe \
	     --allow-incomplete-classpath \
	     -H:ReflectionConfigurationFiles=META-INF/native-image/logging.json

chmod +x dartclj
echo "Size of generated native-image `ls -sh dartclj`"
