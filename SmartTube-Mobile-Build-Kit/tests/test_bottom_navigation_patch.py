"""Offline structural tests of the source integration (not an Android compilation)."""
from pathlib import Path
import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('bottom_nav', ROOT / 'scripts/apply-bottom-navigation.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

JAVA_FIXTURE = '''package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;
import android.widget.ProgressBar;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
public class MobileBrowseFragment {
    private SectionAdapter mSectionAdapter;
    void setup() {
        mSectionAdapter = new SectionAdapter(this::onSectionPicked);
        mSectionAdapter.setSelected(section);
    }
    // ----- helpers -----
}
'''
LAYOUT_FIXTURE = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android">
    <LinearLayout>
        <FrameLayout>
        </FrameLayout>
    </LinearLayout>
    <!-- Drawer menu: retained as alternate navigation. -->
</androidx.drawerlayout.widget.DrawerLayout>
'''

class BottomNavPatchTests(unittest.TestCase):
    def test_java_is_idempotent(self):
        once = mod.patch_java(JAVA_FIXTURE)
        self.assertEqual(once, mod.patch_java(once))
        self.assertIn('selectBottomSection(MediaGroup.TYPE_SUBSCRIPTIONS)', once)
        self.assertIn('PopupMenu popup', once)
        self.assertIn('updateBottomSelection(section.getId())', once)

    def test_layout_is_valid_and_idempotent(self):
        overlay = (ROOT / 'overlays/mobile_bottom_nav.xml').read_text()
        once = mod.patch_layout(LAYOUT_FIXTURE, overlay)
        self.assertEqual(once, mod.patch_layout(once, overlay))
        ET.fromstring(once)
        for key in ('bottom_home','bottom_shorts','bottom_subscriptions','bottom_library','bottom_search'):
            self.assertIn('@+id/' + key, once)
        self.assertIn('Drawer menu: retained', once)

    def test_unknown_java_fails_closed(self):
        with self.assertRaises(ValueError):
            mod.patch_java('class SomeOtherFragment {}')

    def test_layout_preserves_blank_line_before_drawer(self):
        original = LAYOUT_FIXTURE.replace('    <!-- Drawer menu:', '\n    <!-- Drawer menu:')
        overlay = (ROOT / 'overlays/mobile_bottom_nav.xml').read_text()
        patched = mod.patch_layout(original, overlay)
        ET.fromstring(patched)
        self.assertIn('    </LinearLayout>\n\n    <!-- Drawer menu:', patched)
        self.assertEqual(patched, mod.patch_layout(patched, overlay))

    def test_unknown_layout_fails_closed(self):
        with self.assertRaises(ValueError):
            mod.patch_layout('<View/>', '<LinearLayout/>')

    def test_changes_only_mobile_flavor(self):
        with tempfile.TemporaryDirectory() as temp:
            src = Path(temp)
            java = src / mod.JAVA_REL
            layout = src / mod.LAYOUT_REL
            java.parent.mkdir(parents=True)
            layout.parent.mkdir(parents=True)
            java.write_text(JAVA_FIXTURE)
            layout.write_text(LAYOUT_FIXTURE)
            mod.apply(src, check_only=True)
            self.assertFalse((src / mod.STRINGS_REL).exists())
            mod.apply(src, check_only=False)
            self.assertTrue((src / mod.STRINGS_REL).is_file())
            self.assertIn('bottom_home', layout.read_text())
            self.assertIn('updateBottomSelection', java.read_text())
            before = (java.read_bytes(), layout.read_bytes())
            mod.apply(src, check_only=False)
            self.assertEqual(before, (java.read_bytes(), layout.read_bytes()))

if __name__ == '__main__':
    unittest.main()
