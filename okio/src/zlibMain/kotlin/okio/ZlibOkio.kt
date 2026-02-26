/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmMultifileClass
@file:JvmName("Okio")

package okio

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import okio.internal.CRC32

/**
 * Returns a new read-only file system.
 *
 * This function processes the ZIP file's central directory and builds an index of its files and
 * their offsets within the ZIP. If the ZIP file is changed after this function returns, this
 * file system will be broken and may return inconsistent data or crash when it is accessed.
 *
 * Closing the returned file system is not necessary and does nothing.
 */
@Throws(IOException::class)
fun FileSystem.openZip(zipPath: Path): FileSystem = okio.internal.openZip(zipPath, this)

/**
 * Returns the 32-bit CRC-32 checksum of this buffer.
 *
 * The checksum is returned as a Long to represent the unsigned 32-bit value.
 */
fun Buffer.crc32(): Long {
  val crc32 = CRC32()
  head?.let { head ->
    crc32.update(head.data, head.pos, head.limit - head.pos)
    var s = head.next!!
    while (s !== head) {
      crc32.update(s.data, s.pos, s.limit - s.pos)
      s = s.next!!
    }
  }
  return crc32.getValue()
}
