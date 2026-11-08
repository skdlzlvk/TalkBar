import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

// 출시 서명 정보: 프로젝트 맨 위 keystore.properties (git에 안 올라감, RELEASE.md 참고).
// 파일이 없으면 release는 서명 없이 만들어진다 (설치·업로드 불가, 컴파일 확인만).
val keystoreProps = Properties().apply {
  rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
  namespace = "com.siryul.talkbar"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.siryul.talkbar"
    // Live Update API(Android 16, API 36)를 버전 분기 없이 쓰려고 36부터.
    minSdk = 36
    targetSdk = 36
    // 스토어에 올릴 때마다 versionCode를 1씩 올린다.
    versionCode = 1
    versionName = "1.0.0"
  }

  signingConfigs {
    if (keystoreProps.isNotEmpty()) {
      create("release") {
        storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      signingConfig = signingConfigs.findByName("release")
    }
  }

  bundle {
    // 문구가 작아서 언어별로 쪼개지 않는다. 그래야 '앱 언어'를 바꿔도 다운로드 없이 바로 바뀐다.
    language {
      enableSplit = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

// 외부 라이브러리 없음: 안드로이드 기본 API만 사용.
