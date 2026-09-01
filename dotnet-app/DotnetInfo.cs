using System.Diagnostics;
using System.Globalization;
using System.Reflection;
using System.Runtime;
using System.Runtime.InteropServices;

namespace DotnetApp;

/// <summary>
/// The .NET answer to <c>phpinfo()</c>: everything the runtime knows about
/// itself, on one page.
/// </summary>
/// <remarks>
/// Unlike <c>phpinfo()</c> this deliberately leaves out environment variables,
/// which routinely hold credentials. Even so, the page exposes plenty about the
/// runtime — keep it off the public internet.
/// </remarks>
internal static class DotnetInfo
{
    public static string Render()
    {
        var sections = string.Concat(
            "<h2>.NET runtime</h2>", RuntimeTable(),
            "<h2>Operating system</h2>", OperatingSystemTable(),
            "<h2>Memory</h2>", MemoryTable(),
            "<h2>Garbage collector</h2>", GarbageCollectorTable(),
            "<h2>Thread pool</h2>", ThreadPoolTable(),
            "<h2>Loaded assemblies</h2>", AssembliesTable());

        return $"""
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <title>dotnetinfo</title>
                {Html.Style}
            </head>
            <body>
                <h1>🟪 dotnetinfo</h1>
                <p><a href="/">&larr; back</a></p>
                {sections}
            </body>
            </html>
            """;
    }

    private static string RuntimeTable()
    {
        var process = Process.GetCurrentProcess();

        return Html.Table(
            Html.Row("Framework", RuntimeInformation.FrameworkDescription),
            Html.Row("Runtime identifier", RuntimeInformation.RuntimeIdentifier),
            Html.Row("Process architecture", RuntimeInformation.ProcessArchitecture),
            Html.Row("ASP.NET Core", typeof(WebApplication).Assembly.GetName().Version),
            Html.Row("Server GC", GCSettings.IsServerGC),
            Html.Row("Latency mode", GCSettings.LatencyMode),
            Html.Row("PID", Environment.ProcessId),
            Html.Row("Started at", process.StartTime.ToUniversalTime().ToString("O", CultureInfo.InvariantCulture)),
            Html.Row("Uptime", Seconds(SystemReadings.UptimeSeconds())),
            Html.Row("Process CPU time", Seconds(SystemReadings.ProcessCpuSeconds())),
            Html.Row("Working directory", Environment.CurrentDirectory));
    }

    private static string OperatingSystemTable()
    {
        var load = SystemReadings.GetSystemLoad();
        var memory = SystemReadings.GetSystemMemory();

        return Html.Table(
            Html.Row("Description", RuntimeInformation.OSDescription),
            Html.Row("Architecture", RuntimeInformation.OSArchitecture),
            Html.Row("Available processors", SystemReadings.SystemCpuCount()),
            Html.Row("Machine name", Environment.MachineName),
            Html.Row(
                "Load average (1/5/15m)",
                load is null
                    ? "n/a (not Linux)"
                    : string.Create(CultureInfo.InvariantCulture, $"{load.One:F2} / {load.Five:F2} / {load.Fifteen:F2}")),
            Html.Row(
                "System memory",
                memory is null
                    ? "n/a (not Linux)"
                    : $"{Html.Mib(memory.Used)} used / {Html.Mib(memory.Total)} total"));
    }

    private static string MemoryTable()
    {
        var info = GC.GetGCMemoryInfo();

        return Html.Table(
            Html.Row("Managed heap", Html.Mib(SystemReadings.ManagedMemoryBytes())),
            Html.Row("Heap committed", AfterFirstCollection(info.TotalCommittedBytes)),
            Html.Row("Heap size", AfterFirstCollection(info.HeapSizeBytes)),
            Html.Row("Fragmented", AfterFirstCollection(info.FragmentedBytes)),
            Html.Row("High memory load threshold", Html.Mib(info.HighMemoryLoadThresholdBytes)),
            Html.Row("Total available memory", Html.Mib(info.TotalAvailableMemoryBytes)),
            Html.Row("Working set", Html.Mib(SystemReadings.WorkingSetBytes())),
            Html.Row("Allocated since start", Html.Mib(SystemReadings.AllocatedBytes())));
    }

    private static string GarbageCollectorTable()
    {
        var rows = new List<string> { "<tr><th>Generation</th><th>Collections</th></tr>" };
        for (var generation = 0; generation <= GC.MaxGeneration; generation++)
        {
            rows.Add(Html.Row($"Gen {generation}", GC.CollectionCount(generation)));
        }

        rows.Add(Html.Row("Pause time (total)", Seconds(GC.GetTotalPauseDuration().TotalSeconds)));
        rows.Add(Html.Row("Concurrent", GC.GetGCMemoryInfo().Concurrent));

        return Html.Table([.. rows]);
    }

    private static string ThreadPoolTable()
    {
        ThreadPool.GetMinThreads(out var minWorker, out var minIo);
        ThreadPool.GetMaxThreads(out var maxWorker, out var maxIo);
        ThreadPool.GetAvailableThreads(out var freeWorker, out var freeIo);

        return Html.Table(
            Html.Row("Worker threads (min/max/available)", $"{minWorker} / {maxWorker} / {freeWorker}"),
            Html.Row("I/O threads (min/max/available)", $"{minIo} / {maxIo} / {freeIo}"),
            Html.Row("Thread pool threads", ThreadPool.ThreadCount),
            Html.Row("Queued work items", ThreadPool.PendingWorkItemCount),
            Html.Row("Completed work items", ThreadPool.CompletedWorkItemCount),
            Html.Row("OS threads", SystemReadings.ThreadCount()));
    }

    private static string AssembliesTable()
    {
        var rows = AppDomain.CurrentDomain
            .GetAssemblies()
            .Select(assembly => assembly.GetName())
            .OrderBy(name => name.Name, StringComparer.Ordinal)
            .Select(name => Html.Row(name.Name ?? "unknown", name.Version?.ToString() ?? "unknown"))
            .Prepend("<tr><th>Assembly</th><th>Version</th></tr>")
            .ToArray();

        return Html.Table(rows);
    }

    /// <summary>GCMemoryInfo stays zeroed until the first collection happens.</summary>
    private static string AfterFirstCollection(long bytes) =>
        bytes == 0 ? "n/a (no collection yet)" : Html.Mib(bytes);

    private static string Seconds(double value) =>
        string.Create(CultureInfo.InvariantCulture, $"{value:F3}s");
}
