# fix-cors-issues.ps1
# PowerShell script to remove @CrossOrigin annotations from all controllers

Write-Host "Fixing CORS Issues by Removing Controller @CrossOrigin Annotations..."

# Get all controller files
$controllers = @(
    "AdminController.java",
    "AuthController.java", 
    "DebugController.java",
    "JobController.java",
    "JobMatchingController.java",
    "ScraperController.java",
    "TestController.java",
    "TestCVController.java"
)

foreach ($controller in $controllers) {
    $path = "src\main\java\com\example\jobportal\controller\$controller"
    
    if (Test-Path $path) {
        Write-Host "Processing $controller..."
        
        # Read the file
        $content = Get-Content $path -Raw
        
        # Remove @CrossOrigin annotations (with or without parameters)
        $content = $content -replace '@CrossOrigin\s*(\([^)]*\))?\s*\n', ''
        $content = $content -replace '@CrossOrigin\s*(\([^)]*\))?\s*', ''
        
        # Remove the import if it exists and is no longer needed
        if ($content -notmatch '@CrossOrigin') {
            $content = $content -replace 'import\s+org\.springframework\.web\.bind\.annotation\.CrossOrigin;\s*\n', ''
        }
        
        # Save the file
        Set-Content $path $content
        Write-Host "Fixed $controller"
    }
}

Write-Host ""
Write-Host "CORS annotations removed from controllers."
Write-Host "CORS is now handled globally in SecurityConfig.java"
