"""Checks transactional patch behavior and downstream safety; not Gradle/runtime tests."""
from pathlib import Path
import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('dev_identity', ROOT / 'scripts/apply-development-identity.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

GRADLE_FIXTURE = '''android {
    productFlavors {
        ststable { applicationId "org.smarttube.stable" }
        stmobile {
            applicationId "com.codesculptor.smartertube"
            versionCode 23743
            versionName "v0.9.10-beta.1+st32.68"
        }
    }
}
'''
STRINGS_FIXTURE = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name" translatable="false">SmarterTube</string>
    <string name="mobile_about_title">About SmarterTube</string>
    <string name="mobile_about_drawer_entry">About SmarterTube</string>
    <string name="mobile_about_body">SmarterTube is a phone and tablet fork of SmartTube by yuliskov. The YouTube client code is upstream\\'s work, merged from upstream on every release. This fork adds the phone/tablet UI on top.\\n\\nLicensed under MIT, same as upstream.</string>
    <string name="mobile_stats_endpoint" translatable="false">https://smartertube-stats.codesculptor.workers.dev</string>
</resources>
'''
HOME_FIXTURE = '''public class MobileBrowseActivity {
        dialogs.add(done -> LaunchUpdateNotices.onHomeCreated(this, done));
}
'''
ABOUT_FIXTURE = '''public class MobileAboutActivity {
        findViewById(R.id.btn_check_updates).setOnClickListener(v -> checkForUpdates());
}
'''
MANIFEST_FIXTURE = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round" /></manifest>'''


class DevelopmentIdentityTests(unittest.TestCase):
    def test_gradle_mobile_flavor_only(self):
        after = mod.patch_gradle(GRADLE_FIXTURE)
        self.assertIn('applicationId "org.smarttube.stable"', after)
        self.assertIn('applicationId "dev.smarttubemobile.unofficial"', after)
        # Preserve the fork's version-name parser format even without its updater.
        self.assertIn('versionName "v0.1.0-alpha.3+st32.68"', after)
        self.assertEqual(after, mod.patch_gradle(after))

    def test_strings_safe_and_attribution(self):
        after = mod.patch_strings(STRINGS_FIXTURE)
        self.assertIn('SmarterTube by CodeSculptor', after)
        self.assertNotIn('https://smartertube-stats.codesculptor.workers.dev', after)
        self.assertIn('SmartTube Mobile Dev', after)
        ET.fromstring(after)
        self.assertEqual(after, mod.patch_strings(after))

    def test_updates_disabled(self):
        self.assertIn('No automatic update source', mod.patch_home_activity(HOME_FIXTURE))
        self.assertIn('View.GONE', mod.patch_about_activity(ABOUT_FIXTURE))
        self.assertEqual(mod.patch_home_activity(HOME_FIXTURE), mod.patch_home_activity(mod.patch_home_activity(HOME_FIXTURE)))

    def test_launcher_identity(self):
        after = mod.patch_manifest(MANIFEST_FIXTURE)
        self.assertIn('smarttube_mobile_dev_icon', after)
        self.assertEqual(after, mod.patch_manifest(after))

    def test_fails_closed_on_drift(self):
        with self.assertRaises(ValueError):
            mod.patch_gradle('stmobile { applicationId "wrong" }')
        with self.assertRaises(ValueError):
            mod.patch_strings('<resources/>')

    def test_transactional_preflight_and_dry_run(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            files = [(mod.GRADLE, GRADLE_FIXTURE), (mod.STRINGS, STRINGS_FIXTURE),
                     (mod.HOME_ACTIVITY, HOME_FIXTURE), (mod.ABOUT_ACTIVITY, ABOUT_FIXTURE),
                     (mod.MANIFEST, MANIFEST_FIXTURE)]
            for rel, content in files:
                p = root / rel
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content)
            mod.apply(root, check_only=True)
            self.assertEqual((root / mod.GRADLE).read_text(), GRADLE_FIXTURE)
            (root / mod.ABOUT_ACTIVITY).write_text('drifted')
            with self.assertRaises(ValueError):
                mod.apply(root)
            self.assertEqual((root / mod.GRADLE).read_text(), GRADLE_FIXTURE)
            (root / mod.ABOUT_ACTIVITY).write_text(ABOUT_FIXTURE)
            mod.apply(root)
            outputs = [(root / rel).read_text() for rel, _ in files]
            mod.apply(root)
            self.assertEqual(outputs, [(root / rel).read_text() for rel, _ in files])


if __name__ == '__main__':
    unittest.main()
