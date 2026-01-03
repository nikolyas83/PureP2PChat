$env:JAVA_HOME = "$PSScriptRoot\jdk\jdk-21.0.5+11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "$PSScriptRoot\android-sdk"
$env:PATH = "$env:ANDROID_HOME\cmdline-tools\cmdline-tools\bin;$env:PATH"
