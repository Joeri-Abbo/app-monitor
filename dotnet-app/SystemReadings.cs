using System.Diagnostics;
using System.Globalization;
using System.Text.RegularExpressions;

namespace DotnetApp;

/// <summary>
/// Live process and host readings, shared by the homepage, the /dotnetinfo
/// report and the Prometheus gauges.
/// </summary>
/// <remarks>
/// The host-wide numbers are read straight from <c>/proc</c>, exactly like the
/// PHP, Java and Node apps do, so every container reports memory and load the
/// same way. On non-Linux hosts they return <c>null</c> and the callers omit
/// them.
/// </remarks>
internal static partial class SystemReadings
{
    /// <summary>Host memory in bytes; <paramref name="Used"/> is total - available.</summary>
    public sealed record SystemMemory(long Total, long Available, long Used)
    {
        public double UsedPercentage => Total == 0 ? 0 : (double)Used / Total * 100;
    }

    /// <summary>Load averages over the last 1, 5 and 15 minutes.</summary>
    public sealed record LoadAverage(double One, double Five, double Fifteen);

    // -- This process ---------------------------------------------------------

    /// <summary>Bytes currently held by managed objects on the GC heap.</summary>
    public static long ManagedMemoryBytes() => GC.GetTotalMemory(forceFullCollection: false);

    /// <summary>
    /// Bytes ever allocated on the managed heap. Unlike <c>GCMemoryInfo</c>, which
    /// stays empty until the first collection, this is meaningful from startup.
    /// </summary>
    public static long AllocatedBytes() => GC.GetTotalAllocatedBytes(precise: false);

    public static long WorkingSetBytes() => Environment.WorkingSet;

    public static double ProcessCpuSeconds() =>
        Process.GetCurrentProcess().TotalProcessorTime.TotalSeconds;

    public static int ThreadCount() => Process.GetCurrentProcess().Threads.Count;

    public static double UptimeSeconds() =>
        (DateTime.Now - Process.GetCurrentProcess().StartTime).TotalSeconds;

    // -- Host -----------------------------------------------------------------

    public static int SystemCpuCount() => Environment.ProcessorCount;

    /// <summary>Host memory from <c>/proc/meminfo</c> (Linux only).</summary>
    public static SystemMemory? GetSystemMemory()
    {
        var info = ReadProc("/proc/meminfo");
        if (info is null)
        {
            return null;
        }

        long? total = null;
        long? available = null;

        foreach (Match match in MemInfoLine().Matches(info))
        {
            var bytes = long.Parse(match.Groups[2].Value, CultureInfo.InvariantCulture) * 1024;
            switch (match.Groups[1].Value)
            {
                case "MemTotal":
                    total = bytes;
                    break;
                case "MemAvailable":
                    available = bytes;
                    break;
            }
        }

        if (total is null || available is null)
        {
            return null;
        }

        return new SystemMemory(total.Value, available.Value, total.Value - available.Value);
    }

    /// <summary>Load averages from <c>/proc/loadavg</c> (Linux only).</summary>
    public static LoadAverage? GetSystemLoad()
    {
        var contents = ReadProc("/proc/loadavg");
        if (contents is null)
        {
            return null;
        }

        var fields = contents.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (fields.Length < 3)
        {
            return null;
        }

        if (!double.TryParse(fields[0], CultureInfo.InvariantCulture, out var one) ||
            !double.TryParse(fields[1], CultureInfo.InvariantCulture, out var five) ||
            !double.TryParse(fields[2], CultureInfo.InvariantCulture, out var fifteen))
        {
            return null;
        }

        return new LoadAverage(one, five, fifteen);
    }

    private static string? ReadProc(string path)
    {
        try
        {
            return File.Exists(path) ? File.ReadAllText(path) : null;
        }
        catch (IOException)
        {
            return null;
        }
        catch (UnauthorizedAccessException)
        {
            return null;
        }
    }

    [GeneratedRegex(@"^(\w+):\s+(\d+)\s+kB$", RegexOptions.Multiline)]
    private static partial Regex MemInfoLine();
}
