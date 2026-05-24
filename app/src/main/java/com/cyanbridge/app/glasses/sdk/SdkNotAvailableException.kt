package com.cyanbridge.app.glasses.sdk

/**
 * Thrown when AAR-based SDK operations are called but glasses_sdk_20250723_v01.aar
 * is not present in app/libs/. See docs/heycyan-sdk-analysis.md for setup instructions.
 */
class SdkNotAvailableException(message: String) : Exception(message)
