# Copyright (C) 2009 The Android Open Source Project
# Copyright (c) 2011, The Linux Foundation. All rights reserved.
# Copyright (C) 2017-2018 The LineageOS Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import hashlib
import common
import re

def FullOTA_Assertions(info):
  AddModemAssertion(info)
  return

def IncrementalOTA_Assertions(info):
  AddModemAssertion(info)
  return

def AddModemAssertion(info):
  android_info = info.input_zip.read("OTA/android-info.txt")
  m = re.search(r'require\s+version-modem\s*=\s*(.+)', android_info)
  f = re.search(r'require\s+version-firmware\s*=\s*(.+)', android_info)
  if m and f:
    version_modem = m.group(1).rstrip()
    version_firmware = f.group(1).rstrip()
    if ((len(version_modem) and '*' not in version_modem) and \
    (len(version_firmware) and '*' not in version_firmware)):
      cmd = 'assert(X00TD.verify_modem("' + version_modem + '") == "1" || \
abort("Error: This package requires firmware version ' + version_firmware + \
' or newer. Please upgrade firmware and retry!"););'
      info.script.AppendExtra(cmd)
  return

def FullOTA_InstallEnd(info):
  info.script.AppendExtra('ifelse(is_mounted("/system"), unmount("/system"));');
  info.script.AppendExtra('ifelse(is_mounted("/vendor"), unmount("/vendor"));');
  info.script.AppendExtra('ifelse(is_mounted("/persist"), unmount("/persist"));');
  info.script.Mount("/system")
  info.script.Mount("/vendor")
  info.script.Mount("/persist")
  RunCustomScript(info, "device_check.sh", "")
  info.script.Unmount("/system")
  info.script.Unmount("/vendor")
  info.script.Unmount("/persist")
  return

def RunCustomScript(info, name, arg):
  info.script.AppendExtra(('run_program("/tmp/install/bin/%s", "%s");' % (name, arg)))
  return
