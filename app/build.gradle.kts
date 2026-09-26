plugins {
 id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt")
}
android { namespace="com.dokstudio.obs"; compileSdk=36
 defaultConfig { applicationId="com.dokstudio.obs"; minSdk=29; targetSdk=36; versionCode=1; versionName="0.1.0-alpha" }
 buildFeatures { compose=true; buildConfig=true }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
val composeBom = dependencies.platform("androidx.compose:compose-bom:2025.08.01")
dependencies {
 implementation(composeBom); androidTestImplementation(composeBom)
 implementation("androidx.core:core-ktx:1.17.0"); implementation("androidx.activity:activity-compose:1.13.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0"); implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.navigation:navigation-compose:2.9.4"); implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.datastore:datastore-preferences:1.2.0")
 implementation("androidx.room:room-runtime:2.8.2"); implementation("androidx.room:room-ktx:2.8.2"); kapt("androidx.room:room-compiler:2.8.2")
 implementation("androidx.camera:camera-core:1.6.2"); implementation("androidx.camera:camera-camera2:1.6.2"); implementation("androidx.camera:camera-lifecycle:1.6.2"); implementation("androidx.camera:camera-view:1.6.2")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
 testImplementation("junit:junit:4.13.2"); androidTestImplementation("androidx.test.ext:junit:1.3.0"); androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
