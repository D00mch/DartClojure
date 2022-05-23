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
  }
  ]' | tee META-INF/native-image/logging.json

native-image -cp app.jar -jar app.jar \
	     -H:Name=app -H:+ReportExceptionStackTraces \
	     -J-Dclojure.spec.skip.macros=true \
             -J-Dclojure.compiler.direct-linking=true -J-Xmx3G \
	     --verbose --no-fallback --no-server\
	     --report-unsupported-elements-at-runtime --native-image-info \
	     -H:+StaticExecutableWithDynamicLibC -H:CCompilerOption=-pipe \
	     -H:ReflectionConfigurationFiles=META-INF/native-image/logging.json
chmod +x app
echo "Size of generated native-image `ls -sh app`"
