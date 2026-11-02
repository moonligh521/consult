#!/bin/sh
# Measure the target host before starting the observability stack.
# Exit 1 if Langfuse/ClickHouse would contend with MySQL/Redis.

set -eu

need_mem_mb=10240
need_disk_gb=300
need_cpu=4

mem_mb=$(awk '/MemAvailable:/ {print int($2/1024)}' /proc/meminfo)
disk_gb=$(df -BG --output=avail /var/lib/docker 2>/dev/null | tail -1 | tr -dc '0-9')
if [ -z "$disk_gb" ]; then
  disk_gb=$(df -BG --output=avail / | tail -1 | tr -dc '0-9')
fi
cpu=$(nproc)

echo "available_memory_mb=$mem_mb"
echo "available_disk_gb=$disk_gb"
echo "cpu_cores=$cpu"
echo "budget_memory_mb=$need_mem_mb"
echo "budget_disk_gb=$need_disk_gb"
echo "budget_cpu=$need_cpu"

fail=0
if [ "$mem_mb" -lt "$need_mem_mb" ]; then
  echo "refuse: available memory below 10GB after core services must remain free"
  fail=1
fi
if [ "$disk_gb" -lt "$need_disk_gb" ]; then
  echo "refuse: available disk below 300GB; ClickHouse must not share a nearly-full MySQL volume"
  fail=1
fi
if [ "$cpu" -lt "$need_cpu" ]; then
  echo "refuse: fewer than 4 spare cores for collector/Langfuse/ClickHouse"
  fail=1
fi
if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "capacity_gate=pass"
