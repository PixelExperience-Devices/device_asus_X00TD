#! /vendor/bin/sh

# Set GSM baseband
if strings /vendor/firmware_mnt/image/modem.b17 | grep "^MPSS.AT" >> /dev/null; then
    setprop gsm.version.baseband `strings /vendor/firmware_mnt/image/modem.b17 | grep "^MPSS.AT" | head -1`
fi
