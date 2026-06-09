# Test API MusicClubApp
$baseUrl = "http://localhost:8080"

# 1. Rejestracja
Write-Host "=== REJESTRACJA ===" -ForegroundColor Cyan
$registerBody = @{
    username = "charlie"
    email = "charlie@test.com"
    password = "password123"
} | ConvertTo-Json

$registerResponse = Invoke-WebRequest -Uri "$baseUrl/api/auth/register" `
    -Method POST `
    -ContentType "application/json" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($registerBody)) `
    -UseBasicParsing

$registerData = $registerResponse.Content | ConvertFrom-Json
Write-Host "Rejestracja: $($registerData.message)" -ForegroundColor Green

# 2. Logowanie
Write-Host "`n=== LOGOWANIE ===" -ForegroundColor Cyan
$loginBody = @{
    username = "charlie"
    password = "password123"
} | ConvertTo-Json

$loginResponse = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($loginBody)) `
    -UseBasicParsing

$loginData = $loginResponse.Content | ConvertFrom-Json
$token = $loginData.token
Write-Host "Token: $token" -ForegroundColor Green

# 3. Dostęp do API z tokenem
Write-Host "`n=== LISTA UZYTKOWNIKOW ===" -ForegroundColor Cyan
$usersUri = "$baseUrl/api/users?page=0`&size=10"
$usersResponse = Invoke-WebRequest -Uri $usersUri `
    -Method GET `
    -Headers @{"Authorization" = "Bearer $token"} `
    -UseBasicParsing

$usersData = $usersResponse.Content | ConvertFrom-Json
Write-Host "Liczba uzytkownikow: $($usersData.totalElements)" -ForegroundColor Green
$usersData.content | ForEach-Object {
    Write-Host "  - $($_.username) ($($_.email))"
}

# 4. Swagger UI
Write-Host "`n=== SWAGGER UI ===" -ForegroundColor Cyan
Write-Host "Otworz: $baseUrl/swagger-ui/index.html" -ForegroundColor Yellow

# 5. Test paginacji
Write-Host "`n=== PAGINACJA ===" -ForegroundColor Cyan
$pagedUri = "$baseUrl/api/users?page=0`&size=5`&sort=username,asc"
$pagedResponse = Invoke-WebRequest -Uri $pagedUri `
    -Method GET `
    -Headers @{"Authorization" = "Bearer $token"} `
    -UseBasicParsing

$pagedData = $pagedResponse.Content | ConvertFrom-Json
Write-Host "Strona 0: $($pagedData.numberOfElements) elementow (razem $($pagedData.totalElements))" -ForegroundColor Green

