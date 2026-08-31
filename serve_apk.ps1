
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://192.168.31.93:8000/")
$listener.Start()
Write-Host "Server listening on http://192.168.31.93:8000/"
try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $response = $context.Response
        $file = "C:\Users\ychan\.gemini\antigravity\brain\8d8b1530-2f7a-4e07-a4a0-c8a690a97c25\app-debug.apk"
        if (Test-Path $file) {
            $bytes = [System.IO.File]::ReadAllBytes($file)
            $response.ContentType = "application/vnd.android.package-archive"
            $response.ContentLength64 = $bytes.Length
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $response.StatusCode = 404
        }
        $response.Close()
    }
} finally {
    $listener.Stop()
}

