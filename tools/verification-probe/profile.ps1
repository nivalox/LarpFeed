# The verification probe's one profile: what it instruments, its own package and its log tag.
function Get-VerificationProbeProfile {
    return [pscustomobject]@{ Name = 'tiktok-whole-app-v1'; Package = 'com.zhiliaoapp.musically'; ProbePackage = 'app.larpfeed.verification'
        Instrumentation = 'app.larpfeed.verification.Probe'; LogTag = 'LarpfeedProbe' }
}
