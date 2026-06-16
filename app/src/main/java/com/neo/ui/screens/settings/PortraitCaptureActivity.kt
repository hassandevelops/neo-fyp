package com.neo.ui.screens.settings

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Portrait-locked QR scanner. The default ZXing [CaptureActivity] is landscape;
 * subclassing and locking orientation in the manifest gives a portrait scanner
 * that matches how users hold the phone.
 */
class PortraitCaptureActivity : CaptureActivity()
