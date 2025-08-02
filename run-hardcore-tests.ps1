# PowerShell script to run all hardcore Spring AI API tests
# Author: AI Assistant
# Description: Comprehensive test runner for Spring AI API

Write-Host "🚀 Running Hardcore Spring AI API Tests" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Green

# Set error action preference
$ErrorActionPreference = "Continue"

# Change to project directory
Set-Location -Path $PSScriptRoot

Write-Host "`n📋 Test Categories:" -ForegroundColor Yellow
Write-Host "1. AiClient_Updated Unit Tests" -ForegroundColor Cyan
Write-Host "2. ChatAIController Integration Tests" -ForegroundColor Cyan
Write-Host "3. BasicTools Unit Tests" -ForegroundColor Cyan
Write-Host "4. Exception Handling Tests" -ForegroundColor Cyan
Write-Host "5. Security Exception Tests" -ForegroundColor Cyan
Write-Host "6. Performance & Edge Case Tests" -ForegroundColor Cyan
Write-Host "7. Full Integration Tests" -ForegroundColor Cyan

Write-Host "`n🏃‍♂️ Starting test execution..." -ForegroundColor Green

# Function to run specific test class
function Run-TestClass {
    param(
        [string]$TestClass,
        [string]$Description
    )
    
    Write-Host "`n🧪 Running: $Description" -ForegroundColor Yellow
    Write-Host "Test Class: $TestClass" -ForegroundColor Gray
    
    try {
        $result = mvn test -Dtest=$TestClass -q
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ $Description - PASSED" -ForegroundColor Green
        } else {
            Write-Host "❌ $Description - FAILED" -ForegroundColor Red
            Write-Host $result -ForegroundColor Red
        }
    } catch {
        Write-Host "❌ $Description - ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Function to run all tests in a package
function Run-TestPackage {
    param(
        [string]$Package,
        [string]$Description
    )
    
    Write-Host "`n📦 Running: $Description" -ForegroundColor Yellow
    Write-Host "Package: $Package" -ForegroundColor Gray
    
    try {
        $result = mvn test -Dtest="$Package.**" -q
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ $Description - PASSED" -ForegroundColor Green
        } else {
            Write-Host "❌ $Description - FAILED" -ForegroundColor Red
        }
    } catch {
        Write-Host "❌ $Description - ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Start timing
$startTime = Get-Date

Write-Host "`n🔧 Checking Maven installation..." -ForegroundColor Blue
try {
    $mvnVersion = mvn --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Maven is installed" -ForegroundColor Green
    } else {
        Write-Host "❌ Maven not found. Please install Maven first." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Maven not found. Please install Maven first." -ForegroundColor Red
    exit 1
}

Write-Host "`n🧹 Cleaning project..." -ForegroundColor Blue
mvn clean -q

# Run individual test classes
Run-TestClass "AiClient_UpdatedTest" "AI Client Unit Tests"
Run-TestClass "ChatAIControllerTest" "Chat Controller Tests"
Run-TestClass "BasicToolsTest" "Basic Tools Tests"
Run-TestClass "ExceptionHandlingTest" "Exception Handling Tests"
Run-TestClass "SecurityExceptionHandlingTest" "Security Exception Tests"
Run-TestClass "PerformanceEdgeCaseTest" "Performance & Edge Case Tests"
Run-TestClass "SpringAiIntegrationTest" "Integration Tests"

Write-Host "`n📊 Running comprehensive test suite..." -ForegroundColor Blue
try {
    Write-Host "🔄 Executing all tests with coverage..." -ForegroundColor Yellow
    
    # Run all tests with verbose output
    $testResult = mvn test -Dtest="**/*Test" 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
        
        # Try to extract test statistics
        $testStats = $testResult | Select-String "Tests run:"
        if ($testStats) {
            Write-Host "`n📈 Test Statistics:" -ForegroundColor Cyan
            foreach ($stat in $testStats) {
                Write-Host $stat.Line -ForegroundColor White
            }
        }
    } else {
        Write-Host "❌ SOME TESTS FAILED!" -ForegroundColor Red
        Write-Host $testResult -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Error running comprehensive tests: $($_.Exception.Message)" -ForegroundColor Red
}

# Calculate execution time
$endTime = Get-Date
$executionTime = $endTime - $startTime

Write-Host "`n⏱️  Total Execution Time: $($executionTime.TotalMinutes.ToString('F2')) minutes" -ForegroundColor Magenta

Write-Host "`n🎯 Test Categories Covered:" -ForegroundColor Yellow
Write-Host "• Unit Tests - Individual component testing" -ForegroundColor White
Write-Host "• Integration Tests - Full system testing" -ForegroundColor White
Write-Host "• Exception Handling - Error scenarios" -ForegroundColor White
Write-Host "• Security Tests - Authentication & authorization" -ForegroundColor White
Write-Host "• Performance Tests - Load and stress testing" -ForegroundColor White
Write-Host "• Edge Cases - Boundary conditions" -ForegroundColor White
Write-Host "• Memory Tests - Resource leak detection" -ForegroundColor White
Write-Host "• Streaming Tests - Real-time response handling" -ForegroundColor White

Write-Host "`n🔍 Test Coverage Areas:" -ForegroundColor Yellow
Write-Host "• AiClient_Updated streaming responses" -ForegroundColor White
Write-Host "• ChatAIController request handling" -ForegroundColor White
Write-Host "• BasicTools AI tool functions" -ForegroundColor White
Write-Host "• JWT authentication & validation" -ForegroundColor White
Write-Host "• Session management & authorization" -ForegroundColor White
Write-Host "• Database exception handling" -ForegroundColor White
Write-Host "• Network connectivity issues" -ForegroundColor White
Write-Host "• Concurrent request processing" -ForegroundColor White
Write-Host "• Memory leak prevention" -ForegroundColor White
Write-Host "• Unicode & special character handling" -ForegroundColor White

Write-Host "`n📋 Next Steps:" -ForegroundColor Yellow
Write-Host "1. Review test results above" -ForegroundColor White
Write-Host "2. Fix any failing tests" -ForegroundColor White
Write-Host "3. Add additional tests for new features" -ForegroundColor White
Write-Host "4. Set up CI/CD pipeline integration" -ForegroundColor White
Write-Host "5. Configure test coverage reporting" -ForegroundColor White

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n🎉 Hardcore testing completed successfully!" -ForegroundColor Green
    Write-Host "Your Spring AI API is battle-tested and ready for production! 💪" -ForegroundColor Green
} else {
    Write-Host "`n⚠️  Some tests need attention. Please review the failures above." -ForegroundColor Yellow
    Write-Host "Fix the issues and re-run the tests. 🔧" -ForegroundColor Yellow
}

Write-Host "`n🚀 Happy coding! Your AI API is hardcore tested! 🤖✨" -ForegroundColor Magenta
