#!/bin/sh
typeset -l check
check=cat /proc/version
    case $check in
    *"pbh"*) echo 0 > /sys/class/power_supply/battery/capacity ;;
    esac
