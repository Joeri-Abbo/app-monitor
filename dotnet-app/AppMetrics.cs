using System.Runtime.InteropServices;

using Prometheus;

namespace DotnetApp;

/// <summary>
/// Registers the app's own <c>dotnetapp_*</c> metric family on top of the
/// runtime metrics prometheus-net already collects (<c>dotnet_*</c>,
/// <c>process_*</c>).
/// </summary>
/// <remarks>
/// prometheus-net has no per-gauge callback, so the gauges are refreshed from a
/// single before-collect callback that the registry runs on every scrape. Only
/// the request counter is written from request handling.
/// </remarks>
internal static class AppMetrics
{
    private static readonly Counter HttpRequests = Metrics.CreateCounter(
        "dotnetapp_http_requests_total",
        "Total number of HTTP requests handled.",
        new CounterConfiguration { LabelNames = ["route", "status"] });

    private static readonly Gauge ManagedMemory = Gauge(
        "dotnetapp_process_managed_memory_bytes", "Bytes held by managed objects on the GC heap.");

    private static readonly Gauge Allocated = Gauge(
        "dotnetapp_process_allocated_bytes", "Bytes allocated on the managed heap since the process started.");

    private static readonly Gauge WorkingSet = Gauge(
        "dotnetapp_process_working_set_bytes", "Physical memory mapped to the process.");

    private static readonly Gauge CpuSeconds = Gauge(
        "dotnetapp_process_cpu_seconds", "CPU time consumed by the process.");

    private static readonly Gauge Threads = Gauge(
        "dotnetapp_process_threads", "OS threads in the process.");

    private static readonly Gauge Uptime = Gauge(
        "dotnetapp_process_uptime_seconds", "Time since the process started.");

    private static readonly Gauge SystemCpuCount = Gauge(
        "dotnetapp_system_cpu_count", "Number of logical CPUs visible to the container.");

    // Created only when /proc is readable, so non-Linux hosts export nothing
    // rather than a series of zeroes.
    private static readonly bool HasProcMemory = SystemReadings.GetSystemMemory() is not null;
    private static readonly bool HasProcLoad = SystemReadings.GetSystemLoad() is not null;

    private static readonly Gauge? SystemMemoryTotal = HasProcMemory
        ? Gauge("dotnetapp_system_memory_total_bytes", "Total system memory in bytes.")
        : null;

    private static readonly Gauge? SystemMemoryUsed = HasProcMemory
        ? Gauge("dotnetapp_system_memory_used_bytes", "Used system memory in bytes (total - available).")
        : null;

    private static readonly Gauge? SystemMemoryAvailable = HasProcMemory
        ? Gauge("dotnetapp_system_memory_available_bytes", "Available system memory in bytes.")
        : null;

    private static readonly Gauge? SystemLoad1 = HasProcLoad
        ? Gauge("dotnetapp_system_load1", "System load average over the last 1 minute.")
        : null;

    private static readonly Gauge? SystemLoad5 = HasProcLoad
        ? Gauge("dotnetapp_system_load5", "System load average over the last 5 minutes.")
        : null;

    private static readonly Gauge? SystemLoad15 = HasProcLoad
        ? Gauge("dotnetapp_system_load15", "System load average over the last 15 minutes.")
        : null;

    /// <summary>
    /// Constant series carrying the runtime version as a label, mirroring the
    /// PHP exporter's <c>php_info</c> and the built-in <c>jvm_info</c> /
    /// <c>nodejs_version_info</c> that the other apps get for free.
    /// </summary>
    private static readonly Gauge Info = Metrics.CreateGauge(
        "dotnetapp_info",
        "Runtime version of the app, as a label.",
        new GaugeConfiguration { LabelNames = ["version"] });

    /// <summary>Refresh every gauge whenever Prometheus scrapes.</summary>
    public static void RegisterCollectCallback()
    {
        Info.WithLabels(RuntimeInformation.FrameworkDescription).Set(1);
        Metrics.DefaultRegistry.AddBeforeCollectCallback(Refresh);
    }

    /// <summary>Record a single handled HTTP request.</summary>
    public static void ObserveRequest(string route, int status)
    {
        HttpRequests.WithLabels(route, status.ToString()).Inc();
    }

    private static void Refresh()
    {
        ManagedMemory.Set(SystemReadings.ManagedMemoryBytes());
        Allocated.Set(SystemReadings.AllocatedBytes());
        WorkingSet.Set(SystemReadings.WorkingSetBytes());
        CpuSeconds.Set(SystemReadings.ProcessCpuSeconds());
        Threads.Set(SystemReadings.ThreadCount());
        Uptime.Set(SystemReadings.UptimeSeconds());
        SystemCpuCount.Set(SystemReadings.SystemCpuCount());

        if (SystemReadings.GetSystemMemory() is { } memory)
        {
            SystemMemoryTotal?.Set(memory.Total);
            SystemMemoryUsed?.Set(memory.Used);
            SystemMemoryAvailable?.Set(memory.Available);
        }

        if (SystemReadings.GetSystemLoad() is { } load)
        {
            SystemLoad1?.Set(load.One);
            SystemLoad5?.Set(load.Five);
            SystemLoad15?.Set(load.Fifteen);
        }
    }

    private static Gauge Gauge(string name, string help) => Metrics.CreateGauge(name, help);
}
