package com.parc.fitnesstimer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt-enabled Application class. Must be referenced in AndroidManifest
 * android:name=".ParcApplication".
 */
@HiltAndroidApp
class ParcApplication : Application()
