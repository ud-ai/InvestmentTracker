// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    kotlin("jvm") version "1.9.22"


    id("com.google.gms.google-services") version "4.3.15" apply false // Add the Google Services plugin
}

buildscript {
    repositories {
        google()  // Make sure Google repository is included
        mavenCentral() // For other dependencies
    }
    dependencies {
        classpath("com.google.gms:google-services:4.3.15") // Firebase Google services
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.2") // Firebase Crashlytics (if using)
    }
}


