plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.chatapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.chatapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 统一 Java 和 Kotlin 的兼容性级别
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // 确保与 Java 的 targetCompatibility 匹配
    }

    buildFeatures {
        viewBinding = true
    }

    // 防止由Apache POI可能引起的打包冲突 - 使用新的语法
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 网络请求库
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("org.jsoup:jsoup:1.16.1")

    // 处理简繁转换
    implementation("com.github.houbb:opencc4j:1.7.2")

    // MPAndroidChart图表库 - 用于显示Token消耗统计图表
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // HanLP轻量级版本
    implementation("com.hankcs:hanlp:portable-1.8.2")

    // Glide图片加载库 - 用于头像加载和圆形裁剪
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
    implementation("com.github.yalantis:ucrop:2.2.8")

    // 图片处理和缓存
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    implementation("jp.wasabeef:glide-transformations:4.3.0") // 使用Glide变换
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // 添加缺失的依赖以避免运行时错误
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // JSON处理
    implementation("com.google.code.gson:gson:2.10.1")

    // PDF库支持
    implementation("com.itextpdf:itextg:5.5.10")

    // Apache POI - Office文档支持
    implementation("org.apache.poi:poi:5.2.3")  // 支持DOC和XLS
    implementation("org.apache.poi:poi-ooxml:5.2.3")  // 支持DOCX和XLSX
    implementation("org.apache.poi:poi-scratchpad:5.2.3")  // 支持旧格式Office文档

    // XML解析支持
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")

    // 避免Log4j的API版本冲突 - 修正exclude语法
    implementation("org.apache.logging.log4j:log4j-api:2.18.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }

    // Markwon - Markdown渲染库
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")

    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}