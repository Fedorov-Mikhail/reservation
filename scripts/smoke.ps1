param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$api = $BaseUrl.TrimEnd('/') + '/api/v1'
$resourceBody = @{ name = 'Demo room ' + [DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss'); location = 'Local test' } | ConvertTo-Json
$resource = Invoke-RestMethod -Method Post -Uri "$api/resources" -ContentType 'application/json; charset=utf-8' -Body $resourceBody
$start = [DateTimeOffset]::UtcNow.AddDays(1)
$start = [DateTimeOffset]::new($start.Year, $start.Month, $start.Day, 10, 0, 0, [TimeSpan]::Zero)
$end = $start.AddHours(1)
$payload = @{ startsAt = $start.ToString('yyyy-MM-ddTHH:mm:ssZ'); endsAt = $end.ToString('yyyy-MM-ddTHH:mm:ssZ') } | ConvertTo-Json
$booking = Invoke-RestMethod -Method Post -Uri "$api/resources/$($resource.id)/bookings" -ContentType 'application/json' -Body $payload
Write-Output "Resource: $($resource.id)"
Write-Output "Booking:  $($booking.id)"
try {
    $null = Invoke-RestMethod -Method Post -Uri "$api/resources/$($resource.id)/bookings" -ContentType 'application/json' -Body $payload
    throw 'Expected a conflict but the duplicate booking succeeded.'
} catch {
    if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 409) { throw }
    Write-Output 'Overlap correctly rejected: HTTP 409'
}
$from = $start.AddHours(-1).ToString('yyyy-MM-ddTHH:mm:ssZ')
$to = $end.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ssZ')
$available = Invoke-RestMethod -Uri "$api/resources/$($resource.id)/availability?from=$from&to=$to&minDurationMinutes=60"
$available | ConvertTo-Json -Depth 5
$cancelled = Invoke-RestMethod -Method Post -Uri "$api/bookings/$($booking.id)/cancel"
$repeated = Invoke-RestMethod -Method Post -Uri "$api/bookings/$($booking.id)/cancel"
if ($cancelled.status -ne 'CANCELLED' -or $repeated.cancelledAt -ne $cancelled.cancelledAt) { throw 'Cancellation check failed' }
Write-Output 'Cancellation and repeated cancellation: OK'
Write-Output 'Demo resource and cancelled booking remain in the database.'

