#! /bin/sh
mkdir -p META-INF/native-image

echo '[
  { 
    "name": "java.lang.reflect.AccessibleObject",
    "methods" : [{"name":"canAccess"}]
  }
  ]' | tee META-INF/native-image/logging.json

# remove -H:+StaticExecutableWithDynamicLibC for m1 builds

native-image -jar dartclj.jar --no-fallback \
    -J-Dclojure.spec.skip.macros=true -J-Dclojure.compiler.direct-linking=true \
    --verbose --no-server -J-Xmx3G \
    --report-unsupported-elements-at-runtime --native-image-info \
    # -H:+StaticExecutableWithDynamicLibC \
    -H:CCompilerOption=-pipe \
    -H:ReflectionConfigurationFiles=META-INF/native-image/logging.json

chmod +x dartclj
echo "Size of generated native-image `ls -sh dartclj`"
