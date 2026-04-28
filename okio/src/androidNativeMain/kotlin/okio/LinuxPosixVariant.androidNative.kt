package okio

import platform.posix.android_get_device_api_level

actual fun getAndroidDeviceApiLevel(): Int {
  return android_get_device_api_level()
}
