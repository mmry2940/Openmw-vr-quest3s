plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.libopenmw.openmw"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.openmw.vr"
    minSdk = 24
    targetSdk = 34
    versionCode = 46
    versionName = "1.0"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keyFile = file(keystorePath)
      if (keyFile.exists() && System.getenv("STORE_PASSWORD") != null) {
        storeFile = keyFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("STORE_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  sourceSets {
    getByName("main") {
      jniLibs.srcDirs("src/main/jniLibs")
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

abstract class CopyPrebuiltNativeLibsTask : DefaultTask() {
  @get:OutputDirectory
  abstract val targetDirectory: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Optional
  abstract val sourceFiles: ConfigurableFileCollection

  @TaskAction
  fun copyLibs() {
    val target = targetDirectory.get().asFile
    target.mkdirs()
    sourceFiles.files.forEach { file ->
      if (file.isFile && file.name.endsWith(".so")) {
        val destName = if (file.name == "libopenmw_vr.so") "libopenmw.so" else file.name
        val destFile = File(target, destName)
        if (!destFile.exists() || destFile.length() != file.length()) {
          file.copyTo(destFile, overwrite = true)
        }
      } else if (file.isDirectory) {
        file.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.forEach { soFile ->
          val destName = if (soFile.name == "libopenmw_vr.so") "libopenmw.so" else soFile.name
          val destFile = File(target, destName)
          if (!destFile.exists() || destFile.length() != soFile.length()) {
            soFile.copyTo(destFile, overwrite = true)
          }
        }
      }
    }
  }
}

val copyPrebuiltNativeLibs = tasks.register<CopyPrebuiltNativeLibsTask>("copyPrebuiltNativeLibs") {
  group = "build"
  description = "Copies pre-built native libraries (including libopenmw.so) into src/main/jniLibs"
  targetDirectory.set(layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a"))
  sourceFiles.from(
    layout.projectDirectory.dir("prebuilt"),
    layout.projectDirectory.dir("../prebuilt"),
    layout.projectDirectory.dir("../buildscripts/prefix/arm64/lib"),
    layout.projectDirectory.dir("../buildscripts/build/arm64/openmw-prefix")
  )
}

tasks.named("preBuild") {
  dependsOn(copyPrebuiltNativeLibs)
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}


dependencies {
  implementation(libs.androidx.core.ktx)
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
  implementation("com.bugsnag:bugsnag-android:4.22.3")

  testImplementation(libs.junit)
}
