#!/usr/bin/env python3
"""Play Store compliance audit for the merged release manifest + Gradle deps.

Usage:
    python3 scripts/audit_play_compliance.py <path_to_merged_manifest.xml> [path_to_build.gradle.kts]

Typical invocation in this repo:
    ./gradlew :app:processReleaseMainManifest
    python3 scripts/audit_play_compliance.py \
        app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml \
        app/build.gradle.kts

As of this writing this app declares only INTERNET, ACCESS_NETWORK_STATE, CAMERA,
and POST_NOTIFICATIONS — none flagged below — so a clean run is expected. This
script exists as a regression guard for when that permission set grows, not
because a violation is currently suspected.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Play Store target-SDK floor. Google raises this roughly once a year (most recently
# to API 36, effective August 31, 2026) — reconcile this constant against Google's
# current Play Console requirement before trusting a PASS here long-term.
REQUIRED_MIN_TARGET_SDK = 36

# Permissions triggering a manual Play Console declaration form or automated rejection.
HIGH_RISK_PERMISSIONS = {
    "android.permission.READ_SMS": "Requires SMS Declaration Form. Auto-rejected if not default SMS handler.",
    "android.permission.RECEIVE_SMS": "Requires SMS Declaration Form. Auto-rejected if not default SMS handler.",
    "android.permission.SEND_SMS": "Requires SMS Declaration Form. Auto-rejected if not default SMS handler.",
    "android.permission.READ_CALL_LOG": "Requires Call Log Declaration Form. Auto-rejected if not default dialer.",
    "android.permission.WRITE_CALL_LOG": "Requires Call Log Declaration Form. Auto-rejected if not default dialer.",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "Strictly audited. Must use MediaStore/Storage Access Framework unless file manager/antivirus.",
    "android.permission.ACCESS_BACKGROUND_LOCATION": "Requires background location declaration form and in-app disclosure video proof.",
    "android.permission.SYSTEM_ALERT_WINDOW": "High scrutiny overlay permission; restricted in many categories.",
    "android.permission.REQUEST_INSTALL_PACKAGES": "Requires package installer declaration form.",
    "android.permission.BIND_ACCESSIBILITY_SERVICE": "Requires Accessibility Declaration. Apps outside accessibility categories face bans.",
    "android.permission.SCHEDULE_EXACT_ALARM": "Requires exact alarm policy justification unless alarm/calendar/timer app.",
    "android.permission.USE_EXACT_ALARM": "Restricted exact alarm permission; policy strictly enforced.",
}

# Third-party SDK telemetry indicators requiring a Data Safety declaration.
TELEMETRY_SDK_SIGNATURES = {
    "com.google.android.gms:play-services-ads": {
        "name": "Google Mobile Ads (AdMob)",
        "data_collected": "Device ID, IP, Ad Interaction, Diagnostics",
        "requirement": "Must declare 'Ad ID (AAID)' and 'App interactions' in Data Safety.",
    },
    "com.google.firebase:firebase-analytics": {
        "name": "Firebase Analytics",
        "data_collected": "Device identifiers, Analytics, Usage data",
        "requirement": "Must declare 'App interactions' and 'Diagnostics' in Data Safety.",
    },
    "com.facebook.android:facebook-": {
        "name": "Meta Audience Network / SDK",
        "data_collected": "Advertising ID, User identifiers, Device attributes",
        "requirement": "Must declare third-party data sharing for Advertising/Marketing.",
    },
    "com.appsflyer:af-android-sdk": {
        "name": "AppsFlyer",
        "data_collected": "Advertising ID, IP, Install referrers",
        "requirement": "Must declare third-party attribution tracking.",
    },
    "com.adjust.sdk:adjust-android": {
        "name": "Adjust",
        "data_collected": "Device identifiers, AAID, Session telemetry",
        "requirement": "Must declare third-party attribution tracking.",
    },
}


def audit_manifest(manifest_path: Path) -> bool:
    print(f"\n[+] Auditing Merged Manifest: {manifest_path}")
    if not manifest_path.exists():
        print(f"[!] ERROR: Manifest file not found at {manifest_path}")
        return False

    tree = ET.parse(manifest_path)
    root = tree.getroot()
    ns = {"android": "http://schemas.android.com/apk/res/android"}

    has_errors = False

    uses_sdk = root.find("uses-sdk")
    if uses_sdk is not None:
        target_sdk = uses_sdk.attrib.get(f"{{{ns['android']}}}targetSdkVersion")
        if target_sdk:
            target_sdk = int(target_sdk)
            if target_sdk < REQUIRED_MIN_TARGET_SDK:
                print(f"[CRITICAL] targetSdkVersion is {target_sdk}. Minimum required: {REQUIRED_MIN_TARGET_SDK}.")
                has_errors = True
            else:
                print(f"[PASS] targetSdkVersion = {target_sdk} (Compliant with API {REQUIRED_MIN_TARGET_SDK}+)")
        else:
            print("[WARNING] targetSdkVersion not declared in manifest (verify build.gradle.kts).")
    else:
        print("[WARNING] No <uses-sdk> block found in manifest.")

    declared_permissions = []
    for perm in root.findall("uses-permission"):
        perm_name = perm.attrib.get(f"{{{ns['android']}}}name")
        if perm_name:
            declared_permissions.append(perm_name)

    print(f"\n[+] Total Permissions Detected: {len(declared_permissions)}")
    for perm in declared_permissions:
        print(f"    - {perm}")
        if perm in HIGH_RISK_PERMISSIONS:
            print(f"[RED FLAG] High-Risk Permission Detected: {perm}")
            print(f"    Reason: {HIGH_RISK_PERMISSIONS[perm]}")
            has_errors = True

    for service in root.findall(".//service"):
        name = service.attrib.get(f"{{{ns['android']}}}name", "UnknownService")
        fgs_type = service.attrib.get(f"{{{ns['android']}}}foregroundServiceType")
        if "android.permission.FOREGROUND_SERVICE" in declared_permissions and not fgs_type:
            print(f"[WARNING] Service '{name}' may run as FGS without explicit foregroundServiceType declared.")

    return not has_errors


def audit_dependencies(gradle_file: Path) -> None:
    print(f"\n[+] Scanning Gradle Dependencies: {gradle_file}")
    if not gradle_file.exists():
        print(f"[!] Warning: Build file not found at {gradle_file}. Skipping SDK audit.")
        return

    content = gradle_file.read_text(encoding="utf-8")
    detected_sdks = [meta for sig, meta in TELEMETRY_SDK_SIGNATURES.items() if sig in content]

    if detected_sdks:
        print(f"[!] Detected {len(detected_sdks)} Third-Party Telemetry SDK(s):")
        for sdk in detected_sdks:
            print(f"  - {sdk['name']}")
            print(f"    Collected: {sdk['data_collected']}")
            print(f"    Action: {sdk['requirement']}")
    else:
        print("[PASS] No known high-risk telemetry SDKs flagged in build script.")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 audit_play_compliance.py <path_to_merged_manifest.xml> [path_to_build.gradle.kts]")
        sys.exit(1)

    manifest = Path(sys.argv[1])
    gradle = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("app/build.gradle.kts")

    passed = audit_manifest(manifest)
    audit_dependencies(gradle)

    if not passed:
        print("\n[RESULT] Audit FAILED. Resolve blocking permissions/SDK issues prior to submission.")
        sys.exit(1)
    else:
        print("\n[RESULT] Basic compliance audit PASSED.")
        sys.exit(0)
